package cn.firedance.hearth.followup.importer;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 继承判定结果。
 *
 * @param reviewStatus 本期该记录的核实状态
 * @param reviewReason 置为 PENDING 时的原因（CHANGED / TEACHER_FLAGGED / MANUAL），继承时为原值
 * @param inherited    是否从上一期继承了状态
 * @param dispatchedAt 必须原样带过去 —— **状态回退也不清空**
 * @param changedFields 变化的字段名，便于日志与排查
 */
public record InheritanceResult(
        String reviewStatus,
        String reviewReason,
        boolean inherited,
        LocalDateTime dispatchedAt,
        List<String> changedFields
) {

    public boolean changed() {
        return !changedFields.isEmpty();
    }
}
