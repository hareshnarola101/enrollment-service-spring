package com.hareshnarola.enrollment.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hareshnarola.enrollment.notification.Notification;
import com.hareshnarola.enrollment.notification.NotificationRepository;
import com.hareshnarola.enrollment.support.IntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * End to end: REST call -> database + outbox row in one transaction -> relay -> Kafka
 * -> notification consumer.
 */
class OutboxKafkaIT extends IntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaConnectionDetails kafkaConnection;
    @Autowired
    private OutboxEventRepository outbox;
    @Autowired
    private NotificationRepository notifications;
    @Autowired
    private ObjectMapper objectMapper;
    @Value("${app.kafka.enrollment-events-topic}")
    private String topic;

    private KafkaConsumer<String, String> consumer;
    private final List<ConsumerRecord<String, String>> received = new ArrayList<>();

    @BeforeEach
    void subscribe() {
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafkaConnection.getBootstrapServers()),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(List.of(topic));
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    @Test
    void confirmedEnrollmentReachesKafkaWithItsEventId() {
        long courseId = createCourse(5);
        Student student = registerStudent();
        long enrollmentId = enroll(courseId, student).getBody().get("id").asLong();

        ConsumerRecord<String, String> record = awaitRecords(forEnrollment(enrollmentId), 1).getFirst();

        JsonNode payload = json(record);
        assertThat(record.key()).isEqualTo(String.valueOf(courseId));
        assertThat(header(record, OutboxRelay.EVENT_TYPE_HEADER)).isEqualTo("EnrollmentConfirmed");
        assertThat(header(record, OutboxRelay.EVENT_ID_HEADER)).isEqualTo(payload.get("eventId").asText());
        assertThat(payload.get("type").asText()).isEqualTo("EnrollmentConfirmed");
        assertThat(payload.get("studentId").asLong()).isEqualTo(student.id());
        assertThat(payload.get("courseId").asLong()).isEqualTo(courseId);

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(outbox.findByAggregateTypeAndAggregateIdOrderById("Enrollment", String.valueOf(enrollmentId)))
                        .singleElement()
                        .satisfies(row -> assertThat(row.isPublished()).isTrue()));
    }

    @Test
    void cancellationAndPromotionArePublishedInTheOrderTheyHappened() {
        long courseId = createCourse(1);
        Student holder = registerStudent();
        Student waiting = registerStudent();
        long holderEnrollment = enroll(courseId, holder).getBody().get("id").asLong();
        long waitingEnrollment = enroll(courseId, waiting).getBody().get("id").asLong();

        cancel(holderEnrollment, holder.token());

        List<ConsumerRecord<String, String>> forCourse = awaitRecords(
                r -> String.valueOf(courseId).equals(r.key()), 3).stream()
                .sorted(Comparator.comparingLong(ConsumerRecord::offset))
                .toList();
        assertThat(forCourse).extracting(r -> header(r, OutboxRelay.EVENT_TYPE_HEADER))
                .containsExactly("EnrollmentConfirmed", "EnrollmentCancelled", "StudentPromotedFromWaitlist");
        assertThat(forCourse).extracting(r -> json(r).get("enrollmentId").asLong())
                .containsExactly(holderEnrollment, holderEnrollment, waitingEnrollment);
        assertThat(forCourse).extracting(ConsumerRecord::partition).containsOnly(forCourse.getFirst().partition());
    }

    @Test
    void consumerWritesANotificationTheStudentCanRead() {
        long courseId = createCourse(5);
        Student student = registerStudent();
        long enrollmentId = enroll(courseId, student).getBody().get("id").asLong();

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(notifications.findByEnrollmentIdOrderById(enrollmentId))
                        .extracting(Notification::getEventType)
                        .containsExactly("EnrollmentConfirmed"));

        ResponseEntity<JsonNode> mine = call(HttpMethod.GET, "/api/notifications/me", student.token(), null);
        assertThat(mine.getBody().get("totalElements").asLong()).isEqualTo(1);
        assertThat(mine.getBody().get("content").get(0).get("message").asText()).contains("confirmed");
    }

    private List<ConsumerRecord<String, String>> awaitRecords(Predicate<ConsumerRecord<String, String>> filter,
                                                             int count) {
        await().atMost(TIMEOUT).until(() -> {
            consumer.poll(Duration.ofMillis(250)).forEach(received::add);
            return received.stream().filter(filter).count() >= count;
        });
        return received.stream().filter(filter).toList();
    }

    private Predicate<ConsumerRecord<String, String>> forEnrollment(long enrollmentId) {
        return r -> json(r).get("enrollmentId").asLong() == enrollmentId;
    }

    private JsonNode json(ConsumerRecord<String, String> record) {
        try {
            return objectMapper.readTree(record.value());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
