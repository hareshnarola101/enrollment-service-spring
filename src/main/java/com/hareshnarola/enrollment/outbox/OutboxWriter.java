package com.hareshnarola.enrollment.outbox;

import java.time.Clock;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends events to the outbox. MANDATORY propagation makes it impossible to call this
 * outside the business transaction, which is the whole point of the pattern.
 */
@Component
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxWriter(OutboxEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(OutboxMessage message) {
        repository.save(new OutboxEvent(
                message.eventId(),
                message.aggregateType(),
                message.aggregateId(),
                message.eventType(),
                message.topic(),
                message.key(),
                toJson(message.payload()),
                clock.instant()));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Outbox payload is not serializable", e);
        }
    }

    /**
     * @param key Kafka message key; messages with the same key keep their relative order
     */
    public record OutboxMessage(UUID eventId, String aggregateType, String aggregateId, String eventType,
                                String topic, String key, Object payload) {
    }
}
