package com.netwatch.processor.config;

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
 * Ensures the required Kafka topics exist (idempotent creation) and installs
 * a global {@link DefaultErrorHandler} that routes poison-pill messages to a
 * dead-letter topic after a bounded number of retries.
 *
 * <p>Spring Kafka auto-configuration handles consumer/producer factories based
 * on the {@code spring.kafka.*} properties in application.yml, so we only need
 * explicit beans for topic provisioning and error handling.
 */
@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    private final NetwatchProperties props;

    public KafkaConfig(NetwatchProperties props) {
        this.props = props;
    }

    @Bean
    public NewTopic rawEventsTopic() {
        return TopicBuilder.name(props.kafka().topics().rawEvents())
                .partitions(6)
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

    @Bean
    public NewTopic anomalyEventsTopic() {
        return TopicBuilder.name(props.kafka().topics().anomalyEvents())
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic rawEventsDltTopic() {
        return TopicBuilder.name(props.kafka().topics().rawEvents() + ".DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * After 3 quick retries (500ms apart) a failing record is published to
     * {@code raw-events.DLT} and acknowledged so the consumer can move on.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        // DLT has 1 partition — always write to partition 0.
        var recoverer = new DeadLetterPublishingRecoverer(template,
            (record, ex) -> {
                log.warn("Routing record offset={} partition={} to DLT: {}",
                         record.offset(), record.partition(), ex.getMessage());
                return new org.apache.kafka.common.TopicPartition(
                        record.topic() + ".DLT", 0);
            });
        // 3 retries, 500ms apart
        return new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 3L));
    }
}
