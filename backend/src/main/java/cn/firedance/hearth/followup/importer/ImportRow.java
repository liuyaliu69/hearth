package cn.firedance.hearth.followup.importer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一条导入记录（CSV 一行 → 强类型）。
 *
 * <p>只保留规则判定与继承需要的字段；其余列原样放在 {@link #extras()} 里，
 * 将来决定怎么存（丢弃 / JSON / 正式列）时不用重写解析。
 *
 * <p>字段与「继承四要素」对应：{@code mobile} / {@code countcardLessons} /
 * {@code timecardExpire} / {@code teacherName} —— 这四个任一变化就要重新核实。
 */
public record ImportRow(
        String memberName,
        String mobile,
        String teacherName,
        Integer daysSinceClass,
        Integer attendCount,
        Integer timecardDaysLeft,
        Integer countcardLessons,
        String timecardExpire,
        Integer bought199,
        Map<String, String> extras
) {

    public ImportRow {
        if (extras == null) {
            extras = new LinkedHashMap<>();
        }
    }

    public boolean mobileIsBlank() {
        return mobile == null || mobile.isBlank();
    }

    /** 11 位纯数字 */
    public boolean mobileLooksValid() {
        return mobile != null && mobile.matches("^\\d{11}$");
    }

    /** 去空白后可能为 null */
    public String normalizedMobile() {
        return blankToNull(mobile);
    }

    public String normalizedTeacher() {
        return blankToNull(teacherName);
    }

    public String normalizedTimecardExpire() {
        return blankToNull(timecardExpire);
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
