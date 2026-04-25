package com.netwatch.api.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka topic declarations and error handling for the API service.
 *
 * <p>Topic creation is idempotent — the {@code kafka-init} container and the
 * processor also declare the same topics, so whichever starts first wins.
 */
@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    private final NetwatchProperties props;

    public KafkaConfig(NetwatchProperties props) {
        this.props = props;
    }

    @Bean
    public NewTopic anomalyEventsTopic() {
        return TopicBuilder.name(props.kafka().topics().anomalyEvents())
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic explainedEventsTopic() {
        return TopicBuilder.name(props.kafka().topics().explainedEvents())
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic processedEventsTopic() {
        return TopicBuilder.name(props.kafka().topics().processedEvents())
                .partitions(6)
                .replicas(1)
                .build();
    }

    /** After 2 retries (500ms apart) route the record to a .DLT topic and move on. */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
            (record, ex) -> {
                log.warn("DLT: offset={} partition={} reason={}",
                         record.offset(), record.partition(), ex.getMessage());
                return new org.apache.kafka.common.TopicPartition(
                        record.topic() + ".DLT", 0);
            });
        return new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 2L));
    }
}
