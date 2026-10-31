package com.hareshnarola.enrollment.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenServiceTest {

    private static final JwtProperties PROPERTIES =
            new JwtProperties("test-secret-that-is-at-least-32-bytes-long", Duration.ofMinutes(30), "enrollment-service");

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void issuedTokenRoundTripsThroughTheDecoder() {
        // Issue "now" so the decoder's expiry check passes.
        JwtTokenService service = new JwtTokenService(config.jwtEncoder(PROPERTIES), PROPERTIES, Clock.systemUTC());

        TokenResponse token = service.issue(user(7L, Role.ADMIN));
        Jwt jwt = config.jwtDecoder(PROPERTIES).decode(token.accessToken());

        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(1800);
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("enrollment-service");
        assertThat(CurrentUser.from(jwt)).isEqualTo(new CurrentUser(7L, Role.ADMIN));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtProperties other = new JwtProperties("a-different-secret-also-32-bytes-long!!", Duration.ofMinutes(30),
                "enrollment-service");
        String token = new JwtTokenService(config.jwtEncoder(other), other, Clock.systemUTC())
                .issue(user(7L, Role.ADMIN)).accessToken();

        JwtDecoder decoder = config.jwtDecoder(PROPERTIES);

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String token = new JwtTokenService(config.jwtEncoder(PROPERTIES), PROPERTIES, twoHoursAgo)
                .issue(user(7L, Role.STUDENT)).accessToken();

        assertThatThrownBy(() -> config.jwtDecoder(PROPERTIES).decode(token)).isInstanceOf(JwtException.class);
    }

    private static UserAccount user(long id, Role role) {
        UserAccount user = new UserAccount("user@example.com", "$hash", "User", role, Instant.now());
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
