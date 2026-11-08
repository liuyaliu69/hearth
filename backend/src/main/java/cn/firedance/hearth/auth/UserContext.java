package cn.firedance.hearth.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 从 SecurityContext 取当前登录用户。
 *
 * <p>业务代码统一通过这里拿用户，不直接摸 SecurityContextHolder。
 */
public final class UserContext {

    private UserContext() {
    }

    public static Optional<CurrentUser> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof CurrentUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** 需要登录却没登录时抛出，由全局异常处理转成 401 */
    public static CurrentUser require() {
        return current().orElseThrow(() -> new IllegalStateException("未登录"));
    }
}
