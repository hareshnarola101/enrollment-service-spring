package com.hareshnarola.enrollment.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
class KafkaTopicConfig {

    @Bean
    NewTopic enrollmentEventsTopic(@Value("${app.kafka.enrollment-events-topic}") String name) {
        // Messages are keyed by course id, so three partitions still keep per-course order.
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
