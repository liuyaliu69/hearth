package cn.firedance.hearth.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * JWT 签发与解析。
 *
 * <p>载荷**只放** {@code sub}(userId)、{@code iat}、{@code exp}。
 * <b>刻意不放角色</b>：角色变更与账号停用必须立即生效，放进 token 就得等 7 天。
 */
@Component
public class JwtUtil {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Duration ttl;

    public JwtUtil(JwtEncoder encoder,
                   JwtDecoder decoder,
                   @Value("${app.jwt.ttl-days:7}") long ttlDays) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.ttl = Duration.ofDays(ttlDays);
    }

    public IssuedToken issue(long userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new IssuedToken(token, expiresAt);
    }

    /** 解析失败（过期、篡改、格式错）统一返回 empty，调用方按未登录处理 */
    public Optional<Long> parseUserId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jwt jwt = decoder.decode(token);
            return Optional.of(Long.valueOf(jwt.getSubject()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Duration ttl() {
        return ttl;
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
