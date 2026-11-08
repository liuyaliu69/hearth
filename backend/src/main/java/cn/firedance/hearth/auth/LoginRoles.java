package cn.firedance.hearth.auth;

/**
 * 角色代号常量。与 app_role.code 保持一致。
 */
public final class LoginRoles {

    public static final String OWNER = "owner";
    public static final String TEACHER = "teacher";
    public static final String OPERATOR = "operator";

    /** Spring Security 的 authorities 前缀 */
    public static final String PREFIX = "ROLE_";

    private LoginRoles() {
    }

    public static String authority(String roleCode) {
        return PREFIX + roleCode.toUpperCase();
    }
}
