package com.hareshnarola.enrollment.enrollment;

import java.time.Instant;

import com.hareshnarola.enrollment.common.BusinessRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A student's enrollment in a course. Course and student are referenced by id rather than
 * by JPA association: they are separate aggregates, and nothing here needs to navigate
 * into them, so plain ids avoid lazy-loading surprises.
 */
@Entity
@Table(name = "enrollments")
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false, updatable = false)
    private Long courseId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Enrollment() {
        // for JPA
    }

    private Enrollment(Long courseId, Long studentId, Instant now) {
        this.courseId = courseId;
        this.studentId = studentId;
        this.status = EnrollmentStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Enrollment request(Long courseId, Long studentId, Instant now) {
        return new Enrollment(courseId, studentId, now);
    }

    /** Confirms a new request or promotes a waitlisted one. The caller must have reserved a seat. */
    public void confirm(Instant now) {
        transitionTo(EnrollmentStatus.CONFIRMED, now);
    }

    public void waitlist(Instant now) {
        transitionTo(EnrollmentStatus.WAITLISTED, now);
    }

    public void cancel(Instant now) {
        transitionTo(EnrollmentStatus.CANCELLED, now);
    }

    public boolean holdsSeat() {
        return status == EnrollmentStatus.CONFIRMED;
    }

    public boolean belongsTo(Long userId) {
        return studentId.equals(userId);
    }

    private void transitionTo(EnrollmentStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessRuleViolationException(
                    "Enrollment %s cannot move from %s to %s".formatted(id, status, target));
        }
        this.status = target;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getCourseId() {
        return courseId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
