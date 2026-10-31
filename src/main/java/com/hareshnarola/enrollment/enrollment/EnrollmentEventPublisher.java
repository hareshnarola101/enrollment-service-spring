package com.hareshnarola.enrollment.enrollment;

import java.time.Clock;
import java.util.UUID;

import com.hareshnarola.enrollment.outbox.OutboxWriter;
import com.hareshnarola.enrollment.outbox.OutboxWriter.OutboxMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Turns enrollment state changes into outbox rows. Nothing here talks to Kafka directly;
 * the events only leave the service if the surrounding transaction commits.
 */
@Component
class EnrollmentEventPublisher {

    static final String AGGREGATE_TYPE = "Enrollment";

    private final OutboxWriter outbox;
    private final String topic;
    private final Clock clock;

    EnrollmentEventPublisher(OutboxWriter outbox,
                             @Value("${app.kafka.enrollment-events-topic}") String topic,
                             Clock clock) {
        this.outbox = outbox;
        this.topic = topic;
        this.clock = clock;
    }

    void confirmed(Enrollment enrollment) {
        publish(EnrollmentEventType.CONFIRMED, enrollment);
    }

    void cancelled(Enrollment enrollment) {
        publish(EnrollmentEventType.CANCELLED, enrollment);
    }

    void promotedFromWaitlist(Enrollment enrollment) {
        publish(EnrollmentEventType.PROMOTED_FROM_WAITLIST, enrollment);
    }

    private void publish(EnrollmentEventType type, Enrollment enrollment) {
        UUID eventId = UUID.randomUUID();
        EnrollmentEvent payload = new EnrollmentEvent(eventId, type.wireName(), enrollment.getId(),
                enrollment.getCourseId(), enrollment.getStudentId(), enrollment.getStatus(), clock.instant());
        // Keyed by course: all events for one course land on one partition, in order.
        outbox.append(new OutboxMessage(eventId, AGGREGATE_TYPE, enrollment.getId().toString(),
                type.wireName(), topic, enrollment.getCourseId().toString(), payload));
    }
}
