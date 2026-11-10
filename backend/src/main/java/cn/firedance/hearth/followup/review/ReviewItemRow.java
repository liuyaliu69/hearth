package cn.firedance.hearth.followup.review;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 核实列表行（POJO，理由同其他 MyBatis 映射）。
 */
public class ReviewItemRow {

    private Long id;
    private Long memberId;
    private Long staffId;
    private String memberName;
    private String mobile;
    private String teacherName;
    private String tier;
    private String activity;
    private Integer daysSinceClass;
    private Integer countcardLessons;
    private Integer timecardDaysLeft;
    private LocalDate timecardExpire;
    private String queue;
    private Integer sortKey;
    private String reviewStatus;
    private String reviewReason;
    private String talkScript;
    private LocalDateTime dispatchedAt;
    private String flags;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getStaffId() {
        return staffId;
    }

    public void setStaffId(Long staffId) {
        this.staffId = staffId;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public String getActivity() {
        return activity;
    }

    public void setActivity(String activity) {
        this.activity = activity;
    }

    public Integer getDaysSinceClass() {
        return daysSinceClass;
    }

    public void setDaysSinceClass(Integer daysSinceClass) {
        this.daysSinceClass = daysSinceClass;
    }

    public Integer getCountcardLessons() {
        return countcardLessons;
    }

    public void setCountcardLessons(Integer countcardLessons) {
        this.countcardLessons = countcardLessons;
    }

    public Integer getTimecardDaysLeft() {
        return timecardDaysLeft;
    }

    public void setTimecardDaysLeft(Integer timecardDaysLeft) {
        this.timecardDaysLeft = timecardDaysLeft;
    }

    public LocalDate getTimecardExpire() {
        return timecardExpire;
    }

    public void setTimecardExpire(LocalDate timecardExpire) {
        this.timecardExpire = timecardExpire;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public Integer getSortKey() {
        return sortKey;
    }

    public void setSortKey(Integer sortKey) {
        this.sortKey = sortKey;
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

    public String getTalkScript() {
        return talkScript;
    }

    public void setTalkScript(String talkScript) {
        this.talkScript = talkScript;
    }

    public LocalDateTime getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(LocalDateTime dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public String getFlags() {
        return flags;
    }

    public void setFlags(String flags) {
        this.flags = flags;
    }
}
