package com.hareshnarola.enrollment.auth;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

import com.hareshnarola.enrollment.common.BusinessRuleViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;
    private final Clock clock;
    // Compared against when the email is unknown, so a failed login takes the same time
    // whether or not the account exists.
    private final String dummyHash;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder,
                       JwtTokenService tokens, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    @Transactional
    public UserResponse registerStudent(RegisterRequest request) {
        return UserResponse.from(createUser(request.email(), request.password(), request.fullName(), Role.STUDENT));
    }

    @Transactional
    public UserAccount createUser(String email, String rawPassword, String fullName, Role role) {
        String normalizedEmail = normalize(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw new BusinessRuleViolationException("An account with this email already exists");
        }
        UserAccount user = new UserAccount(normalizedEmail, passwordEncoder.encode(rawPassword),
                fullName.trim(), role, clock.instant());
        return users.save(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Optional<UserAccount> user = users.findByEmail(normalize(request.email()));
        String hash = user.map(UserAccount::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !matches) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return tokens.issue(user.get());
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
