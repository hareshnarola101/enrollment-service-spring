package com.hareshnarola.enrollment.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.hareshnarola.enrollment.course.Course;
import com.hareshnarola.enrollment.course.CourseCapacityIncreased;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WaitlistPromoterTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final long COURSE_ID = 10L;

    @Mock
    private EnrollmentRepository enrollments;
    @Mock
    private EnrollmentEventPublisher events;

    private WaitlistPromoter promoter;

    @BeforeEach
    void setUp() {
        promoter = new WaitlistPromoter(enrollments, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void promotesTheOldestWaitlistedStudentsIntoFreeSeats() {
        Course course = course(3, 1);
        Enrollment first = waitlisted(1L, 201L);
        Enrollment second = waitlisted(2L, 202L);
        when(enrollments.findByCourseIdAndStatusOrderByCreatedAtAscIdAsc(
                COURSE_ID, EnrollmentStatus.WAITLISTED, Limit.of(2))).thenReturn(List.of(first, second));

        List<Enrollment> promoted = promoter.fillFreeSeats(course);

        assertThat(promoted).containsExactly(first, second);
        assertThat(promoted).allMatch(e -> e.getStatus() == EnrollmentStatus.CONFIRMED);
        assertThat(course.getSeatsTaken()).isEqualTo(3);
        InOrder order = Mockito.inOrder(events);
        order.verify(events).promotedFromWaitlist(first);
        order.verify(events).promotedFromWaitlist(second);
    }

    @Test
    void promotesOnlyAsManyAsAreWaiting() {
        Course course = course(5, 1);
        Enrollment only = waitlisted(1L, 201L);
        when(enrollments.findByCourseIdAndStatusOrderByCreatedAtAscIdAsc(
                COURSE_ID, EnrollmentStatus.WAITLISTED, Limit.of(4))).thenReturn(List.of(only));

        promoter.fillFreeSeats(course);

        assertThat(course.getSeatsTaken()).isEqualTo(2);
        assertThat(course.freeSeats()).isEqualTo(3);
    }

    @Test
    void doesNothingWhenTheCourseIsFull() {
        Course course = course(2, 2);

        assertThat(promoter.fillFreeSeats(course)).isEmpty();
        verifyNoInteractions(enrollments, events);
    }

    @Test
    void fillsNewSeatsWhenCapacityIncreases() {
        Course course = course(2, 1);
        Enrollment waiting = waitlisted(1L, 201L);
        when(enrollments.findByCourseIdAndStatusOrderByCreatedAtAscIdAsc(
                COURSE_ID, EnrollmentStatus.WAITLISTED, Limit.of(1))).thenReturn(List.of(waiting));

        promoter.onCapacityIncreased(new CourseCapacityIncreased(course));

        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        verify(events).promotedFromWaitlist(waiting);
    }

    private static Course course(int capacity, int seatsTaken) {
        Course course = new Course("JAVA-101", "Java", null, capacity, NOW);
        ReflectionTestUtils.setField(course, "id", COURSE_ID);
        for (int i = 0; i < seatsTaken; i++) {
            course.takeSeat(NOW);
        }
        return course;
    }

    private static Enrollment waitlisted(long id, long studentId) {
        Enrollment enrollment = Enrollment.request(COURSE_ID, studentId, NOW.minusSeconds(100 - id));
        enrollment.waitlist(NOW);
        ReflectionTestUtils.setField(enrollment, "id", id);
        return enrollment;
    }
}
