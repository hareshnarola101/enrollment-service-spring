package com.hareshnarola.enrollment.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Polls the outbox and publishes pending rows to Kafka in insertion order.
 *
 * <p>Delivery is at-least-once: if the process dies after Kafka acknowledged a message
 * but before the "published" mark commits, that message is sent again on the next run.
 * Consumers deduplicate on the {@code eventId} header.
 */
@Component
public class OutboxRelay {

    public static final String EVENT_ID_HEADER = "eventId";
    public static final String EVENT_TYPE_HEADER = "eventType";

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transactionTemplate;
    private final OutboxProperties properties;
    private final Clock clock;

    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafka,
                       TransactionTemplate transactionTemplate, OutboxProperties properties, Clock clock) {
        this.repository = repository;
        this.kafka = kafka;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval}")
    void scheduledRun() {
        relayPending();
    }

    /**
     * Publishes batches until the outbox is drained or a send fails.
     *
     * @return number of events published in this run
     */
    public int relayPending() {
        int total = 0;
        while (true) {
            BatchResult result = transactionTemplate.execute(status -> publishNextBatch());
            if (result == null) {
                return total;
            }
            total += result.published();
            if (result.stopped() || result.claimed() < properties.batchSize()) {
                return total;
            }
        }
    }

    private BatchResult publishNextBatch() {
        List<OutboxEvent> batch = repository.lockNextUnpublished(properties.batchSize());
        int published = 0;
        for (OutboxEvent event : batch) {
            if (!send(event)) {
                // Stop at the first failure so later events never overtake an earlier one.
                // Whatever was sent so far is still marked published when this transaction commits.
                return new BatchResult(batch.size(), published, true);
            }
            event.markPublished(clock.instant());
            published++;
        }
        return new BatchResult(batch.size(), published, false);
    }

    private boolean send(OutboxEvent event) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());
        record.headers()
                .add(EVENT_ID_HEADER, event.getEventId().toString().getBytes(StandardCharsets.UTF_8))
                .add(EVENT_TYPE_HEADER, event.getEventType().getBytes(StandardCharsets.UTF_8));
        try {
            kafka.send(record).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            log.warn("Could not publish outbox event {} ({}); will retry on the next run",
                    event.getEventId(), event.getEventType(), e);
            return false;
        }
    }

    private record BatchResult(int claimed, int published, boolean stopped) {
    }
}
