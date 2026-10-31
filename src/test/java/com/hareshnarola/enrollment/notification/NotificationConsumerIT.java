package com.hareshnarola.enrollment.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.hareshnarola.enrollment.outbox.OutboxRelay;
import com.hareshnarola.enrollment.support.IntegrationTest;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Talks to the consumer directly through Kafka to simulate what the relay cannot rule out:
 * the same event delivered twice, and a message that will never parse.
 *
 * <p>Each test finishes by sending a "marker" event with the same key. Same key means same
 * partition, and a partition is consumed in order, so once the marker is processed every
 * message sent before it has been handled too. That avoids sleeping and hoping.
 */
class NotificationConsumerIT extends IntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafka;
    @Autowired
    private NotificationRepository notifications;
    @Value("${app.kafka.enrollment-events-topic}")
    private String topic;

    @Test
    void duplicateDeliveryProducesOneNotification() throws Exception {
        String key = randomKey();
        UUID eventId = UUID.randomUUID();

        send(key, eventId, payload("EnrollmentConfirmed"));
        send(key, eventId, payload("EnrollmentConfirmed"));
        UUID marker = sendMarker(key);

        await().atMost(TIMEOUT).until(() -> !notifications.findByEventId(marker).isEmpty());
        assertThat(notifications.findByEventId(eventId)).hasSize(1);
    }

    @Test
    void malformedMessageIsSkippedAndDoesNotBlockThePartition() throws Exception {
        String key = randomKey();
        kafka.send(new ProducerRecord<>(topic, key, "this is not json")).get();
        send(key, UUID.randomUUID(), "{\"type\": ");
        UUID marker = sendMarker(key);

        await().atMost(TIMEOUT).until(() -> !notifications.findByEventId(marker).isEmpty());
    }

    private UUID sendMarker(String key) throws Exception {
        UUID marker = UUID.randomUUID();
        send(key, marker, payload("EnrollmentCancelled"));
        return marker;
    }

    private void send(String key, UUID eventId, String payload) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        record.headers().add(OutboxRelay.EVENT_ID_HEADER, eventId.toString().getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get();
    }

    private static String payload(String type) {
        long id = ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000);
        return """
                {"type": "%s", "enrollmentId": %d, "courseId": %d, "studentId": %d}
                """.formatted(type, id, id, id);
    }

    private static String randomKey() {
        return "test-" + UUID.randomUUID();
    }
}
