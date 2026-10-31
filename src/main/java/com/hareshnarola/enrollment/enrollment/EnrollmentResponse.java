package com.hareshnarola.enrollment.enrollment;

import java.time.Instant;

public record EnrollmentResponse(
        Long id,
        Long courseId,
        Long studentId,
        EnrollmentStatus status,
        Instant createdAt,
        Instant updatedAt) {

    static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(enrollment.getId(), enrollment.getCourseId(), enrollment.getStudentId(),
                enrollment.getStatus(), enrollment.getCreatedAt(), enrollment.getUpdatedAt());
    }
}
