package cn.firedance.hearth.followup.importer;

/**
 * 队列归类 + 排序键。
 *
 * <p>五个队列（spec 第三节）：
 * <table>
 *   <tr><td>RED</td><td>临期：时间卡剩余 0–30 天</td></tr>
 *   <tr><td>ORANGE</td><td>沉默有余额：有剩余课时且 ≥180 天没来</td></tr>
 *   <tr><td>YELLOW</td><td>囤课待消：有剩余课时，但还在来</td></tr>
 *   <tr><td>BLUE</td><td>未转化：买过 199 但没课</td></tr>
 *   <tr><td>NONE</td><td>其余</td></tr>
 * </table>
 *
 * <p><b>优先级：临期 &gt; 疑点</b>。临期决定「先跟进谁」，疑点决定「这条要不要核」，
 * 两件事不冲突。{@code sort_key} 越小越靠前。
 */
public final class QueueClassifier {

    public static final String RED = "RED";
    public static final String ORANGE = "ORANGE";
    public static final String YELLOW = "YELLOW";
    public static final String BLUE = "BLUE";
    public static final String NONE = "NONE";

    /** 临期窗口：时间卡剩余天数 0–30 */
    public static final int URGENT_DAYS = 30;
    private static final int SILENT_DAYS = 180;

    private QueueClassifier() {
    }

    public record Result(String queue, int sortKey) {
    }

    public static Result classify(ImportRow row) {
        Integer cardDays = row.timecardDaysLeft();
        Integer lessons = row.countcardLessons();
        Integer away = row.daysSinceClass();

        // 1. 临期：最优先，组内按剩余天数升序
        if (cardDays != null && cardDays >= 0 && cardDays <= URGENT_DAYS) {
            return new Result(RED, cardDays);
        }

        // 2. 有钱没上课
        if (lessons != null && lessons > 0 && away != null && away >= SILENT_DAYS) {
            return new Result(ORANGE, 10000 + Math.min(away, 9999));
        }

        // 3. 有余额但还在来
        if (lessons != null && lessons > 0) {
            return new Result(YELLOW, 20000 + Math.min(away == null ? 0 : away, 9999));
        }

        // 4. 买过 199 但没课
        if (Integer.valueOf(1).equals(row.bought199())) {
            return new Result(BLUE, 30000 + Math.min(away == null ? 0 : away, 9999));
        }

        return new Result(NONE, 40000 + Math.min(away == null ? 0 : away, 9999));
    }
}
