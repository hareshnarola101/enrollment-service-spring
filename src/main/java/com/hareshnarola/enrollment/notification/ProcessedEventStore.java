package com.hareshnarola.enrollment.notification;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Remembers which events a consumer has already handled. The insert is a single atomic
 * statement, so two deliveries of the same event racing each other cannot both win.
 */
@Repository
class ProcessedEventStore {

    private final JdbcTemplate jdbc;

    ProcessedEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @return true if this is the first time {@code consumer} sees {@code eventId}
     */
    boolean markProcessed(String consumer, UUID eventId, Instant now) {
        int inserted = jdbc.update("""
                insert into processed_events (consumer, event_id, processed_at)
                values (?, ?, ?)
                on conflict (consumer, event_id) do nothing
                """, consumer, eventId, Timestamp.from(now));
        return inserted == 1;
    }
}
