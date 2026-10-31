package com.hareshnarola.enrollment.notification;

import java.time.Instant;

public record NotificationResponse(Long id, String eventType, Long courseId, Long enrollmentId,
                                   String message, Instant createdAt) {

    static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getEventType(), n.getCourseId(), n.getEnrollmentId(),
                n.getMessage(), n.getCreatedAt());
    }
}
