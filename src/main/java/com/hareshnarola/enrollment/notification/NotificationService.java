package com.hareshnarola.enrollment.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    static final String CONSUMER_NAME = "notification-log";

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ProcessedEventStore processedEvents;
    private final NotificationRepository notifications;
    private final Clock clock;

    NotificationService(ProcessedEventStore processedEvents, NotificationRepository notifications, Clock clock) {
        this.processedEvents = processedEvents;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Records a notification for the event unless it was handled before. The dedup marker
     * and the notification commit together, so a crash between them cannot lose either.
     *
     * @return false if the event was a duplicate delivery and was skipped
     */
    @Transactional
    public boolean handle(UUID eventId, EnrollmentNotificationMessage message) {
        Instant now = clock.instant();
        if (!processedEvents.markProcessed(CONSUMER_NAME, eventId, now)) {
            log.info("Skipping duplicate event {}", eventId);
            return false;
        }
        notifications.save(new Notification(eventId, message.studentId(), message.courseId(),
                message.enrollmentId(), message.type(), describe(message), now));
        return true;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listForStudent(Long studentId, Pageable pageable) {
        return notifications.findByStudentId(studentId, pageable).map(NotificationResponse::from);
    }

    private static String describe(EnrollmentNotificationMessage message) {
        return switch (message.type()) {
            case "EnrollmentConfirmed" -> "Your seat in course %d is confirmed.".formatted(message.courseId());
            case "EnrollmentCancelled" -> "Your enrollment in course %d was cancelled.".formatted(message.courseId());
            case "StudentPromotedFromWaitlist" ->
                    "A seat opened up in course %d and you have moved off the waitlist.".formatted(message.courseId());
            default -> "Enrollment %d changed (%s).".formatted(message.enrollmentId(), message.type());
        };
    }
}
