package com.hareshnarola.enrollment.auth;

import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authenticated caller as seen by the service layer. Services take this instead of
 * reaching into the SecurityContext, which keeps them easy to unit test.
 */
public record CurrentUser(Long id, Role role) {

    public static CurrentUser from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(JwtTokenService.ROLES_CLAIM);
        Role role = roles != null && roles.contains(Role.ADMIN.name()) ? Role.ADMIN : Role.STUDENT;
        return new CurrentUser(Long.valueOf(jwt.getSubject()), role);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
