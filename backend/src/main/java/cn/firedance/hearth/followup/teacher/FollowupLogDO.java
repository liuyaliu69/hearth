package cn.firedance.hearth.followup.teacher;

/** 跟进记录写入载体 */
public class FollowupLogDO {

    private Long shopId;
    private java.time.LocalDate snapshotDate;
    private Long memberId;
    private Long staffId;
    private java.time.LocalDate followDate;
    private Integer resultCode;
    private String resultText;
    private java.math.BigDecimal amount;
    private String note;

    public Long getShopId() {
        return shopId;
    }

    public void setShopId(Long shopId) {
        this.shopId = shopId;
    }

    public java.time.LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(java.time.LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
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

    public java.time.LocalDate getFollowDate() {
        return followDate;
    }

    public void setFollowDate(java.time.LocalDate followDate) {
        this.followDate = followDate;
    }

    public Integer getResultCode() {
        return resultCode;
    }

    public void setResultCode(Integer resultCode) {
        this.resultCode = resultCode;
    }

    public String getResultText() {
        return resultText;
    }

    public void setResultText(String resultText) {
        this.resultText = resultText;
    }

    public java.math.BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(java.math.BigDecimal amount) {
        this.amount = amount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
