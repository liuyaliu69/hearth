package cn.firedance.hearth.followup.importer;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 上一期快照（判继承用）。只取需要比对与继承的字段。
 */
public record PrevSnapshot(
        Long id,
        String mobile,
        Integer countcardLessons,
        LocalDate timecardExpire,
        String teacherName,
        String reviewStatus,
        String reviewReason,
        LocalDateTime dispatchedAt
) {
}
