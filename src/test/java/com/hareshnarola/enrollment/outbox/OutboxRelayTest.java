package com.hareshnarola.enrollment.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Mock
    private OutboxEventRepository repository;
    @Mock
    private KafkaTemplate<String, String> kafka;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = relayWithBatchSize(10);
    }

    @Test
    void publishesPendingEventsAndMarksThemPublished() {
        OutboxEvent first = event("EnrollmentConfirmed");
        OutboxEvent second = event("EnrollmentCancelled");
        when(repository.lockNextUnpublished(10)).thenReturn(List.of(first, second));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(acked());

        int published = relay.relayPending();

        assertThat(published).isEqualTo(2);
        assertThat(first.getPublishedAt()).isEqualTo(NOW);
        assertThat(second.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void sendsKeyPayloadAndHeaders() {
        OutboxEvent event = event("EnrollmentConfirmed");
        when(repository.lockNextUnpublished(10)).thenReturn(List.of(event));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(acked());

        relay.relayPending();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();
        assertThat(record.topic()).isEqualTo("enrollment.events");
        assertThat(record.key()).isEqualTo("course-7");
        assertThat(record.value()).isEqualTo("{\"a\":1}");
        assertThat(header(record, OutboxRelay.EVENT_ID_HEADER)).isEqualTo(event.getEventId().toString());
        assertThat(header(record, OutboxRelay.EVENT_TYPE_HEADER)).isEqualTo("EnrollmentConfirmed");
    }

    @Test
    void stopsAtTheFirstFailureSoOrderIsPreserved() {
        OutboxEvent first = event("A");
        OutboxEvent second = event("B");
        OutboxEvent third = event("C");
        when(repository.lockNextUnpublished(10)).thenReturn(List.of(first, second, third));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(acked())
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        int published = relay.relayPending();

        assertThat(published).isEqualTo(1);
        assertThat(first.isPublished()).isTrue();
        assertThat(second.isPublished()).isFalse();
        assertThat(third.isPublished()).isFalse();
        verify(kafka, times(2)).send(any(ProducerRecord.class));
    }

    @Test
    void keepsGoingUntilTheOutboxIsDrained() {
        relay = relayWithBatchSize(2);
        when(repository.lockNextUnpublished(2))
                .thenReturn(List.of(event("A"), event("B")))
                .thenReturn(List.of(event("C")));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(acked());

        int published = relay.relayPending();

        assertThat(published).isEqualTo(3);
        verify(repository, times(2)).lockNextUnpublished(2);
    }

    @Test
    void anEmptyOutboxIsANoOp() {
        when(repository.lockNextUnpublished(10)).thenReturn(List.of());

        assertThat(relay.relayPending()).isZero();
    }

    private OutboxRelay relayWithBatchSize(int batchSize) {
        // A mocked transaction manager is enough: TransactionTemplate just calls begin/commit on it.
        TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
        OutboxProperties properties = new OutboxProperties(batchSize, Duration.ofMillis(500), Duration.ofSeconds(1));
        return new OutboxRelay(repository, kafka, tx, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OutboxEvent event(String type) {
        return new OutboxEvent(UUID.randomUUID(), "Enrollment", "1", type, "enrollment.events",
                "course-7", "{\"a\":1}", NOW);
    }

    private static CompletableFuture<SendResult<String, String>> acked() {
        return CompletableFuture.completedFuture(null);
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
