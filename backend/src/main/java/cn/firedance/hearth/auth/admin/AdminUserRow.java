package cn.firedance.hearth.auth.admin;

import java.time.LocalDateTime;

/**
 * 管理列表用的行对象（普通 POJO）。
 *
 * <p>刻意不用 record：MyBatis 把结果集映射到 record 需要开启
 * {@code arg-name-based-constructor-auto-mapping}，否则要写一堆 resultMap。
 * 用 POJO + setter 是最省事也最不会踩坑的做法，服务层再转成出参 record。
 */
public class AdminUserRow {

    private Long id;
    private String name;
    private String openid;
    private Long staffId;
    private String staffName;
    private String staffMobile;
    private String status;
    private LocalDateTime lastLoginAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOpenid() {
        return openid;
    }

    public void setOpenid(String openid) {
        this.openid = openid;
    }

    public Long getStaffId() {
        return staffId;
    }

    public void setStaffId(Long staffId) {
        this.staffId = staffId;
    }

    public String getStaffName() {
        return staffName;
    }

    public void setStaffName(String staffName) {
        this.staffName = staffName;
    }

    public String getStaffMobile() {
        return staffMobile;
    }

    public void setStaffMobile(String staffMobile) {
        this.staffMobile = staffMobile;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
