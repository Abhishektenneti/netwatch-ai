package com.netwatch.processor.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.concurrent.ExecutionException;

/**
 * Initialises the Qdrant gRPC client and ensures the device-embeddings
 * collection exists on startup.
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
        return client;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureCollection() {
        String collection = props.qdrant().collection();
        try {
            var collections = client.listCollectionsAsync().get();
            if (collections.contains(collection)) {
                log.info("Qdrant collection '{}' already exists", collection);
                return;
            }
            client.createCollectionAsync(collection,
                VectorParams.newBuilder()
                    .setSize(props.qdrant().vectorSize())
                    .setDistance(Distance.Cosine)
                    .build()
            ).get();
            log.info("Created Qdrant collection '{}' (dim={}, cosine)",
                     collection, props.qdrant().vectorSize());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while initialising Qdrant collection", e);
        } catch (ExecutionException e) {
            log.warn("Could not initialise Qdrant collection '{}' — "
                     + "will retry on first write: {}", collection, e.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
