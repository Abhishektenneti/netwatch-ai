package com.netwatch.api.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a Qdrant gRPC client for similarity search.
 *
 * <p>The API service only reads from Qdrant (similarity queries); collection
 * creation is the processor's responsibility.
 */
@Configuration
public class QdrantConfig {

    private static final Logger log = LoggerFactory.getLogger(QdrantConfig.class);

    private final NetwatchProperties props;
    private QdrantClient client;

    public QdrantConfig(NetwatchProperties props) {
        this.props = props;
    }

    @Bean
    public QdrantClient qdrantClient() {
        var grpc = QdrantGrpcClient.newBuilder(
                props.qdrant().host(),
                props.qdrant().port(),
                false
        ).build();
        client = new QdrantClient(grpc);
        log.info("Qdrant client connected to {}:{}", props.qdrant().host(), props.qdrant().port());
        return client;
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
