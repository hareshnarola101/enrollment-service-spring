package com.hareshnarola.enrollment.outbox;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * SKIP LOCKED lets several app instances run the relay at once: each one claims a
     * different batch instead of blocking on, or double-sending, the same rows.
     */
    @Query(value = """
            select * from outbox_events
            where published_at is null
            order by id
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockNextUnpublished(@Param("limit") int limit);

    List<OutboxEvent> findByAggregateTypeAndAggregateIdOrderById(String aggregateType, String aggregateId);
}
