package com.hareshnarola.enrollment.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.hareshnarola.enrollment.common.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final String DUMMY_HASH = "$dummy";

    @Mock
    private UserAccountRepository users;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenService tokens;

    private AuthService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode("timing-equalizer-not-a-real-password")).thenReturn(DUMMY_HASH);
        service = new AuthService(users, passwordEncoder, tokens, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registersStudentWithNormalizedEmailAndHashedPassword() {
        when(passwordEncoder.encode("s3cret-pass")).thenReturn("$hashed");
        when(users.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = service.registerStudent(
                new RegisterRequest("  Asha@Example.COM ", "s3cret-pass", "Asha Patel"));

        assertThat(response.email()).isEqualTo("asha@example.com");
        assertThat(response.role()).isEqualTo(Role.STUDENT);
        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$hashed");
    }

    @Test
    void rejectsDuplicateEmail() {
        when(users.existsByEmail("asha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.registerStudent(
                new RegisterRequest("asha@example.com", "s3cret-pass", "Asha Patel")))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(users, never()).save(any());
    }

    @Test
    void issuesTokenForCorrectPassword() {
        UserAccount user = new UserAccount("asha@example.com", "$hashed", "Asha", Role.STUDENT, NOW);
        TokenResponse token = new TokenResponse("jwt", "Bearer", 3600);
        when(users.findByEmail("asha@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("s3cret-pass", "$hashed")).thenReturn(true);
        when(tokens.issue(user)).thenReturn(token);

        assertThat(service.login(new LoginRequest("Asha@example.com", "s3cret-pass"))).isEqualTo(token);
    }

    @Test
    void rejectsWrongPassword() {
        UserAccount user = new UserAccount("asha@example.com", "$hashed", "Asha", Role.STUDENT, NOW);
        when(users.findByEmail("asha@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
        verify(tokens, never()).issue(any());
    }

    @Test
    void unknownEmailStillPaysForAPasswordCheck() {
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "whatever")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(passwordEncoder).matches("whatever", DUMMY_HASH);
    }
}
