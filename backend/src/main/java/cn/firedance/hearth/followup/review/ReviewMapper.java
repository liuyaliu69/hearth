package cn.firedance.hearth.followup.review;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 核实工作台读写。SQL 在 resources/mapper/ReviewMapper.xml。
 */
@Mapper
public interface ReviewMapper {

    int countPending(@Param("date") LocalDate date);

    int countUrgent(@Param("date") LocalDate date);

    List<ReasonCount> countByReason(@Param("date") LocalDate date);

    int countClean(@Param("date") LocalDate date);

    List<ReviewItemRow> listByReason(@Param("date") LocalDate date,
                                     @Param("reason") String reason,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    List<ReviewItemRow> listClean(@Param("date") LocalDate date,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    ReviewItemRow findItem(@Param("id") Long id);

    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("reason") String reason,
                     @Param("note") String note,
                     @Param("reviewedBy") Long reviewedBy,
                     @Param("reviewedAt") LocalDateTime reviewedAt);

    int updateMobile(@Param("id") Long id, @Param("mobile") String mobile);

    int updateCountcard(@Param("id") Long id, @Param("lessons") Integer lessons);

    int updateTimecardExpire(@Param("id") Long id, @Param("expire") LocalDate expire);

    int updateTeacher(@Param("id") Long id,
                      @Param("teacherName") String teacherName,
                      @Param("staffId") Long staffId);

    int insertLog(ReviewLogDO log);

    /** 整批处理：先查 id 再按 id 更新，避免「查的和改的不是同一批」 */
    List<Long> findBatchIds(@Param("reason") String reason,
                            @Param("limit") int limit);

    int updateStatusByIds(@Param("ids") List<Long> ids,
                          @Param("status") String status,
                          @Param("reviewedBy") Long reviewedBy,
                          @Param("reviewedAt") LocalDateTime reviewedAt);

    int insertLogBatch(@Param("ids") List<Long> ids,
                       @Param("action") String action,
                       @Param("staffId") Long staffId,
                       @Param("note") String note);

    /** 疑点代号统计行 */
    class ReasonCount {
        private String reasonCode;
        private Integer cnt;

        public String getReasonCode() {
            return reasonCode;
        }

        public void setReasonCode(String reasonCode) {
            this.reasonCode = reasonCode;
        }

        public Integer getCnt() {
            return cnt;
        }

        public void setCnt(Integer cnt) {
            this.cnt = cnt;
        }
    }
}
