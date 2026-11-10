package cn.firedance.hearth.followup.teacher;

import java.util.List;
import java.util.Map;

/**
 * 老师端出入参。
 */
public final class TeacherDtos {

    private TeacherDtos() {
    }

    /** 结果码：1已续费 2有意向 3考虑中 4没时间 5不续 6未接通 7要顺延 8已流失 9其他 */
    public static final Map<Integer, String> RESULT_LABELS = Map.of(
            1, "已续费", 2, "有意向", 3, "考虑中", 4, "没时间", 5, "不续",
            6, "未接通", 7, "要顺延", 8, "已流失", 9, "其他");

    public record TaskItem(
            Long id,
            Long memberId,
            String memberName,
            String mobileMasked,
            String teacherName,
            String tier,
            Integer daysSinceClass,
            Integer countcardLessons,
            Integer timecardDaysLeft,
            String queue,
            boolean done,
            Integer resultCode,
            String resultText
    ) {
    }

    public record TaskListVO(int total, int doneCount, List<TaskItem> items) {
    }

    public record LogItem(String followDate, Integer resultCode, String resultText,
                          String amount, String note) {
    }

    public record MemberDetailVO(
            Long id,
            Long memberId,
            String memberName,
            String mobileMasked,
            String teacherName,
            String tier,
            String activity,
            Integer daysSinceClass,
            String lastClassDate,
            Integer countcardLessons,
            Integer timecardDaysLeft,
            String timecardExpire,
            String queue,
            String talkScript,
            boolean doneToday,
            List<LogItem> history
    ) {
    }

    /** 提交跟进结果 */
    public record SubmitReq(Long memberId, Integer resultCode, String resultText,
                            String amount, String note) {
    }

    /** 信息有误退回 */
    public record FlagReq(Long memberId, String note) {
    }

    public record StatsVO(int total, int done, int pendingReview, List<StaffStatRow> byStaff) {
    }
}
