package cn.firedance.hearth.followup.importer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次导入的结果。命令行会把它打印成人能看的样子。
 */
public record ImportReport(
        String batch,
        String snapshotDate,
        int total,
        int inserted,
        int inheritedVerified,
        int inheritedOther,
        int pending,
        int changed,
        Map<String, Integer> flagHits,
        int memberCreated,
        int staffMatched,
        int staffUnmatched,
        int withExtraJson,
        long elapsedMs
) {

    public static Builder builder(String batch, String snapshotDate) {
        return new Builder(batch, snapshotDate);
    }

    /** 待核实总数（含命中疑点与被标记变更的） */
    public int pendingTotal() {
        return pending;
    }

    public static final class Builder {
        private final String batch;
        private final String snapshotDate;
        private int total;
        private int inserted;
        private int inheritedVerified;
        private int inheritedOther;
        private int pending;
        private int changed;
        private final Map<String, Integer> flagHits = new LinkedHashMap<>();
        private int memberCreated;
        private int staffMatched;
        private int staffUnmatched;
        private int withExtraJson;
        private long startAt = System.currentTimeMillis();

        private Builder(String batch, String snapshotDate) {
            this.batch = batch;
            this.snapshotDate = snapshotDate;
        }

        public void countRow(InheritanceResult inherit) {
            if (inherit.inherited()) {
                if ("VERIFIED".equals(inherit.reviewStatus())) {
                    inheritedVerified++;
                } else {
                    inheritedOther++;
                }
            } else {
                pending++;
                if (inherit.changed()) {
                    changed++;
                }
            }
        }

        public void countFlag(String code) {
            flagHits.merge(code, 1, Integer::sum);
        }

        public void createdAtOnce() {
            inserted++;
        }

        public void memberCreated() {
            memberCreated++;
        }

        public void staffMatched(boolean matched) {
            if (matched) {
                staffMatched++;
            } else {
                staffUnmatched++;
            }
        }

        public void extraJson() {
            withExtraJson++;
        }

        public void total() {
            total++;
        }

        public ImportReport build() {
            return new ImportReport(batch, snapshotDate, total, inserted, inheritedVerified,
                    inheritedOther, pending, changed, flagHits, memberCreated, staffMatched,
                    staffUnmatched, withExtraJson, System.currentTimeMillis() - startAt);
        }
    }
}
