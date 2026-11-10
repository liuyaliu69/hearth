package cn.firedance.hearth.followup.teacher;

import cn.firedance.hearth.auth.CurrentUser;
import cn.firedance.hearth.auth.DataScope;
import cn.firedance.hearth.auth.DataScopeService;
import cn.firedance.hearth.common.BizException;
import cn.firedance.hearth.common.MaskUtil;
import cn.firedance.hearth.followup.review.ReviewLogDO;
import cn.firedance.hearth.followup.review.ReviewMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 老师端。
 *
 * <p>两条铁律：
 * <ol>
 *   <li><b>只看已核实</b>：所有查询都走 {@code TaskMapper.xml} 里唯一的 visible 片段</li>
 *   <li><b>越权返回 404</b>：查不到就当不存在，不告诉对方「有这条但你没权限」</li>
 * </ol>
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private static final int DEFAULT_LIMIT = 100;

    private final TaskMapper taskMapper;
    private final ReviewMapper reviewMapper;
    private final DataScopeService dataScopeService;

    public TaskService(TaskMapper taskMapper, ReviewMapper reviewMapper,
                       DataScopeService dataScopeService) {
        this.taskMapper = taskMapper;
        this.reviewMapper = reviewMapper;
        this.dataScopeService = dataScopeService;
    }

    // ---------------------------------------------------------------- 今日待办

    @Transactional
    public TeacherDtos.TaskListVO tasks(CurrentUser user, int limit) {
        DataScope scope = dataScopeService.scopeOf(user);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 300);

        List<TaskRow> rows = taskMapper.listTasks(scope.all(), scope.staffId(), safeLimit);
        if (!rows.isEmpty()) {
            // 懒分发：第一次进入老师视野时记一笔，之后不再变
            List<Long> ids = new ArrayList<>(rows.size());
            for (TaskRow r : rows) {
                ids.add(r.getId());
            }
            taskMapper.markDispatched(ids);
        }

        List<TeacherDtos.TaskItem> items = new ArrayList<>(rows.size());
        int done = 0;
        for (TaskRow r : rows) {
            boolean isDone = r.getDone() != null && r.getDone() == 1;
            if (isDone) {
                done++;
            }
            items.add(new TeacherDtos.TaskItem(
                    r.getId(), r.getMemberId(), r.getMemberName(),
                    MaskUtil.maskMobile(r.getMobile()), r.getTeacherName(), r.getTier(),
                    r.getDaysSinceClass(), r.getCountcardLessons(), r.getTimecardDaysLeft(),
                    r.getQueue(), isDone, r.getResultCode(), r.getResultText()));
        }
        return new TeacherDtos.TaskListVO(items.size(), done, items);
    }

    // ---------------------------------------------------------------- 学员详情

    public TeacherDtos.MemberDetailVO member(Long snapshotId, CurrentUser user) {
        DataScope scope = dataScopeService.scopeOf(user);
        TaskRow r = taskMapper.findVisibleMember(snapshotId, scope.all(), scope.staffId());
        if (r == null) {
            // 未核实 / 不属于自己 / 不存在 —— 统一 404
            throw new BizException(404, "记录不存在");
        }

        List<FollowupLogRow> logs = taskMapper.listLogs(r.getMemberId(), 20);
        List<TeacherDtos.LogItem> history = new ArrayList<>(logs.size());
        for (FollowupLogRow l : logs) {
            history.add(new TeacherDtos.LogItem(
                    l.getFollowDate() == null ? null : l.getFollowDate().toString(),
                    l.getResultCode(), l.getResultText(),
                    l.getAmount() == null ? null : l.getAmount().toPlainString(),
                    l.getNote()));
        }

        return new TeacherDtos.MemberDetailVO(
                r.getId(), r.getMemberId(), r.getMemberName(),
                MaskUtil.maskMobile(r.getMobile()), r.getTeacherName(), r.getTier(),
                null, r.getDaysSinceClass(),
                r.getLastClassDate() == null ? null : r.getLastClassDate().toString(),
                r.getCountcardLessons(), r.getTimecardDaysLeft(),
                r.getTimecardExpire() == null ? null : r.getTimecardExpire().toString(),
                r.getQueue(), r.getTalkScript(),
                r.getDone() != null && r.getDone() == 1, history);
    }

    // ---------------------------------------------------------------- 提交结果

    @Transactional
    public void submit(TeacherDtos.SubmitReq req, CurrentUser user) {
        if (req == null || req.memberId() == null) {
            throw new BizException(400, "memberId 不能为空");
        }
        if (req.resultCode() == null || !TeacherDtos.RESULT_LABELS.containsKey(req.resultCode())) {
            throw new BizException(400, "结果码不合法（1–9）");
        }

        DataScope scope = dataScopeService.scopeOf(user);
        TaskRow r = taskMapper.findVisibleByMember(req.memberId(), scope.all(), scope.staffId());
        if (r == null) {
            throw new BizException(404, "记录不存在");
        }

        FollowupLogDO logDO = new FollowupLogDO();
        logDO.setShopId(1L);
        logDO.setSnapshotDate(LocalDate.now());
        logDO.setMemberId(req.memberId());
        logDO.setStaffId(user.userId());
        logDO.setFollowDate(LocalDate.now());
        logDO.setResultCode(req.resultCode());
        logDO.setResultText(req.resultText() == null
                ? TeacherDtos.RESULT_LABELS.get(req.resultCode()) : req.resultText());
        logDO.setAmount(parseAmount(req.amount()));
        logDO.setNote(req.note());

        taskMapper.upsertLog(logDO);
        log.info("提交跟进 memberId={} result={} by={}", req.memberId(), req.resultCode(), user.userId());
    }

    // ---------------------------------------------------------------- 信息有误退回

    @Transactional
    public void flag(TeacherDtos.FlagReq req, CurrentUser user) {
        if (req == null || req.memberId() == null) {
            throw new BizException(400, "memberId 不能为空");
        }
        if (req.note() == null || req.note().isBlank()) {
            throw new BizException(400, "请写明哪里不对，否则老板没法改进数据");
        }

        DataScope scope = dataScopeService.scopeOf(user);
        TaskRow r = taskMapper.findVisibleByMember(req.memberId(), scope.all(), scope.staffId());
        if (r == null) {
            throw new BizException(404, "记录不存在");
        }

        // 回 PENDING + TEACHER_FLAGGED：数据重新进入待核实队列
        reviewMapper.updateStatus(r.getId(), "PENDING", "TEACHER_FLAGGED", req.note(),
                user.userId(), LocalDateTime.now());

        ReviewLogDO logDO = new ReviewLogDO();
        logDO.setSnapshotId(r.getId());
        logDO.setMemberId(r.getMemberId());
        logDO.setStaffId(user.userId());
        logDO.setAction("REOPEN");
        logDO.setNote(req.note());
        reviewMapper.insertLog(logDO);

        log.info("老师退回 memberId={} by={}", req.memberId(), user.userId());
    }

    // ---------------------------------------------------------------- 统计

    public TeacherDtos.StatsVO stats(CurrentUser user) {
        DataScope scope = dataScopeService.scopeOf(user);
        StatsRow row = taskMapper.stats(scope.all(), scope.staffId());
        int total = row == null || row.getTotal() == null ? 0 : row.getTotal();
        int done = row == null || row.getDone() == null ? 0 : row.getDone();

        int pendingReview = 0;
        List<StaffStatRow> byStaff = List.of();
        if (user.isOwner()) {
            pendingReview = taskMapper.countPendingReview();
            byStaff = taskMapper.statsByStaff();
        }
        return new TeacherDtos.StatsVO(total, done, pendingReview, byStaff);
    }

    private static java.math.BigDecimal parseAmount(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            throw new BizException(400, "金额格式不对");
        }
    }
}
