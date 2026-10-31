package com.hareshnarola.enrollment.auth;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param secret HMAC key for HS256; must be at least 32 bytes. Supplied via JWT_SECRET.
 * @param ttl    how long an access token stays valid
 * @param issuer value of the "iss" claim, checked on every request
 */
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32) String secret,
        @NotNull Duration ttl,
        @NotBlank String issuer) {
}
