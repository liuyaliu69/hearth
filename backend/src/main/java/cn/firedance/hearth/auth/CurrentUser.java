package cn.firedance.hearth.auth;

import java.util.List;

/**
 * 已登录用户。放在 SecurityContext 的 principal 里。
 *
 * <p>字段刻意保持最少：只放鉴权与展示需要的东西。角色不进 token，每次请求从库里查。
 *
 * @param userId       app_user.id
 * @param name         显示名（app_user.name）
 * @param staffId      关联的员工 id，可为空
 * @param staffName    员工姓名
 * @param staffMobile  员工手机号（可空 —— 空则 needMobile=true）
 * @param roles        角色 code 列表，如 ["owner"]
 */
public record CurrentUser(
        Long userId,
        String name,
        Long staffId,
        String staffName,
        String staffMobile,
        List<String> roles
) {

    public boolean isOwner() {
        return roles != null && roles.contains(LoginRoles.OWNER);
    }

    public boolean hasRole(String roleCode) {
        return roles != null && roles.contains(roleCode);
    }

    /** 员工资料里没有手机号 —— 前端据此强提醒补充 */
    public boolean needMobile() {
        return staffMobile == null || staffMobile.isBlank();
    }
}
