package cn.firedance.hearth.followup.teacher;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 老师端只读查询 + 跟进记录写入。
 *
 * <p><b>可见性单点收敛（T17）</b>：{@code review_status = 'VERIFIED'} 只出现在
 * {@code TaskMapper.xml} 的 {@code <sql id="visible">} 片段里一次，
 * 所有老师端查询都 include 它。散落成三处就一定会漏一处。
 */
@Mapper
public interface TaskMapper {

    /** 今日待办（已核实 + 有队列 + 数据范围） */
    List<TaskRow> listTasks(@Param("all") boolean all,
                            @Param("staffId") Long staffId,
                            @Param("limit") int limit);

    /** 首次进入老师视野时写 dispatched_at（懒分发） */
    int markDispatched(@Param("ids") List<Long> ids);

    /** 学员详情：同样必须可见（越权/未核实一律查不到 → 404） */
    TaskRow findVisibleMember(@Param("snapshotId") Long snapshotId,
                              @Param("all") boolean all,
                              @Param("staffId") Long staffId);

    /** 按 member_id 找最新可见快照 */
    TaskRow findVisibleByMember(@Param("memberId") Long memberId,
                                @Param("all") boolean all,
                                @Param("staffId") Long staffId);

    List<FollowupLogRow> listLogs(@Param("memberId") Long memberId,
                                  @Param("limit") int limit);

    int upsertLog(FollowupLogDO log);

    StatsRow stats(@Param("all") boolean all, @Param("staffId") Long staffId);

    List<StaffStatRow> statsByStaff();

    int countPendingReview();
}
