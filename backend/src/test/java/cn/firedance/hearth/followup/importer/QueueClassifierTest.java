package cn.firedance.hearth.followup.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 队列归类 —— 决定「老师先跟进谁」。
 *
 * <p>优先级：临期 &gt; 疑点。临期决定顺序，疑点决定要不要核。
 */
class QueueClassifierTest {

    private static ImportRow row(Integer timecardDaysLeft, Integer countcardLessons,
                                 Integer daysSinceClass, Integer bought199) {
        return new ImportRow("张艳", "13812345678", "柒柒", daysSinceClass, 3,
                timecardDaysLeft, countcardLessons, null, bought199, Map.of());
    }

    @Test
    @DisplayName("临期：时间卡剩余 0–30 天 → RED，排序键就是剩余天数")
    void urgentIsRed() {
        assertThat(QueueClassifier.classify(row(0, 5, 10, 0)))
                .extracting(QueueClassifier.Result::queue, QueueClassifier.Result::sortKey)
                .containsExactly("RED", 0);
        assertThat(QueueClassifier.classify(row(15, 5, 10, 0)))
                .extracting(QueueClassifier.Result::queue, QueueClassifier.Result::sortKey)
                .containsExactly("RED", 15);
        assertThat(QueueClassifier.classify(row(30, null, 10, 0)))
                .extracting(QueueClassifier.Result::queue)
                .isEqualTo("RED");
    }

    @Test
    @DisplayName("临期边界：31 天不算临期；负数（已过期）不算临期")
    void urgentBoundary() {
        assertThat(QueueClassifier.classify(row(31, null, 10, 0)).queue()).isNotEqualTo("RED");
        assertThat(QueueClassifier.classify(row(-1, null, 10, 0)).queue()).isNotEqualTo("RED");
    }

    @Test
    @DisplayName("有课时 + 180 天没来 → ORANGE")
    void silentWithBalanceIsOrange() {
        assertThat(QueueClassifier.classify(row(null, 5, 200, 0)).queue()).isEqualTo("ORANGE");
        assertThat(QueueClassifier.classify(row(null, 5, 180, 0)).queue()).isEqualTo("ORANGE");
    }

    @Test
    @DisplayName("有课时但还在来 → YELLOW")
    void stillComingWithBalanceIsYellow() {
        assertThat(QueueClassifier.classify(row(null, 5, 30, 0)).queue()).isEqualTo("YELLOW");
    }

    @Test
    @DisplayName("买过 199 但没课 → BLUE")
    void bought199IsBlue() {
        assertThat(QueueClassifier.classify(row(null, null, null, 1)).queue()).isEqualTo("BLUE");
    }

    @Test
    @DisplayName("什么都没有 → NONE")
    void nothingIsNone() {
        assertThat(QueueClassifier.classify(row(null, null, null, 0)).queue()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("同时满足临期与有余额时，临期优先")
    void urgentWinsOverBalance() {
        QueueClassifier.Result r = QueueClassifier.classify(row(7, 20, 200, 0));
        assertThat(r.queue()).isEqualTo("RED");
    }

    @Test
    @DisplayName("sort_key 越小越靠前：临期整体排在有余额之前")
    void sortKeyOrdering() {
        int red = QueueClassifier.classify(row(20, 5, 200, 0)).sortKey();
        int orange = QueueClassifier.classify(row(null, 5, 200, 0)).sortKey();
        int yellow = QueueClassifier.classify(row(null, 5, 10, 0)).sortKey();
        int none = QueueClassifier.classify(row(null, null, null, 0)).sortKey();

        assertThat(red).isLessThan(orange);
        assertThat(orange).isLessThan(yellow);
        assertThat(yellow).isLessThan(none);
    }
}
