package com.hareshnarola.enrollment.enrollment;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka payload for enrollment events. Kept flat and self-contained so consumers do not
 * need to call back into this service to make sense of a message.
 */
public record EnrollmentEvent(
        UUID eventId,
        String type,
        Long enrollmentId,
        Long courseId,
        Long studentId,
        EnrollmentStatus status,
        Instant occurredAt) {
}
