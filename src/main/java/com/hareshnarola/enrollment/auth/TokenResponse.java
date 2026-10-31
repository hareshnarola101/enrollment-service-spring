package com.hareshnarola.enrollment.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
