package com.hareshnarola.enrollment.notification;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hareshnarola.enrollment.outbox.OutboxRelay;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes enrollment events and writes them to the notification log. Kafka may deliver
 * a message more than once (relay retries, consumer rebalances), so handling is idempotent.
 */
@Component
class NotificationListener {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    NotificationListener(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${app.kafka.enrollment-events-topic}", groupId = NotificationService.CONSUMER_NAME)
    void onEnrollmentEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        UUID eventId = eventId(record);
        EnrollmentNotificationMessage message =
                objectMapper.readValue(record.value(), EnrollmentNotificationMessage.class);
        notificationService.handle(eventId, message);
    }

    private static UUID eventId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(OutboxRelay.EVENT_ID_HEADER);
        if (header == null) {
            throw new IllegalArgumentException("Message at offset " + record.offset() + " has no eventId header");
        }
        return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
    }
}
