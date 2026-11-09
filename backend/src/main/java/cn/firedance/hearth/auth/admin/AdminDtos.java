package cn.firedance.hearth.auth.admin;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理接口的出参。
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** 列表/详情里的用户行 */
    public record UserRow(
            Long id,
            String name,
            String openid,
            Long staffId,
            String staffName,
            String staffMobile,
            String status,
            List<String> roles,
            LocalDateTime lastLoginAt,
            boolean needMobile
    ) {
    }

    /** 新建账号：绑定到哪个员工、用哪个 openid、给什么角色 */
    public record CreateReq(
            Long staffId,
            String openid,
            List<String> roleCodes
    ) {
    }

    /** 修改账号：停用/启用、改角色（都可空，只改传了的） */
    public record UpdateReq(
            String status,
            List<String> roleCodes
    ) {
    }
}
