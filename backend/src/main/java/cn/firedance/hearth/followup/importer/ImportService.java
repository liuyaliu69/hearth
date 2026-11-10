package cn.firedance.hearth.followup.importer;

import cn.firedance.hearth.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 导入主流程（design 5.2 的七步）。
 *
 * <p>一个事务：**失败即整体回滚**，不会留下半批数据。
 * 重复导入同一天 = 覆盖（先删当天快照与标记，再插入）。
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final ImportMapper importMapper;
    private final InheritanceResolver inheritanceResolver = new InheritanceResolver();

    @Value("${followup.shop-code:fire-buji}")
    private String shopCode;

    @Value("${followup.left-teachers:曼曼,艺帆,璇子}")
    private String leftTeachersConfig;

    public ImportService(ImportMapper importMapper) {
        this.importMapper = importMapper;
    }

    @Transactional
    public ImportReport run(String csvText, LocalDate snapshotDate, String batch) {
        List<Map<String, String>> rawRows = CsvParser.parseWithHeader(csvText);
        if (rawRows.isEmpty()) {
            throw new BizException(400, "CSV 里没有数据行");
        }

        List<ImportRow> rows = new ArrayList<>(rawRows.size());
        for (Map<String, String> raw : rawRows) {
            rows.add(CsvRowMapper.toRow(raw));
        }

        Long shopId = importMapper.findShopIdByCode(shopCode);
        if (shopId == null) {
            throw new BizException(500, "门店不存在：" + shopCode);
        }

        Set<String> duplicated = SuspectRuleEngine.findDuplicatedMobiles(rows);
        SuspectRuleEngine engine = new SuspectRuleEngine(leftTeachers());

        // 覆盖式导入：先清当天
        importMapper.deleteFlagsByDate(snapshotDate);
        importMapper.deleteSnapshotsByDate(snapshotDate);

        Map<String, PrevSnapshot> prevCache = new HashMap<>();
        ImportReport.Builder builder = ImportReport.builder(batch, snapshotDate.toString());

        for (int i = 0; i < rawRows.size(); i++) {
            Map<String, String> raw = rawRows.get(i);
            ImportRow row = rows.get(i);
            builder.total();

            Long memberId = ensureMember(shopId, row, builder);
            Long staffId = importMapper.findStaffIdByName(row.normalizedTeacher());
            builder.staffMatched(staffId != null);

            QueueClassifier.Result queue = QueueClassifier.classify(row);
            Set<String> flags = engine.evaluate(row, duplicated);

            PrevSnapshot prev = prevOf(row, snapshotDate, prevCache);
            InheritanceResult inherit = inheritanceResolver.resolve(row, prev);
            builder.countRow(inherit);

            SnapshotDO snapshot = buildSnapshot(raw, row, shopId, snapshotDate, batch,
                    memberId, staffId, queue, inherit);
            importMapper.insertSnapshot(snapshot);
            builder.createdAtOnce();

            for (String code : flags) {
                importMapper.insertFlag(snapshot.getId(), code);
                builder.countFlag(code);
            }
        }

        ImportReport report = builder.build();
        log.info("导入完成 batch={} date={} total={} pending={} elapsed={}ms",
                batch, snapshotDate, report.total(), report.pending(), report.elapsedMs());
        return report;
    }

    // ---------------------------------------------------------------- 内部

    private Long ensureMember(Long shopId, ImportRow row, ImportReport.Builder builder) {
        String name = row.memberName();
        String mobile = row.normalizedMobile();
        if (name == null || name.isBlank()) {
            throw new BizException(400, "存在没有学员姓名的行，无法导入");
        }
        if (mobile == null) {
            // 没手机号的人也要能进系统（标 NO_MOBILE 由核实台处理），用姓名占位查重
            mobile = "";
        }

        Long existing = importMapper.findMemberId(mobile, name);
        if (existing != null) {
            return existing;
        }

        importMapper.insertMember(shopId, name, mobile, null, LocalDate.now());
        builder.memberCreated();
        Long created = importMapper.findMemberId(mobile, name);
        if (created == null) {
            throw new BizException(500, "会员创建后查不到：" + name);
        }
        return created;
    }

    private PrevSnapshot prevOf(ImportRow row, LocalDate date, Map<String, PrevSnapshot> cache) {
        String mobile = row.normalizedMobile();
        if (mobile == null) {
            return null;
        }
        if (cache.containsKey(mobile)) {
            return cache.get(mobile);
        }
        PrevSnapshotRow prevRow = importMapper.findPrevSnapshot(mobile, date);
        PrevSnapshot prev = prevRow == null ? null : prevRow.toRecord();
        cache.put(mobile, prev);
        return prev;
    }

    private SnapshotDO buildSnapshot(Map<String, String> raw, ImportRow row, Long shopId,
                                     LocalDate date, String batch, Long memberId, Long staffId,
                                     QueueClassifier.Result queue, InheritanceResult inherit) {
        SnapshotDO s = new SnapshotDO();
        s.setShopId(shopId);
        s.setSnapshotDate(date);
        s.setImportBatch(batch);
        s.setMemberId(memberId);
        s.setStaffId(staffId);

        s.setMemberName(row.memberName());
        s.setMobile(blankToEmpty(row.normalizedMobile()));
        s.setTeacherName(blankToEmpty(row.normalizedTeacher()));

        s.setTier(CsvRowMapper.str(raw, "分层"));
        s.setActivity(CsvRowMapper.str(raw, "活跃度"));
        s.setLastClassDate(CsvRowMapper.dateOrNull(raw, "最后上课"));
        s.setDaysSinceClass(row.daysSinceClass());
        s.setAttendCount(row.attendCount());
        s.setTimecardExpire(CsvRowMapper.dateOrNull(raw, "时间卡_到期日"));
        s.setTimecardDaysLeft(row.timecardDaysLeft());
        s.setCountcardLessons(row.countcardLessons());
        s.setCountcardDaysLeft(CsvRowMapper.intOrNull(raw, "次卡_剩余天数"));
        s.setLastSignDate(CsvRowMapper.dateOrNull(raw, "最近签单"));
        s.setLastProduct(CsvRowMapper.str(raw, "最近买的产品"));
        s.setLastSignAmount(CsvRowMapper.decimalOrNull(raw, "最近签单金额"));
        s.setTalkScript(CsvRowMapper.str(raw, "话术"));
        s.setTotalPaid(CsvRowMapper.decimalOrNull(raw, "累计实收"));
        s.setSignCount(CsvRowMapper.intOrNull(raw, "签单笔数"));
        s.setBought199(row.bought199());
        s.setTimecardKind(CsvRowMapper.str(raw, "时间卡_卡种"));
        s.setCountcardKind(CsvRowMapper.str(raw, "次卡_卡种"));

        String extra = CsvRowMapper.toJson(CsvRowMapper.unmapped(raw));
        s.setExtraJson(extra);

        s.setQueue(queue.queue());
        s.setSortKey(queue.sortKey());

        s.setReviewStatus(inherit.reviewStatus());
        s.setReviewReason(inherit.reviewReason());
        s.setDispatchedAt(inherit.dispatchedAt());
        return s;
    }

    private List<String> leftTeachers() {
        Set<String> set = new LinkedHashSet<>();
        Arrays.stream(leftTeachersConfig.split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .forEach(set::add);
        return new ArrayList<>(set);
    }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s;
    }
}
