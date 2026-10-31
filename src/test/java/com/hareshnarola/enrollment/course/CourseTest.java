package com.hareshnarola.enrollment.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.hareshnarola.enrollment.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

class CourseTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Test
    void takesSeatsUntilFull() {
        Course course = new Course("JAVA-101", "Java", null, 2, NOW);

        course.takeSeat(NOW);
        course.takeSeat(NOW);

        assertThat(course.getSeatsTaken()).isEqualTo(2);
        assertThat(course.hasFreeSeat()).isFalse();
        assertThat(course.freeSeats()).isZero();
        assertThatThrownBy(() -> course.takeSeat(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void releasingASeatMakesItAvailableAgain() {
        Course course = new Course("JAVA-101", "Java", null, 1, NOW);
        course.takeSeat(NOW);

        course.releaseSeat(NOW);

        assertThat(course.hasFreeSeat()).isTrue();
        assertThatThrownBy(() -> course.releaseSeat(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reportsWhenCapacityGrows() {
        Course course = new Course("JAVA-101", "Java", null, 2, NOW);

        assertThat(course.changeCapacity(5, NOW)).isTrue();
        assertThat(course.changeCapacity(5, NOW)).isFalse();
        assertThat(course.changeCapacity(3, NOW)).isFalse();
        assertThat(course.getCapacity()).isEqualTo(3);
    }

    @Test
    void refusesCapacityBelowConfirmedSeats() {
        Course course = new Course("JAVA-101", "Java", null, 3, NOW);
        course.takeSeat(NOW);
        course.takeSeat(NOW);

        assertThatThrownBy(() -> course.changeCapacity(1, NOW))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("2 seats");
        assertThat(course.getCapacity()).isEqualTo(3);
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> new Course("JAVA-101", "Java", null, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
