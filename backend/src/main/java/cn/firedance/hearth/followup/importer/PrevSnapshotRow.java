package cn.firedance.hearth.followup.importer;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 上一期快照的查询载体（POJO，理由：MyBatis 映射 record 需要额外配置）。
 */
public class PrevSnapshotRow {

    private Long id;
    private String mobile;
    private Integer countcardLessons;
    private LocalDate timecardExpire;
    private String teacherName;
    private String reviewStatus;
    private String reviewReason;
    private LocalDateTime dispatchedAt;

    public PrevSnapshot toRecord() {
        return new PrevSnapshot(id, mobile, countcardLessons, timecardExpire, teacherName,
                reviewStatus, reviewReason, dispatchedAt);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public Integer getCountcardLessons() {
        return countcardLessons;
    }

    public void setCountcardLessons(Integer countcardLessons) {
        this.countcardLessons = countcardLessons;
    }

    public LocalDate getTimecardExpire() {
        return timecardExpire;
    }

    public void setTimecardExpire(LocalDate timecardExpire) {
        this.timecardExpire = timecardExpire;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
    }

    public LocalDateTime getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(LocalDateTime dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }
}
