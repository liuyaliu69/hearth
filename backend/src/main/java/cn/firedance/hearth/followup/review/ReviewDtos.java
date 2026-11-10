package cn.firedance.hearth.followup.review;

import java.util.List;
import java.util.Map;

/**
 * 核实工作台的出入参。
 */
public final class ReviewDtos {

    private ReviewDtos() {
    }

    public static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("URGENT", "临期且待核实"),
            Map.entry("TEACHER_LEFT", "归属老师已离职"),
            Map.entry("SILENT_BALANCE", "有余额但半年没来"),
            Map.entry("CARD_NO_CLASS", "买了课包没来上"),
            Map.entry("MOBILE_BAD", "手机号异常"),
            Map.entry("STATE_CONFLICT", "状态矛盾"),
            Map.entry("NO_SHOW_199", "199 未到课"),
            Map.entry("NO_MOBILE", "没有手机号"),
            Map.entry("CLEAN", "无疑点（可整批通过）")
    );

    public record GroupVO(String reasonCode, String label, int count) {
    }

    public record SummaryVO(int pendingTotal, GroupVO urgent, List<GroupVO> groups) {
    }

    /** 列表项：**手机号已脱敏**，全号不出服务端 */
    public record ItemVO(
            Long id,
            String memberName,
            String mobileMasked,
            String teacherName,
            String tier,
            Integer daysSinceClass,
            Integer countcardLessons,
            String timecardExpire,
            Integer timecardDaysLeft,
            String queue,
            List<String> flags,
            String reviewStatus
    ) {
    }

    public record ItemsVO(int total, int page, int size, List<ItemVO> items) {
    }

    /** 单条动作：CONFIRM / FIX / REJECT / PARK */
    public record ActReq(Long snapshotId, String action, Map<String, String> fields, String note) {
    }

    /** 整批动作：reason = 疑点代号 / CLEAN / URGENT */
    public record BatchReq(String reason, String action, Integer limit, String note) {
    }

    public record ActVO(Long snapshotId, String reviewStatus) {
    }
}
