package cn.firedance.hearth.followup.importer;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 疑点规则引擎 v1。
 *
 * <p>六条规则（见 spec 第五节）：系统**自动标，不自动放行**。
 * 命中只是让这条记录进入核实队列，是否可用仍由人决定。
 *
 * <p>设计要点：
 * <ul>
 *   <li>规则是纯函数：同一条记录 + 同一上下文，结果恒定（好测、可复现）</li>
 *   <li>「与其他记录重复」需要跨行上下文，所以单独传 {@code duplicatedMobiles}</li>
 *   <li>规则可停用：构造时传要启用的代号集合，不改代码</li>
 * </ul>
 *
 * <p>命中率长期 &gt; 40% 说明规则太松，&lt; 5% 说明太紧（spec 有明文）。
 */
public final class SuspectRuleEngine {

    public static final String MOBILE_BAD = "MOBILE_BAD";
    public static final String SILENT_BALANCE = "SILENT_BALANCE";
    public static final String STATE_CONFLICT = "STATE_CONFLICT";
    public static final String TEACHER_LEFT = "TEACHER_LEFT";
    public static final String NO_SHOW_199 = "NO_SHOW_199";
    public static final String NO_MOBILE = "NO_MOBILE";
    /** 买了课包但一次没来 —— SILENT_BALANCE 覆盖不到「从未来过」的人 */
    public static final String CARD_NO_CLASS = "CARD_NO_CLASS";

    public static final Set<String> ALL_RULES = Set.of(
            MOBILE_BAD, SILENT_BALANCE, STATE_CONFLICT, TEACHER_LEFT,
            NO_SHOW_199, NO_MOBILE, CARD_NO_CLASS);

    /** 「有钱没上课」的阈值：距末课 ≥ 这个天数 */
    private static final int SILENT_DAYS = 180;

    private final Set<String> enabledRules;
    private final Set<String> leftTeachers;

    public SuspectRuleEngine(Collection<String> leftTeachers) {
        this(ALL_RULES, leftTeachers);
    }

    public SuspectRuleEngine(Collection<String> enabledRules, Collection<String> leftTeachers) {
        this.enabledRules = enabledRules == null ? ALL_RULES : new LinkedHashSet<>(enabledRules);
        this.leftTeachers = leftTeachers == null ? Set.of() : new LinkedHashSet<>(leftTeachers);
    }

    /**
     * 判定一条记录命中了哪些疑点。
     *
     * @param row               待判定的记录
     * @param duplicatedMobiles 本批次里出现多次的手机号（用于 MOBILE_BAD）
     * @return 命中的规则代号，按 {@link #ALL_RULES} 的稳定顺序
     */
    public Set<String> evaluate(ImportRow row, Set<String> duplicatedMobiles) {
        Set<String> hit = new LinkedHashSet<>();
        if (row == null) {
            return hit;
        }

        if (isOn(NO_MOBILE) && row.mobileIsBlank()) {
            hit.add(NO_MOBILE);
        }

        // 空手机号只报 NO_MOBILE，不重复报 MOBILE_BAD —— 否则一次问题算两个疑点
        if (isOn(MOBILE_BAD) && !row.mobileIsBlank()) {
            boolean bad = !row.mobileLooksValid()
                    || (duplicatedMobiles != null && duplicatedMobiles.contains(row.mobile()));
            if (bad) {
                hit.add(MOBILE_BAD);
            }
        }

        if (isOn(SILENT_BALANCE)
                && positive(row.countcardLessons())
                && row.daysSinceClass() != null
                && row.daysSinceClass() >= SILENT_DAYS) {
            hit.add(SILENT_BALANCE);
        }

        // 与 SILENT_BALANCE 不重叠：有上课记录走上面，一次没来走这条。
        // 「从未来过」的会员没有 days_since_class，SILENT_BALANCE 永远判不了。
        if (isOn(CARD_NO_CLASS)
                && positive(row.countcardLessons())
                && Integer.valueOf(0).equals(row.attendCount())) {
            hit.add(CARD_NO_CLASS);
        }

        if (isOn(STATE_CONFLICT)
                && row.timecardDaysLeft() != null
                && row.timecardDaysLeft() < 0
                && positive(row.countcardLessons())) {
            hit.add(STATE_CONFLICT);
        }

        if (isOn(TEACHER_LEFT)
                && row.teacherName() != null
                && leftTeachers.contains(row.teacherName().trim())) {
            hit.add(TEACHER_LEFT);
        }

        if (isOn(NO_SHOW_199)
                && Integer.valueOf(1).equals(row.bought199())
                && Integer.valueOf(0).equals(row.attendCount())) {
            hit.add(NO_SHOW_199);
        }

        return hit;
    }

    /**
     * 扫描整批，找出出现多次的手机号（空号不参与）。
     *
     * <p>只在批次内比较：跨批次重复要靠数据库唯一键兜底。
     */
    public static Set<String> findDuplicatedMobiles(List<ImportRow> rows) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicated = new LinkedHashSet<>();
        if (rows == null) {
            return duplicated;
        }
        for (ImportRow row : rows) {
            if (row == null || row.mobileIsBlank()) {
                continue;
            }
            String m = row.mobile().trim();
            if (!seen.add(m)) {
                duplicated.add(m);
            }
        }
        return duplicated;
    }

    private boolean isOn(String code) {
        return enabledRules.contains(code);
    }

    private static boolean positive(Integer v) {
        return v != null && v > 0;
    }
}
