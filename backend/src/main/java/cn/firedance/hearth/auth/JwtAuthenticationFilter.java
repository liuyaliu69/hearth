package cn.firedance.hearth.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * JWT 认证过滤器。
 *
 * <p>关键设计：解析出 userId 后**每次都查库**确认账号仍为 ACTIVE。
 * 这样「停用账号」是立即生效的，而不是等 token 7 天过期。
 * 3 个用户，一次主键查询的成本可以忽略。
 *
 * <p>任何失败都只是「不设置认证」，不做跳转、不写响应 —— 交给 Security 的入口点统一返回 401 JSON。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String HEADER = "Authorization";
    private static final String BEARER = "Bearer ";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final JwtUtil jwtUtil;
    private final AuthMapper authMapper;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, AuthMapper authMapper) {
        this.jwtUtil = jwtUtil;
        this.authMapper = authMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length()).trim();
            Optional<Long> userId = jwtUtil.parseUserId(token);

            if (userId.isPresent()) {
                authenticate(userId.get());
            } else {
                log.debug("token 无效或已过期: {}", request.getRequestURI());
            }
        }

        chain.doFilter(request, response);
    }

    private void authenticate(Long userId) {
        UserAuthDO user = authMapper.findById(userId);
        if (user == null) {
            log.info("token 指向的用户不存在 userId={}", userId);
            return;
        }
        if (!STATUS_ACTIVE.equals(user.getStatus())) {
            log.info("账号已停用，拒绝 userI d={}", userId);
            return;
        }

        List<String> roles = authMapper.findRoleCodes(userId);
        List<GrantedAuthority> authorities = roles.stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority(LoginRoles.authority(r)))
                .toList();

        CurrentUser principal = new CurrentUser(
                user.getId(), user.getName(), user.getStaffId(),
                user.getStaffName(), user.getStaffMobile(), roles);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
