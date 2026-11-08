package cn.firedance.hearth.auth;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 鉴权相关的出入参。集中放一处，避免十几个小文件。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 登录请求：小程序 wx.login 拿到的 code */
    public record LoginReq(@NotBlank(message = "不能为空") String code) {
    }

    /** 用户视图（返回给前端） */
    public record UserVO(
            Long id,
            String name,
            Long staffId,
            String staffName,
            List<String> roles
    ) {
    }

    /** 登录响应 */
    public record LoginVO(
            String token,
            String expireAt,
            UserVO user,
            boolean needMobile
    ) {
    }

    /** 当前用户响应 */
    public record MeVO(
            UserVO user,
            boolean needMobile
    ) {
    }
}
