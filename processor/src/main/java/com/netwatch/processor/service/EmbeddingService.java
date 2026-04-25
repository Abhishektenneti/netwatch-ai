package com.netwatch.processor.service;

import com.netwatch.processor.config.NetwatchProperties;
import com.netwatch.processor.model.ProcessedEvent;
import com.netwatch.processor.model.RollingStats;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.PointStruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

/**
 * Computes a device-behaviour embedding from rolling statistics and upserts
 * it into Qdrant so the API can later query for devices with similar traffic
 * patterns.
 *
 * <p>The embedding is an 8-dimensional vector built from the rolling stats
 * plus instantaneous event features.  This is intentionally simple — a
 * production system would use a learned encoder.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final QdrantClient qdrant;
    private final String collection;

    public EmbeddingService(QdrantClient qdrant, NetwatchProperties props) {
        this.qdrant = qdrant;
        this.collection = props.qdrant().collection();
    }

    /**
     * Compute a behaviour embedding for the device in this event and upsert
     * it into Qdrant.  Failures are logged but do not propagate — embedding
     * storage is best-effort so it never blocks the main pipeline.
     */
    public void upsert(ProcessedEvent event) {
        try {
            float[] vector = computeEmbedding(event);
            UUID pointId = UUID.nameUUIDFromBytes(event.deviceId().getBytes());

            PointStruct point = PointStruct.newBuilder()
                    .setId(id(pointId))
                    .setVectors(vectors(vector))
                    .putAllPayload(Map.of(
                        "device_id", value(event.deviceId()),
                        "anomaly_score", value(event.anomalyScore()),
                        "event_count", value(event.rollingStats().eventCount()),
                        "last_event_id", value(event.eventId())
                    ))
                    .build();

            qdrant.upsertAsync(collection, List.of(point));
            // fire-and-forget; errors are logged by the gRPC layer
        } catch (Exception e) {
            log.warn("Failed to upsert embedding for device={}: {}",
                     event.deviceId(), e.getMessage());
        }
    }

    // ---- internal ----

    /**
     * Build an 8-dim vector from rolling stats + event-level anomaly score.
     *
     * <p>Dimensions:
     * <ol>
     *   <li>mean bytes sent (log-scaled)</li>
     *   <li>std bytes sent (log-scaled)</li>
     *   <li>mean bytes received (log-scaled)</li>
     *   <li>std bytes received (log-scaled)</li>
     *   <li>mean duration ms (log-scaled)</li>
     *   <li>event count (log-scaled)</li>
     *   <li>anomaly score</li>
     *   <li>protocol one-hot bucket (hashed)</li>
     * </ol>
     */
    static float[] computeEmbedding(ProcessedEvent event) {
        RollingStats s = event.rollingStats();
        return new float[]{
            logScale(s.meanBytesSent()),
            logScale(s.stdBytesSent()),
            logScale(s.meanBytesReceived()),
            logScale(s.stdBytesReceived()),
            logScale(s.meanDurationMs()),
            logScale(s.eventCount()),
            (float) event.anomalyScore(),
            protocolBucket(event.protocol())
        };
    }

    private static float logScale(double v) {
        return (float) Math.log1p(Math.abs(v));
    }

    /** Map the protocol to a rough [0,1] bucket via simple hashing. */
    private static float protocolBucket(String protocol) {
        if (protocol == null) return 0.5f;
        return (protocol.hashCode() & 0x7FFF_FFFF) % 100 / 100f;
    }
}
