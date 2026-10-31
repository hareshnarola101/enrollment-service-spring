package com.hareshnarola.enrollment.outbox;

import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param batchSize    rows claimed per relay transaction
 * @param pollInterval delay between relay runs
 * @param sendTimeout  how long to wait for a Kafka acknowledgement before giving up on a run
 */
@Validated
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        @Min(1) @Max(1000) int batchSize,
        @NotNull Duration pollInterval,
        @NotNull Duration sendTimeout) {
}
