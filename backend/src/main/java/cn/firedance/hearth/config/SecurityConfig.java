package cn.firedance.hearth.config;

import cn.firedance.hearth.auth.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全规则。
 *
 * <p>路径策略：
 * <ul>
 *   <li>{@code /api/health}、{@code /api/auth/login} —— 放行</li>
 *   <li>{@code /api/admin/**} —— 必须是 owner</li>
 *   <li>其余 —— 登录即可</li>
 * </ul>
 *
 * <p>显式关闭 csrf / cors / session / formLogin / httpBasic：
 * 小程序用 token，不用 cookie，这些默认值全都用不上，留着只会制造调试噪音。
 *
 * <p>401 / 403 统一返回 JSON —— 前端按 {@code code} 判断，不解析 HTML 错误页。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String UNAUTHORIZED_BODY =
            "{\"code\":401,\"msg\":\"未登录或登录已过期\",\"data\":null}";
    private static final String FORBIDDEN_BODY =
            "{\"code\":403,\"msg\":\"没有权限\",\"data\":null}";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/auth/login").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("OWNER")
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
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
