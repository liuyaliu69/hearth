package cn.firedance.hearth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 鉴权骨架。
 *
 * <p>P0 阶段只做两件事：放行探针、其余一律要求认证。
 * 登录与角色规则在 C0 接入（微信 openid + JWT），届时在此处加：
 * <ul>
 *   <li>JwtAuthenticationFilter</li>
 *   <li>/api/auth/** 放行</li>
 *   <li>/api/*&#47;review/** 要求 owner 角色</li>
 * </ul>
 *
 * <p>显式关闭 csrf / cors / session / formLogin / httpBasic：
 * 小程序用 token，不用 cookie，这些默认值全都用不上，留着只会制造调试噪音。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 未登录：401 + 统一 JSON，而不是 Spring 默认的空 body 403。 */
    private static final String UNAUTHORIZED_BODY =
            "{\"code\":401,\"msg\":\"unauthorized\",\"data\":null}";
    /** 已登录但无权限：403 + 统一 JSON。 */
    private static final String FORBIDDEN_BODY =
            "{\"code\":403,\"msg\":\"forbidden\",\"data\":null}";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> writeJson(res, 401, UNAUTHORIZED_BODY))
                        .accessDeniedHandler((req, res, e) -> writeJson(res, 403, FORBIDDEN_BODY)));
        return http.build();
    }

    private static void writeJson(jakarta.servlet.http.HttpServletResponse res, int status, String body)
            throws java.io.IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(body);
    }
}
