package com.hareshnarola.enrollment.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL and Kafka for integration tests. Because every IT shares the same
 * Spring context configuration, the context (and these containers) is started once
 * and reused across test classes.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));
    }
}
