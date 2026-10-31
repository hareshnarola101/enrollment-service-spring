package com.hareshnarola.enrollment.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
class KafkaConsumerConfig {

    /**
     * Retries transient failures (e.g. the database blipping) a few times, then logs and
     * moves on. Malformed messages are not retried: they will never parse. A dead-letter
     * topic would be the next step for anything that exhausts its retries.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(new FixedBackOff(1_000L, 3));
        handler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class);
        return handler;
    }
}
