package com.hareshnarola.enrollment.enrollment;

import java.time.Clock;
import java.time.Instant;

import com.hareshnarola.enrollment.auth.CurrentUser;
import com.hareshnarola.enrollment.common.BusinessRuleViolationException;
import com.hareshnarola.enrollment.common.NotFoundException;
import com.hareshnarola.enrollment.course.Course;
import com.hareshnarola.enrollment.course.CourseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enrollment use cases. Every write locks the course row first (see
 * {@link CourseRepository#findByIdForUpdate}). Locking always in the order
 * course, then enrollments, means two transactions can never deadlock on each other.
 */
@Service
public class EnrollmentService {

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final WaitlistPromoter waitlist;
    private final EnrollmentEventPublisher events;
    private final Clock clock;

    EnrollmentService(CourseRepository courses, EnrollmentRepository enrollments, WaitlistPromoter waitlist,
                      EnrollmentEventPublisher events, Clock clock) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.waitlist = waitlist;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Confirms the student if a seat is free, otherwise puts them on the waitlist.
     */
    @Transactional
    public EnrollmentResponse enroll(Long courseId, CurrentUser student) {
        Course course = courses.findByIdForUpdate(courseId)
                .orElseThrow(() -> new NotFoundException("Course", courseId));

        if (enrollments.existsByCourseIdAndStudentIdAndStatusIn(courseId, student.id(), EnrollmentStatus.ACTIVE)) {
            throw new BusinessRuleViolationException("You are already enrolled or waitlisted for this course");
        }

        Instant now = clock.instant();
        Enrollment enrollment = Enrollment.request(courseId, student.id(), now);
        if (course.hasFreeSeat()) {
            course.takeSeat(now);
            enrollment.confirm(now);
        } else {
            enrollment.waitlist(now);
        }
        enrollments.save(enrollment);

        if (enrollment.holdsSeat()) {
            events.confirmed(enrollment);
        }
        return EnrollmentResponse.from(enrollment);
    }

    /**
     * Cancels an enrollment. If it held a seat, the seat goes to the next waitlisted student
     * in the same transaction, so there is no window where a seat is free while people wait.
     */
    @Transactional
    public EnrollmentResponse cancel(Long enrollmentId, CurrentUser caller) {
        Long courseId = enrollments.findCourseIdById(enrollmentId)
                .orElseThrow(() -> new NotFoundException("Enrollment", enrollmentId));
        Course course = courses.findByIdForUpdate(courseId)
                .orElseThrow(() -> new NotFoundException("Course", courseId));
        Enrollment enrollment = enrollments.findById(enrollmentId)
                .orElseThrow(() -> new NotFoundException("Enrollment", enrollmentId));

        if (!caller.isAdmin() && !enrollment.belongsTo(caller.id())) {
            throw new AccessDeniedException("Enrollment " + enrollmentId + " belongs to another student");
        }

        Instant now = clock.instant();
        boolean freedSeat = enrollment.holdsSeat();
        enrollment.cancel(now);
        events.cancelled(enrollment);

        if (freedSeat) {
            course.releaseSeat(now);
            waitlist.fillFreeSeats(course);
        }
        return EnrollmentResponse.from(enrollment);
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse get(Long enrollmentId, CurrentUser caller) {
        Enrollment enrollment = enrollments.findById(enrollmentId)
                .orElseThrow(() -> new NotFoundException("Enrollment", enrollmentId));
        if (!caller.isAdmin() && !enrollment.belongsTo(caller.id())) {
            throw new AccessDeniedException("Enrollment " + enrollmentId + " belongs to another student");
        }
        return EnrollmentResponse.from(enrollment);
    }

    @Transactional(readOnly = true)
    public Page<EnrollmentResponse> listForStudent(Long studentId, Pageable pageable) {
        return enrollments.findByStudentId(studentId, pageable).map(EnrollmentResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<EnrollmentResponse> listForCourse(Long courseId, EnrollmentStatus status, Pageable pageable) {
        if (!courses.existsById(courseId)) {
            throw new NotFoundException("Course", courseId);
        }
        Page<Enrollment> page = status == null
                ? enrollments.findByCourseId(courseId, pageable)
                : enrollments.findByCourseIdAndStatus(courseId, status, pageable);
        return page.map(EnrollmentResponse::from);
    }
}
