package com.hareshnarola.enrollment.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional first admin account, created on startup if it does not exist yet.
 */
@ConfigurationProperties(prefix = "app.bootstrap-admin")
public record BootstrapAdminProperties(String email, String password) {
}
