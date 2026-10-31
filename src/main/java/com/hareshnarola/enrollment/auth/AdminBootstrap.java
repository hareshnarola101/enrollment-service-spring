package com.hareshnarola.enrollment.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates the first admin account from configuration so a fresh environment is usable
 * without hand-written SQL. Does nothing if the properties are empty or the user exists.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final BootstrapAdminProperties properties;
    private final UserAccountRepository users;
    private final AuthService authService;

    AdminBootstrap(BootstrapAdminProperties properties, UserAccountRepository users, AuthService authService) {
        this.properties = properties;
        this.users = users;
        this.authService = authService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
            return;
        }
        String email = AuthService.normalize(properties.email());
        if (users.existsByEmail(email)) {
            return;
        }
        authService.createUser(email, properties.password(), "Administrator", Role.ADMIN);
        log.info("Created bootstrap admin account {}", email);
    }
}
