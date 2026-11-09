package cn.firedance.hearth.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWT 签发与解析。
 *
 * <p>注意：NimbusJwtEncoder 不允许签发 {@code expiresAt <= issuedAt} 的 token，
 * 所以「过期」用例没法靠负有效期构造，得手写一份**过去签发、已过期**的 token。
 */
class JwtUtilTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private JwtUtil jwtUtil;
    private JwtEncoder encoder;
    private JwtUtil otherKeyJwtUtil;

    @BeforeEach
    void setUp() {
        SecretKey key = key(SECRET);
        encoder = encoder(key);
        jwtUtil = new JwtUtil(encoder, decoder(key), 7);
        otherKeyJwtUtil = new JwtUtil(encoder(key("ffffffffffffffffffffffffffffffff")),
                decoder(key("ffffffffffffffffffffffffffffffff")), 7);
    }

    private static SecretKey key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static JwtEncoder encoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    private static JwtDecoder decoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** 直接编码一个已过期的 token（10 天前签发，1 天前过期） */
    private String expiredToken(long userId) {
        Instant issued = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant expires = Instant.now().minus(1, ChronoUnit.DAYS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(issued)
                .expiresAt(expires)
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    @DisplayName("签发后能解析出同一个 userId")
    void issueAndParse() {
        String token = jwtUtil.issue(42L).token();
        Optional<Long> parsed = jwtUtil.parseUserId(token);
        assertThat(parsed).contains(42L);
    }

    @Test
    @DisplayName("过期 token 解析失败（返回 empty，不抛异常）")
    void expiredTokenReturnsEmpty() {
        assertThat(jwtUtil.parseUserId(expiredToken(42L))).isEmpty();
    }

    @Test
    @DisplayName("换个密钥签的 token 解析失败")
    void tokenFromOtherKeyRejected() {
        String token = otherKeyJwtUtil.issue(7L).token();
        assertThat(jwtUtil.parseUserId(token)).isEmpty();
    }

    @Test
    @DisplayName("空值、乱码、篡改都不抛异常，一律 empty")
    void garbageReturnsEmpty() {
        assertThat(jwtUtil.parseUserId(null)).isEmpty();
        assertThat(jwtUtil.parseUserId("")).isEmpty();
        assertThat(jwtUtil.parseUserId("not-a-jwt")).isEmpty();

        String token = jwtUtil.issue(1L).token();
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThat(jwtUtil.parseUserId(tampered)).isEmpty();
    }

    @Test
    @DisplayName("token 里不含角色信息（改角色不该依赖 token 过期）")
    void tokenPayloadHasNoRoles() {
        String token = jwtUtil.issue(1L).token();
        String payload = new String(Base64.getUrlDecoder()
                .decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payload).doesNotContain("owner", "teacher", "roles");
    }
}
