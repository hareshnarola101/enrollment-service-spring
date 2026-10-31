package com.hareshnarola.enrollment.enrollment;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.hareshnarola.enrollment.course.Course;
import com.hareshnarola.enrollment.course.CourseCapacityIncreased;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves waitlisted students into free seats, first come first served. Seats free up in two
 * ways: a confirmed student cancels, or an admin raises capacity. Both callers already
 * hold the course row lock, which is what makes reading the waitlist and taking seats safe.
 */
@Component
class WaitlistPromoter {

    private final EnrollmentRepository enrollments;
    private final EnrollmentEventPublisher events;
    private final Clock clock;

    WaitlistPromoter(EnrollmentRepository enrollments, EnrollmentEventPublisher events, Clock clock) {
        this.enrollments = enrollments;
        this.events = events;
        this.clock = clock;
    }

    /**
     * @param course a course loaded with {@code findByIdForUpdate} in the current transaction
     * @return the enrollments that were promoted
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Enrollment> fillFreeSeats(Course course) {
        int freeSeats = course.freeSeats();
        if (freeSeats <= 0) {
            return List.of();
        }
        List<Enrollment> next = enrollments.findByCourseIdAndStatusOrderByCreatedAtAscIdAsc(
                course.getId(), EnrollmentStatus.WAITLISTED, Limit.of(freeSeats));
        Instant now = clock.instant();
        for (Enrollment enrollment : next) {
            course.takeSeat(now);
            enrollment.confirm(now);
            events.promotedFromWaitlist(enrollment);
        }
        return next;
    }

    @EventListener
    void onCapacityIncreased(CourseCapacityIncreased event) {
        fillFreeSeats(event.course());
    }
}
