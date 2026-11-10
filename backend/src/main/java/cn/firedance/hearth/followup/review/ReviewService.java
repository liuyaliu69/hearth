package cn.firedance.hearth.followup.review;

import cn.firedance.hearth.auth.CurrentUser;
import cn.firedance.hearth.common.BizException;
import cn.firedance.hearth.common.MaskUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 核实工作台。
 *
 * <p>状态机由这里唯一控制，**不允许 Controller 直接改 review_status**。
 *
 * <p>动作与结果：
 * <ul>
 *   <li>CONFIRM → VERIFIED（数据对，可以给老师看）</li>
 *   <li>FIX → 改白名单字段后 VERIFIED</li>
 *   <li>REJECT → REJECTED（这人不用跟）</li>
 *   <li>PARK → PARKED（以后再说，不算核完）</li>
 * </ul>
 *
 * <p>每个动作都写 `followup_review_log`，改字段时带原值。
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private static final String PENDING = "PENDING";
    private static final String VERIFIED = "VERIFIED";
    private static final String REJECTED = "REJECTED";
    private static final String PARKED = "PARKED";

    private static final Set<String> FIXABLE = Set.of(
            "mobile", "countcardLessons", "timecardExpire", "teacherName");

    private static final int MAX_BATCH = 500;
    private static final int DEFAULT_BATCH = 200;

    private final ReviewMapper reviewMapper;

    public ReviewService(ReviewMapper reviewMapper) {
        this.reviewMapper = reviewMapper;
    }

    // ---------------------------------------------------------------- 汇总

    public ReviewDtos.SummaryVO summary() {
        int pending = reviewMapper.countPending(null);
        int urgent = reviewMapper.countUrgent(null);

        List<ReviewDtos.GroupVO> groups = new ArrayList<>();
        groups.add(new ReviewDtos.GroupVO("URGENT", label("URGENT"), urgent));

        for (ReviewMapper.ReasonCount rc : reviewMapper.countByReason(null)) {
            groups.add(new ReviewDtos.GroupVO(rc.getReasonCode(),
                    label(rc.getReasonCode()), rc.getCnt() == null ? 0 : rc.getCnt()));
        }

        int clean = reviewMapper.countClean(null);
        groups.add(new ReviewDtos.GroupVO("CLEAN", label("CLEAN"), clean));

        return new ReviewDtos.SummaryVO(pending,
                new ReviewDtos.GroupVO("URGENT", label("URGENT"), urgent), groups);
    }

    // ---------------------------------------------------------------- 列表

    public ReviewDtos.ItemsVO items(String reason, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;

        List<ReviewItemRow> rows;
        int total;
        if (reason == null || reason.isBlank() || "CLEAN".equalsIgnoreCase(reason)) {
            rows = reviewMapper.listClean(null, offset, safeSize);
            total = reviewMapper.countClean(null);
        } else {
            rows = reviewMapper.listByReason(null, reason.toUpperCase(Locale.ROOT), offset, safeSize);
            total = countByReason(reason);
        }

        List<ReviewDtos.ItemVO> items = new ArrayList<>(rows.size());
        for (ReviewItemRow r : rows) {
            items.add(toVO(r));
        }
        return new ReviewDtos.ItemsVO(total, safePage, safeSize, items);
    }

    private int countByReason(String reason) {
        String code = reason.toUpperCase(Locale.ROOT);
        for (ReviewMapper.ReasonCount rc : reviewMapper.countByReason(null)) {
            if (code.equals(rc.getReasonCode())) {
                return rc.getCnt() == null ? 0 : rc.getCnt();
            }
        }
        return 0;
    }

    // ---------------------------------------------------------------- 单条动作

    @Transactional
    public ReviewDtos.ActVO act(ReviewDtos.ActReq req, CurrentUser user) {
        if (req == null || req.snapshotId() == null) {
            throw new BizException(400, "snapshotId 不能为空");
        }
        ReviewItemRow item = reviewMapper.findItem(req.snapshotId());
        if (item == null) {
            throw new BizException(404, "记录不存在");
        }
        if (!PENDING.equals(item.getReviewStatus())) {
            throw new BizException(400, "该条已处理：" + item.getReviewStatus());
        }

        String action = normalizeAction(req.action());
        String targetStatus = statusOf(action);

        if ("FIX".equals(action)) {
            applyFixes(item, req.fields(), user);
        }

        reviewMapper.updateStatus(item.getId(), targetStatus, "MANUAL", req.note(),
                user.userId(), LocalDateTime.now());

        reviewMapper.insertLog(buildLog(item, action, user, req.note()));
        log.info("核实动作 {} snapshotId={} by={}", action, item.getId(), user.userId());

        return new ReviewDtos.ActVO(item.getId(), targetStatus);
    }

    // ---------------------------------------------------------------- 整批

    @Transactional
    public int batch(ReviewDtos.BatchReq req, CurrentUser user) {
        String action = normalizeAction(req == null ? null : req.action());
        if ("FIX".equals(action)) {
            throw new BizException(400, "整批不支持修正，请逐条处理");
        }
        String reason = req.reason();
        int limit = req.limit() == null ? DEFAULT_BATCH : Math.min(Math.max(req.limit(), 1), MAX_BATCH);

        List<Long> ids = reviewMapper.findBatchIds(reason, limit);
        if (ids.isEmpty()) {
            return 0;
        }

        String status = statusOf(action);
        LocalDateTime now = LocalDateTime.now();
        int affected = reviewMapper.updateStatusByIds(ids, status, user.userId(), now);
        reviewMapper.insertLogBatch(ids, action, user.userId(), req.note());

        log.info("整批核实 {} reason={} affected={} by={}", action, reason, affected, user.userId());
        return affected;
    }

    // ---------------------------------------------------------------- 内部

    private void applyFixes(ReviewItemRow item, Map<String, String> fields, CurrentUser user) {
        if (fields == null || fields.isEmpty()) {
            throw new BizException(400, "修正动作必须提供 fields");
        }
        for (String field : fields.keySet()) {
            if (!FIXABLE.contains(field)) {
                throw new BizException(400, "字段不允许修改：" + field);
            }
        }

        if (fields.containsKey("mobile")) {
            String newVal = trimToNull(fields.get("mobile"));
            logField(item, "mobile", item.getMobile(), newVal, user);
            reviewMapper.updateMobile(item.getId(), newVal);
        }
        if (fields.containsKey("countcardLessons")) {
            Integer newVal = parseInt(fields.get("countcardLessons"));
            logField(item, "countcard_lessons",
                    item.getCountcardLessons() == null ? null : item.getCountcardLessons().toString(),
                    newVal == null ? null : newVal.toString(), user);
            reviewMapper.updateCountcard(item.getId(), newVal);
        }
        if (fields.containsKey("timecardExpire")) {
            LocalDate newVal = parseDate(fields.get("timecardExpire"));
            logField(item, "timecard_expire",
                    item.getTimecardExpire() == null ? null : item.getTimecardExpire().toString(),
                    newVal == null ? null : newVal.toString(), user);
            reviewMapper.updateTimecardExpire(item.getId(), newVal);
        }
        if (fields.containsKey("teacherName")) {
            String newVal = trimToNull(fields.get("teacherName"));
            logField(item, "teacher_name", item.getTeacherName(), newVal, user);
            reviewMapper.updateTeacher(item.getId(), newVal, null);
        }
    }

    private void logField(ReviewItemRow item, String field, String oldVal, String newVal, CurrentUser user) {
        ReviewLogDO logDO = new ReviewLogDO();
        logDO.setSnapshotId(item.getId());
        logDO.setMemberId(item.getMemberId());
        logDO.setStaffId(user.userId());
        logDO.setAction("FIX");
        logDO.setFieldName(field);
        logDO.setOldValue(oldVal);
        logDO.setNewValue(newVal);
        reviewMapper.insertLog(logDO);
    }

    private ReviewLogDO buildLog(ReviewItemRow item, String action, CurrentUser user, String note) {
        ReviewLogDO logDO = new ReviewLogDO();
        logDO.setSnapshotId(item.getId());
        logDO.setMemberId(item.getMemberId());
        logDO.setStaffId(user.userId());
        logDO.setAction(action);
        logDO.setNote(note);
        return logDO;
    }

    private ReviewDtos.ItemVO toVO(ReviewItemRow r) {
        List<String> flags = new ArrayList<>();
        if (r.getFlags() != null && !r.getFlags().isBlank()) {
            flags.addAll(Arrays.asList(r.getFlags().split(",")));
        }
        return new ReviewDtos.ItemVO(
                r.getId(),
                r.getMemberName(),
                MaskUtil.maskMobile(r.getMobile()),
                r.getTeacherName(),
                r.getTier(),
                r.getDaysSinceClass(),
                r.getCountcardLessons(),
                r.getTimecardExpire() == null ? null : r.getTimecardExpire().toString(),
                r.getTimecardDaysLeft(),
                r.getQueue(),
                flags,
                r.getReviewStatus()
        );
    }

    private static String label(String code) {
        return ReviewDtos.LABELS.getOrDefault(code, code);
    }

    private static String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            throw new BizException(400, "action 不能为空");
        }
        String a = action.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("CONFIRM", "FIX", "REJECT", "PARK").contains(a)) {
            throw new BizException(400, "未知动作：" + action);
        }
        return a;
    }

    private static String statusOf(String action) {
        return switch (action) {
            case "CONFIRM", "FIX" -> VERIFIED;
            case "REJECT" -> REJECTED;
            case "PARK" -> PARKED;
            default -> throw new BizException(400, "未知动作：" + action);
        };
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Integer parseInt(String s) {
        String t = trimToNull(s);
        if (t == null) {
            return null;
        }
        try {
            return Integer.valueOf(t);
        } catch (NumberFormatException e) {
            throw new BizException(400, "不是合法数字：" + s);
        }
    }

    private static LocalDate parseDate(String s) {
        String t = trimToNull(s);
        if (t == null) {
            return null;
        }
        try {
            return LocalDate.parse(t);
        } catch (Exception e) {
            throw new BizException(400, "日期格式应为 yyyy-MM-dd：" + s);
        }
    }
}
