package cn.firedance.hearth.followup.importer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 写入 followup_snapshot 的载体（MyBatis 用 useGeneratedKeys 回填 id）。
 *
 * <p>字段多但都是「一行数据」，刻意用 POJO 而不是 record：
 * MyBatis 映射 record 需要额外配置，而 insert 又需要 setter。
 */
public class SnapshotDO {

    private Long id;
    private Long shopId;
    private LocalDate snapshotDate;
    private String importBatch;

    private Long memberId;
    private Long staffId;

    private String memberName;
    private String mobile;
    private String teacherName;

    private String tier;
    private String activity;
    private LocalDate lastClassDate;
    private Integer daysSinceClass;
    private Integer attendCount;
    private LocalDate timecardExpire;
    private Integer timecardDaysLeft;
    private Integer countcardLessons;
    private Integer countcardDaysLeft;
    private LocalDate lastSignDate;
    private String lastProduct;
    private BigDecimal lastSignAmount;
    private String talkScript;
    private BigDecimal totalPaid;
    private Integer signCount;
    private Integer bought199;
    private String timecardKind;
    private String countcardKind;

    /** 未映射的 16 列，原样留存（JSON 字符串） */
    private String extraJson;

    private String queue;
    private Integer sortKey;

    private String reviewStatus;
    private String reviewReason;
    private java.time.LocalDateTime dispatchedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getShopId() {
        return shopId;
    }

    public void setShopId(Long shopId) {
        this.shopId = shopId;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public String getImportBatch() {
        return importBatch;
    }

    public void setImportBatch(String importBatch) {
        this.importBatch = importBatch;
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

    public LocalDate getLastClassDate() {
        return lastClassDate;
    }

    public void setLastClassDate(LocalDate lastClassDate) {
        this.lastClassDate = lastClassDate;
    }

    public Integer getDaysSinceClass() {
        return daysSinceClass;
    }

    public void setDaysSinceClass(Integer daysSinceClass) {
        this.daysSinceClass = daysSinceClass;
    }

    public Integer getAttendCount() {
        return attendCount;
    }

    public void setAttendCount(Integer attendCount) {
        this.attendCount = attendCount;
    }

    public LocalDate getTimecardExpire() {
        return timecardExpire;
    }

    public void setTimecardExpire(LocalDate timecardExpire) {
        this.timecardExpire = timecardExpire;
    }

    public Integer getTimecardDaysLeft() {
        return timecardDaysLeft;
    }

    public void setTimecardDaysLeft(Integer timecardDaysLeft) {
        this.timecardDaysLeft = timecardDaysLeft;
    }

    public Integer getCountcardLessons() {
        return countcardLessons;
    }

    public void setCountcardLessons(Integer countcardLessons) {
        this.countcardLessons = countcardLessons;
    }

    public Integer getCountcardDaysLeft() {
        return countcardDaysLeft;
    }

    public void setCountcardDaysLeft(Integer countcardDaysLeft) {
        this.countcardDaysLeft = countcardDaysLeft;
    }

    public LocalDate getLastSignDate() {
        return lastSignDate;
    }

    public void setLastSignDate(LocalDate lastSignDate) {
        this.lastSignDate = lastSignDate;
    }

    public String getLastProduct() {
        return lastProduct;
    }

    public void setLastProduct(String lastProduct) {
        this.lastProduct = lastProduct;
    }

    public BigDecimal getLastSignAmount() {
        return lastSignAmount;
    }

    public void setLastSignAmount(BigDecimal lastSignAmount) {
        this.lastSignAmount = lastSignAmount;
    }

    public String getTalkScript() {
        return talkScript;
    }

    public void setTalkScript(String talkScript) {
        this.talkScript = talkScript;
    }

    public BigDecimal getTotalPaid() {
        return totalPaid;
    }

    public void setTotalPaid(BigDecimal totalPaid) {
        this.totalPaid = totalPaid;
    }

    public Integer getSignCount() {
        return signCount;
    }

    public void setSignCount(Integer signCount) {
        this.signCount = signCount;
    }

    public Integer getBought199() {
        return bought199;
    }

    public void setBought199(Integer bought199) {
        this.bought199 = bought199;
    }

    public String getTimecardKind() {
        return timecardKind;
    }

    public void setTimecardKind(String timecardKind) {
        this.timecardKind = timecardKind;
    }

    public String getCountcardKind() {
        return countcardKind;
    }

    public void setCountcardKind(String countcardKind) {
        this.countcardKind = countcardKind;
    }

    public String getExtraJson() {
        return extraJson;
    }

    public void setExtraJson(String extraJson) {
        this.extraJson = extraJson;
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

    public java.time.LocalDateTime getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(java.time.LocalDateTime dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }
}
