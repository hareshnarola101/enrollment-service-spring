package com.hareshnarola.enrollment.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.hareshnarola.enrollment.auth.CurrentUser;
import com.hareshnarola.enrollment.auth.Role;
import com.hareshnarola.enrollment.common.BusinessRuleViolationException;
import com.hareshnarola.enrollment.common.NotFoundException;
import com.hareshnarola.enrollment.course.Course;
import com.hareshnarola.enrollment.course.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final long COURSE_ID = 10L;
    private static final long ENROLLMENT_ID = 100L;
    private static final CurrentUser STUDENT = new CurrentUser(42L, Role.STUDENT);

    @Mock
    private CourseRepository courses;
    @Mock
    private EnrollmentRepository enrollments;
    @Mock
    private WaitlistPromoter waitlist;
    @Mock
    private EnrollmentEventPublisher events;

    private EnrollmentService service;

    @BeforeEach
    void setUp() {
        service = new EnrollmentService(courses, enrollments, waitlist, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Nested
    class Enroll {

        @Test
        void confirmsWhenASeatIsFree() {
            Course course = course(2, 1);
            givenLockedCourse(course);
            givenSaveAssignsId();

            EnrollmentResponse response = service.enroll(COURSE_ID, STUDENT);

            assertThat(response.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
            assertThat(response.studentId()).isEqualTo(STUDENT.id());
            assertThat(course.getSeatsTaken()).isEqualTo(2);
            verify(events).confirmed(any(Enrollment.class));
        }

        @Test
        void waitlistsWhenTheCourseIsFull() {
            Course course = course(1, 1);
            givenLockedCourse(course);
            givenSaveAssignsId();

            EnrollmentResponse response = service.enroll(COURSE_ID, STUDENT);

            assertThat(response.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
            assertThat(course.getSeatsTaken()).isEqualTo(1);
            verify(events, never()).confirmed(any());
        }

        @Test
        void rejectsASecondActiveEnrollment() {
            givenLockedCourse(course(5, 0));
            when(enrollments.existsByCourseIdAndStudentIdAndStatusIn(COURSE_ID, STUDENT.id(),
                    EnrollmentStatus.ACTIVE)).thenReturn(true);

            assertThatThrownBy(() -> service.enroll(COURSE_ID, STUDENT))
                    .isInstanceOf(BusinessRuleViolationException.class);
            verify(enrollments, never()).save(any());
            verifyNoInteractions(events);
        }

        @Test
        void readsSeatsOnlyThroughTheRowLock() {
            givenLockedCourse(course(5, 0));
            givenSaveAssignsId();

            service.enroll(COURSE_ID, STUDENT);

            // The capacity check is only safe if it runs against the locked row.
            InOrder order = inOrder(courses, enrollments);
            order.verify(courses).findByIdForUpdate(COURSE_ID);
            order.verify(enrollments).existsByCourseIdAndStudentIdAndStatusIn(anyLong(), anyLong(), any());
            verify(courses, never()).findById(anyLong());
        }

        @Test
        void failsForAnUnknownCourse() {
            when(courses.findByIdForUpdate(COURSE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.enroll(COURSE_ID, STUDENT)).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Cancel {

        @Test
        void releasesTheSeatAndPromotesFromTheWaitlist() {
            Course course = course(1, 1);
            Enrollment enrollment = confirmedEnrollment(STUDENT.id());
            givenEnrollmentInLockedCourse(course, enrollment);

            EnrollmentResponse response = service.cancel(ENROLLMENT_ID, STUDENT);

            assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(course.getSeatsTaken()).isZero();
            InOrder order = inOrder(events, waitlist);
            order.verify(events).cancelled(enrollment);
            order.verify(waitlist).fillFreeSeats(course);
        }

        @Test
        void lockingTheCourseHappensBeforeTheEnrollmentIsRead() {
            givenEnrollmentInLockedCourse(course(1, 1), confirmedEnrollment(STUDENT.id()));

            service.cancel(ENROLLMENT_ID, STUDENT);

            // Reading the enrollment before the lock could act on a status that another
            // transaction is about to change.
            InOrder order = inOrder(enrollments, courses);
            order.verify(enrollments).findCourseIdById(ENROLLMENT_ID);
            order.verify(courses).findByIdForUpdate(COURSE_ID);
            order.verify(enrollments).findById(ENROLLMENT_ID);
        }

        @Test
        void cancellingAWaitlistedEnrollmentDoesNotPromoteAnyone() {
            Course course = course(1, 1);
            Enrollment enrollment = Enrollment.request(COURSE_ID, STUDENT.id(), NOW);
            enrollment.waitlist(NOW);
            withId(enrollment, ENROLLMENT_ID);
            givenEnrollmentInLockedCourse(course, enrollment);

            service.cancel(ENROLLMENT_ID, STUDENT);

            assertThat(course.getSeatsTaken()).isEqualTo(1);
            verify(events).cancelled(enrollment);
            verifyNoInteractions(waitlist);
        }

        @Test
        void studentsCannotCancelSomeoneElsesEnrollment() {
            Course course = course(1, 1);
            Enrollment enrollment = confirmedEnrollment(99L);
            givenEnrollmentInLockedCourse(course, enrollment);

            assertThatThrownBy(() -> service.cancel(ENROLLMENT_ID, STUDENT))
                    .isInstanceOf(AccessDeniedException.class);
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
            assertThat(course.getSeatsTaken()).isEqualTo(1);
            verifyNoInteractions(events, waitlist);
        }

        @Test
        void adminsCanCancelAnyEnrollment() {
            Enrollment enrollment = confirmedEnrollment(99L);
            givenEnrollmentInLockedCourse(course(1, 1), enrollment);

            EnrollmentResponse response = service.cancel(ENROLLMENT_ID, new CurrentUser(1L, Role.ADMIN));

            assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
        }

        @Test
        void cancellingTwiceIsAConflict() {
            Enrollment enrollment = confirmedEnrollment(STUDENT.id());
            enrollment.cancel(NOW);
            givenEnrollmentInLockedCourse(course(1, 0), enrollment);

            assertThatThrownBy(() -> service.cancel(ENROLLMENT_ID, STUDENT))
                    .isInstanceOf(BusinessRuleViolationException.class);
            verifyNoInteractions(events, waitlist);
        }

        @Test
        void failsForAnUnknownEnrollment() {
            when(enrollments.findCourseIdById(ENROLLMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancel(ENROLLMENT_ID, STUDENT)).isInstanceOf(NotFoundException.class);
            verifyNoInteractions(courses);
        }
    }

    private void givenLockedCourse(Course course) {
        when(courses.findByIdForUpdate(COURSE_ID)).thenReturn(Optional.of(course));
    }

    private void givenEnrollmentInLockedCourse(Course course, Enrollment enrollment) {
        when(enrollments.findCourseIdById(ENROLLMENT_ID)).thenReturn(Optional.of(COURSE_ID));
        givenLockedCourse(course);
        when(enrollments.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
    }

    private void givenSaveAssignsId() {
        when(enrollments.save(any(Enrollment.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), ENROLLMENT_ID));
    }

    private static Course course(int capacity, int seatsTaken) {
        Course course = new Course("JAVA-101", "Java", null, capacity, NOW);
        ReflectionTestUtils.setField(course, "id", COURSE_ID);
        for (int i = 0; i < seatsTaken; i++) {
            course.takeSeat(NOW);
        }
        return course;
    }

    private static Enrollment confirmedEnrollment(Long studentId) {
        Enrollment enrollment = Enrollment.request(COURSE_ID, studentId, NOW);
        enrollment.confirm(NOW);
        return withId(enrollment, ENROLLMENT_ID);
    }

    private static Enrollment withId(Enrollment enrollment, long id) {
        ReflectionTestUtils.setField(enrollment, "id", id);
        return enrollment;
    }
}
