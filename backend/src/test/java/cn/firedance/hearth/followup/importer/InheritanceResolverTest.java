package cn.firedance.hearth.followup.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 继承逻辑 —— C1 最容易做错的一块。
 *
 * <p>做错的两种后果：
 * <ul>
 *   <li>该继承的没继承 → 老师每天重复核实同样的人</li>
 *   <li>不该继承的继承了 → <b>数据变了却沿用旧的「已核实」</b>，老师看到错数据</li>
 * </ul>
 *
 * <p>核心用例是「四要素 × 变/不变 = 16 种组合」，逐一断言。
 */
class InheritanceResolverTest {

    private final InheritanceResolver resolver = new InheritanceResolver();

    // ---------------------------------------------------------------- 构造

    private static ImportRow row(String mobile, Integer countcard, String timecardExpire, String teacher) {
        return new ImportRow("张艳", mobile, teacher, 10, 3, 30, countcard, timecardExpire, 0, Map.of());
    }

    private static PrevSnapshot prev(String mobile, Integer countcard, LocalDate expire, String teacher,
                                     String status, LocalDateTime dispatchedAt) {
        return new PrevSnapshot(1L, mobile, countcard, expire, teacher, status, null, dispatchedAt);
    }

    private static final LocalDateTime DISPATCHED = LocalDateTime.of(2026, 10, 1, 9, 0);

    /** 基准：四要素都一致的一组 */
    private static ImportRow baseRow() {
        return row("13812345678", 10, "2026-12-31", "柒柒");
    }

    private static PrevSnapshot basePrev(String status) {
        return prev("13812345678", 10, LocalDate.of(2026, 12, 31), "柒柒", status, DISPATCHED);
    }

    // ---------------------------------------------------------------- 16 种组合

    @Test
    @DisplayName("四要素 × 变/不变 = 16 种组合，结果逐一对上")
    void allSixteenCombinations() {
        // 位序：0=mobile 1=countcard 2=timecardExpire 3=teacher
        for (int mask = 0; mask < 16; mask++) {
            boolean chMobile = (mask & 1) != 0;
            boolean chCount = (mask & 2) != 0;
            boolean chExpire = (mask & 4) != 0;
            boolean chTeacher = (mask & 8) != 0;

            ImportRow row = row(
                    chMobile ? "13900000000" : "13812345678",
                    chCount ? 20 : 10,
                    chExpire ? "2027-06-30" : "2026-12-31",
                    chTeacher ? "三水" : "柒柒");

            PrevSnapshot prev = basePrev("VERIFIED");
            InheritanceResult r = resolver.resolve(row, prev);

            List<String> expected = new java.util.ArrayList<>();
            if (chMobile) expected.add(InheritanceResolver.FIELD_MOBILE);
            if (chCount) expected.add(InheritanceResolver.FIELD_COUNTCARD);
            if (chExpire) expected.add(InheritanceResolver.FIELD_TIMECARD_EXPIRE);
            if (chTeacher) expected.add(InheritanceResolver.FIELD_TEACHER);

            assertThat(r.changedFields())
                    .as("mask=%d 的变化字段", mask)
                    .containsExactlyElementsOf(expected);

            if (expected.isEmpty()) {
                assertThat(r.reviewStatus()).as("mask=0 应继承 VERIFIED").isEqualTo("VERIFIED");
                assertThat(r.inherited()).isTrue();
            } else {
                assertThat(r.reviewStatus()).as("mask=%d 变了就要回待核", mask).isEqualTo("PENDING");
                assertThat(r.reviewReason()).isEqualTo(InheritanceResolver.REASON_CHANGED);
                assertThat(r.inherited()).isFalse();
            }

            // 无论变没变，dispatched_at 都必须带过去
            assertThat(r.dispatchedAt()).as("mask=%d dispatched_at", mask).isEqualTo(DISPATCHED);
        }
    }

    // ---------------------------------------------------------------- 首次出现

    @Test
    @DisplayName("没有上一期：PENDING，且没有 dispatched_at")
    void firstTimeIsPending() {
        InheritanceResult r = resolver.resolve(baseRow(), null);
        assertThat(r.reviewStatus()).isEqualTo("PENDING");
        assertThat(r.inherited()).isFalse();
        assertThat(r.dispatchedAt()).isNull();
        assertThat(r.changed()).isFalse();
    }

    // ---------------------------------------------------------------- 继承各种状态

    @Test
    @DisplayName("四要素不变时，继承上一期的每一种状态")
    void inheritsEveryStatus() {
        for (String status : List.of("VERIFIED", "REJECTED", "PARKED", "PENDING")) {
            InheritanceResult r = resolver.resolve(baseRow(), basePrev(status));
            assertThat(r.reviewStatus()).as("继承 %s", status).isEqualTo(status);
            assertThat(r.inherited()).isTrue();
        }
    }

    @Test
    @DisplayName("已排除的人数据没变，不该重新变回待核")
    void rejectedStaysRejected() {
        InheritanceResult r = resolver.resolve(baseRow(), basePrev("REJECTED"));
        assertThat(r.reviewStatus()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("★ 保护条款：数据变了退回 PENDING，但 dispatched_at 不清空")
    void dispatchedAtSurvivesReset() {
        ImportRow changed = row("13812345678", 99, "2026-12-31", "柒柒"); // 课时变了
        InheritanceResult r = resolver.resolve(changed, basePrev("VERIFIED"));

        assertThat(r.reviewStatus()).isEqualTo("PENDING");
        assertThat(r.dispatchedAt())
                .as("清空它会让老师昨天跟过的人今天消失")
                .isEqualTo(DISPATCHED);
    }

    // ---------------------------------------------------------------- 边界

    @Test
    @DisplayName("空字符串与 null 视为同一个值，不算变化")
    void blankAndNullAreEqual() {
        ImportRow r = row("13812345678", 10, "", "柒柒");
        PrevSnapshot p = prev("13812345678", 10, null, "柒柒", "VERIFIED", DISPATCHED);
        assertThat(resolver.diff(r, p)).isEmpty();
    }

    @Test
    @DisplayName("日期格式脏数据当作 null，不算变化也不抛异常")
    void dirtyDateTreatedAsNull() {
        ImportRow r = row("13812345678", 10, "2026/12/31", "柒柒");
        PrevSnapshot p = prev("13812345678", 10, null, "柒柒", "VERIFIED", DISPATCHED);
        assertThat(resolver.diff(r, p)).isEmpty();
    }

    @Test
    @DisplayName("前后空白不影响比对")
    void trimsBeforeCompare() {
        ImportRow r = row(" 13812345678 ", 10, " 2026-12-31 ", " 柒柒 ");
        assertThat(resolver.diff(r, basePrev("VERIFIED"))).isEmpty();
    }

    @Test
    @DisplayName("课时从 null 变成 0 也算变化（0 是有意义的：卡用完了）")
    void nullToZeroIsAChange() {
        ImportRow r = row("13812345678", 0, "2026-12-31", "柒柒");
        PrevSnapshot p = prev("13812345678", null, LocalDate.of(2026, 12, 31), "柒柒", "VERIFIED", DISPATCHED);
        assertThat(resolver.diff(r, p)).containsExactly(InheritanceResolver.FIELD_COUNTCARD);
    }
}
