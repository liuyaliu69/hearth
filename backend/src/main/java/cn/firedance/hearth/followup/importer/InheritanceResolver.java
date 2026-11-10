package cn.firedance.hearth.followup.importer;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 核实结果继承。
 *
 * <p>规则（spec 第四节）：
 * <ul>
 *   <li>没有上一期 → {@code PENDING}</li>
 *   <li><b>四要素任一变化</b> → {@code PENDING} + {@code CHANGED}（数据变了就得重新核）</li>
 *   <li>四要素全不变 → 继承上一期的核实状态（VERIFIED / REJECTED / PARKED / PENDING）</li>
 * </ul>
 *
 * <p><b>保护条款</b>：{@code dispatched_at} 永远从上一期继承，**即使退回 PENDING 也不清空**。
 * 否则老师昨天跟过的人今天会「消失」，第二天又被重新分发一遍。
 *
 * <p>四要素：手机号 / 次卡剩余课时 / 时间卡到期日 / 归属老师。
 */
public final class InheritanceResolver {

    public static final String PENDING = "PENDING";
    public static final String VERIFIED = "VERIFIED";
    public static final String REJECTED = "REJECTED";
    public static final String PARKED = "PARKED";

    public static final String REASON_CHANGED = "CHANGED";

    public static final String FIELD_MOBILE = "mobile";
    public static final String FIELD_COUNTCARD = "countcard_lessons";
    public static final String FIELD_TIMECARD_EXPIRE = "timecard_expire";
    public static final String FIELD_TEACHER = "teacher_name";

    /**
     * @param row  本期记录
     * @param prev 上一期快照，可为 null（首次出现）
     */
    public InheritanceResult resolve(ImportRow row, PrevSnapshot prev) {
        if (prev == null) {
            return new InheritanceResult(PENDING, null, false, null, List.of());
        }

        List<String> changed = diff(row, prev);

        if (!changed.isEmpty()) {
            // 数据变了：回到待核实，但 dispatched_at 照旧带过去
            return new InheritanceResult(PENDING, REASON_CHANGED, false, prev.dispatchedAt(), changed);
        }

        String status = prev.reviewStatus() == null || prev.reviewStatus().isBlank()
                ? PENDING : prev.reviewStatus();
        return new InheritanceResult(status, prev.reviewReason(), true, prev.dispatchedAt(), List.of());
    }

    /** 四要素比对，返回变化的字段名 */
    public List<String> diff(ImportRow row, PrevSnapshot prev) {
        List<String> changed = new ArrayList<>(4);

        if (!Objects.equals(row.normalizedMobile(), nullToNull(prev.mobile()))) {
            changed.add(FIELD_MOBILE);
        }
        if (!Objects.equals(row.countcardLessons(), prev.countcardLessons())) {
            changed.add(FIELD_COUNTCARD);
        }
        if (!Objects.equals(parseDate(row.normalizedTimecardExpire()), prev.timecardExpire())) {
            changed.add(FIELD_TIMECARD_EXPIRE);
        }
        if (!Objects.equals(row.normalizedTeacher(), nullToNull(prev.teacherName()))) {
            changed.add(FIELD_TEACHER);
        }
        return changed;
    }

    /** 宽松解析：空 → null；格式不对也 → null（脏数据不该让整批导入失败） */
    static LocalDate parseDate(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(t);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String nullToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
