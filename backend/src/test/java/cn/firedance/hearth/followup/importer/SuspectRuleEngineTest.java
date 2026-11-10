package cn.firedance.hearth.followup.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 疑点规则 —— 每条规则都要有「命中」和「不命中」两个用例。
 *
 * <p>只有命中的用例是自欺欺人：规则写宽了，什么都命中，测试照样绿，
 * 结果核实台每天躺着几百条噪音，老师三天后就不看了。
 */
class SuspectRuleEngineTest {

    private static final List<String> LEFT_TEACHERS = List.of("曼曼", "艺帆", "璇子");

    private final SuspectRuleEngine engine = new SuspectRuleEngine(LEFT_TEACHERS);

    /** 便于构造成员：一个「干净」的记录，再按需覆盖单个字段 */
    private static ImportRow row(String mobile, String teacher, Integer daysSinceClass,
                                 Integer attendCount, Integer timecardDaysLeft,
                                 Integer countcardLessons, Integer bought199) {
        return new ImportRow("张艳", mobile, teacher, daysSinceClass, attendCount,
                timecardDaysLeft, countcardLessons, null, bought199, java.util.Map.of());
    }

    private Set<String> eval(ImportRow row) {
        return engine.evaluate(row, Set.of());
    }

    // ---------------------------------------------------------------- NO_MOBILE

    @Test
    @DisplayName("NO_MOBILE：手机号为空")
    void noMobile_hit() {
        assertThat(eval(row(null, "柒柒", 10, 3, 30, 5, 0))).containsExactly("NO_MOBILE");
        assertThat(eval(row("   ", "柒柒", 10, 3, 30, 5, 0))).containsExactly("NO_MOBILE");
    }

    @Test
    @DisplayName("NO_MOBILE：有手机号时不命中")
    void noMobile_miss() {
        assertThat(eval(row("13812345678", "柒柒", 10, 3, 30, 5, 0))).isEmpty();
    }

    // ---------------------------------------------------------------- MOBILE_BAD

    @Test
    @DisplayName("MOBILE_BAD：位数不对、含星号、含字母")
    void mobileBad_formatHits() {
        assertThat(eval(row("1381234", "柒柒", 10, 3, 30, 5, 0))).containsExactly("MOBILE_BAD");
        assertThat(eval(row("138****5678", "柒柒", 10, 3, 30, 5, 0))).containsExactly("MOBILE_BAD");
        assertThat(eval(row("1381234567a", "柒柒", 10, 3, 30, 5, 0))).containsExactly("MOBILE_BAD");
    }

    @Test
    @DisplayName("MOBILE_BAD：手机号在本批次里重复")
    void mobileBad_duplicateHits() {
        ImportRow r = row("13812345678", "柒柒", 10, 3, 30, 5, 0);
        assertThat(engine.evaluate(r, Set.of("13812345678"))).containsExactly("MOBILE_BAD");
    }

    @Test
    @DisplayName("空手机号只报 NO_MOBILE，不重复报 MOBILE_BAD")
    void emptyMobileNotCountedTwice() {
        Set<String> hit = eval(row(null, "柒柒", 10, 3, 30, 5, 0));
        assertThat(hit).containsExactly("NO_MOBILE");
        assertThat(hit).doesNotContain("MOBILE_BAD");
    }

    // ---------------------------------------------------------------- SILENT_BALANCE

    @Test
    @DisplayName("SILENT_BALANCE：有剩余课时 + 超过 180 天没来")
    void silentBalance_hit() {
        assertThat(eval(row("13812345678", "柒柒", 180, 3, 30, 5, 0)))
                .containsExactly("SILENT_BALANCE");
        assertThat(eval(row("13812345678", "柒柒", 400, 3, 30, 20, 0)))
                .containsExactly("SILENT_BALANCE");
    }

    @Test
    @DisplayName("SILENT_BALANCE：179 天不算、课时为 0 不算、无上课记录不算")
    void silentBalance_miss() {
        assertThat(eval(row("13812345678", "柒柒", 179, 3, 30, 5, 0))).isEmpty();
        assertThat(eval(row("13812345678", "柒柒", 200, 3, 30, 0, 0))).isEmpty();
        assertThat(eval(row("13812345678", "柒柒", null, 3, 30, 5, 0))).isEmpty();
    }

    // ---------------------------------------------------------------- STATE_CONFLICT

    @Test
    @DisplayName("STATE_CONFLICT：时间卡已过期，次卡却还有课时")
    void stateConflict_hit() {
        assertThat(eval(row("13812345678", "柒柒", 10, 3, -1, 5, 0)))
                .containsExactly("STATE_CONFLICT");
    }

    @Test
    @DisplayName("STATE_CONFLICT：时间卡还没过期、或次卡没课时，都不算")
    void stateConflict_miss() {
        assertThat(eval(row("13812345678", "柒柒", 10, 3, 1, 5, 0))).isEmpty();
        assertThat(eval(row("13812345678", "柒柒", 10, 3, -1, 0, 0))).isEmpty();
        assertThat(eval(row("13812345678", "柒柒", 10, 3, null, 5, 0))).isEmpty();
    }

    // ---------------------------------------------------------------- TEACHER_LEFT

    @Test
    @DisplayName("TEACHER_LEFT：分配老师已离职")
    void teacherLeft_hit() {
        assertThat(eval(row("13812345678", "曼曼", 10, 3, 30, 5, 0)))
                .containsExactly("TEACHER_LEFT");
        assertThat(eval(row("13812345678", " 艺帆 ", 10, 3, 30, 5, 0)))
                .containsExactly("TEACHER_LEFT");
    }

    @Test
    @DisplayName("TEACHER_LEFT：在职老师不命中")
    void teacherLeft_miss() {
        assertThat(eval(row("13812345678", "柒柒", 10, 3, 30, 5, 0))).isEmpty();
        assertThat(eval(row("13812345678", null, 10, 3, 30, 5, 0))).isEmpty();
    }

    // ---------------------------------------------------------------- NO_SHOW_199

    @Test
    @DisplayName("NO_SHOW_199：买过 199 但一次没来")
    void noShow199_hit() {
        // 次卡剩余设 0，避免同时命中 CARD_NO_CLASS，保持本用例只测这一条
        assertThat(eval(row("13812345678", "柒柒", 10, 0, 30, 0, 1)))
                .containsExactly("NO_SHOW_199");
    }

    @Test
    @DisplayName("NO_SHOW_199：来过一次就不算、没买过也不算")
    void noShow199_miss() {
        assertThat(eval(row("13812345678", "柒柒", 10, 1, 30, 0, 1))).isEmpty();
        assertThat(eval(row("13812345678", "柒柒", 10, 0, 30, 0, 0))).isEmpty();
    }

    // ---------------------------------------------------------------- CARD_NO_CLASS

    @Test
    @DisplayName("CARD_NO_CLASS：有剩余课时但一次没来（真实案例：375 课时 / 0 次到课）")
    void cardNoClass_hit() {
        Set<String> hit = engine.evaluate(row("15971588802", "刘亚柳", null, 0, null, 375, null), Set.of());
        assertThat(hit).containsExactly("CARD_NO_CLASS");
    }

    @Test
    @DisplayName("CARD_NO_CLASS：来过一次就不算、没课时也不算")
    void cardNoClass_miss() {
        assertThat(eval(row("13812345678", "柒柒", null, 1, null, 375, null))).isEmpty();
        assertThat(eval(row("13812345678", "柒柒", null, 0, null, 0, null))).isEmpty();
        // 到课次数缺失时不能假定为 0，否则整批空数据会全被标成疑点
        assertThat(eval(row("13812345678", "柒柒", null, null, null, 375, null))).isEmpty();
    }

    @Test
    @DisplayName("数据一致时分流：从未来过只报 CARD_NO_CLASS，来过且久未到只报 SILENT_BALANCE")
    void cardNoClassAndSilentBalanceSplitWhenDataIsConsistent() {
        // 从未来过：距末课天数必然为空
        Set<String> never = engine.evaluate(
                row("13812345678", "柒柒", null, 0, null, 10, null), Set.of());
        assertThat(never).containsExactly("CARD_NO_CLASS");

        // 来过、但超过 180 天没来
        Set<String> silent = engine.evaluate(
                row("13812345678", "柒柒", 400, 5, null, 10, null), Set.of());
        assertThat(silent).containsExactly("SILENT_BALANCE");
    }

    @Test
    @DisplayName("数据自相矛盾时两条同时命中（允许一记录多标记）")
    void bothRulesFireOnContradictoryData() {
        // 到课次数=0，却又有距末课 400 天 —— 这本身就是数据问题，两条都该报
        Set<String> hit = engine.evaluate(
                row("13812345678", "柒柒", 400, 0, null, 10, null), Set.of());
        assertThat(hit).containsExactlyInAnyOrder("SILENT_BALANCE", "CARD_NO_CLASS");
    }

    // ---------------------------------------------------------------- 组合与开关

    @Test
    @DisplayName("多条同时命中时全部返回")
    void multipleHits() {
        Set<String> hit = eval(row(null, "曼曼", 200, 0, -1, 5, 1));
        assertThat(hit).containsExactlyInAnyOrder(
                "NO_MOBILE", "TEACHER_LEFT", "SILENT_BALANCE", "STATE_CONFLICT",
                "NO_SHOW_199", "CARD_NO_CLASS");
    }

    @Test
    @DisplayName("停用的规则不再产生命中（可配置，不改代码）")
    void disabledRuleNotApplied() {
        SuspectRuleEngine onlyMobile = new SuspectRuleEngine(
                List.of(SuspectRuleEngine.NO_MOBILE), LEFT_TEACHERS);
        Set<String> hit = onlyMobile.evaluate(
                row(null, "曼曼", 200, 0, -1, 5, 1), Set.of());
        assertThat(hit).containsExactly("NO_MOBILE");
    }

    @Test
    @DisplayName("空记录不抛异常")
    void nullRowSafe() {
        assertThat(engine.evaluate(null, Set.of())).isEmpty();
    }

    // ---------------------------------------------------------------- 批次去重

    @Test
    @DisplayName("findDuplicatedMobiles：只算出现两次以上的，空号不参与")
    void findDuplicatedMobiles() {
        List<ImportRow> rows = List.of(
                row("13812345678", "柒柒", 10, 3, 30, 5, 0),
                row("13812345678", "柒柒", 10, 3, 30, 5, 0),
                row("13900000000", "柒柒", 10, 3, 30, 5, 0),
                row(null, "柒柒", 10, 3, 30, 5, 0),
                row("   ", "柒柒", 10, 3, 30, 5, 0)
        );
        assertThat(SuspectRuleEngine.findDuplicatedMobiles(rows))
                .containsExactly("13812345678");
    }
}
