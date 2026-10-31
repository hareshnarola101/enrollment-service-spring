package com.hareshnarola.enrollment.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.hareshnarola.enrollment.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EnrollmentTest {

    private static final Instant T0 = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);

    @Test
    void newRequestStartsPending() {
        Enrollment enrollment = Enrollment.request(1L, 2L, T0);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(enrollment.holdsSeat()).isFalse();
        assertThat(enrollment.getCreatedAt()).isEqualTo(T0);
    }

    @Test
    void waitlistedEnrollmentCanBePromoted() {
        Enrollment enrollment = Enrollment.request(1L, 2L, T0);
        enrollment.waitlist(T0);

        enrollment.confirm(T1);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(enrollment.holdsSeat()).isTrue();
        assertThat(enrollment.getUpdatedAt()).isEqualTo(T1);
        assertThat(enrollment.getCreatedAt()).isEqualTo(T0);
    }

    @Test
    void cancelledEnrollmentIsFinal() {
        Enrollment enrollment = Enrollment.request(1L, 2L, T0);
        enrollment.confirm(T0);
        enrollment.cancel(T1);

        assertThatThrownBy(() -> enrollment.cancel(T1)).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> enrollment.confirm(T1)).isInstanceOf(BusinessRuleViolationException.class);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    @Test
    void confirmedEnrollmentCannotGoBackToWaitlist() {
        Enrollment enrollment = Enrollment.request(1L, 2L, T0);
        enrollment.confirm(T0);

        assertThatThrownBy(() -> enrollment.waitlist(T1)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void ownershipIsByStudentId() {
        Enrollment enrollment = Enrollment.request(1L, 2L, T0);

        assertThat(enrollment.belongsTo(2L)).isTrue();
        assertThat(enrollment.belongsTo(3L)).isFalse();
    }

    @ParameterizedTest(name = "{0} -> {1} allowed: {2}")
    @CsvSource({
            "PENDING,    CONFIRMED,  true",
            "PENDING,    WAITLISTED, true",
            "PENDING,    CANCELLED,  true",
            "WAITLISTED, CONFIRMED,  true",
            "WAITLISTED, CANCELLED,  true",
            "WAITLISTED, PENDING,    false",
            "CONFIRMED,  CANCELLED,  true",
            "CONFIRMED,  WAITLISTED, false",
            "CONFIRMED,  PENDING,    false",
            "CANCELLED,  CONFIRMED,  false",
            "CANCELLED,  WAITLISTED, false",
    })
    void stateMachine(EnrollmentStatus from, EnrollmentStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
