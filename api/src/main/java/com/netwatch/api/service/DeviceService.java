package com.netwatch.api.service;

import com.netwatch.api.config.NetwatchProperties;
import com.netwatch.api.model.SimilarDevice;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.RecommendPoints;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static io.qdrant.client.PointIdFactory.id;

/**
 * Qdrant-backed similarity search for finding devices with comparable traffic
 * behaviour.
 *
 * <p>Each device has a single point in the {@code device-embeddings} collection
 * keyed by {@code UUID.nameUUIDFromBytes(deviceId.getBytes())}.  The
 * {@link RecommendPoints} API finds nearest neighbours of that point without
 * requiring the caller to provide the raw embedding vector.
 */
@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final QdrantClient qdrant;
    private final String collection;

    public DeviceService(QdrantClient qdrant, NetwatchProperties props) {
        this.qdrant = qdrant;
        this.collection = props.qdrant().collection();
    }

    /**
     * Find up to {@code n} devices with traffic patterns similar to the given
     * device.  Returns an empty list if the device has no embedding yet.
     */
    public List<SimilarDevice> findSimilar(String deviceId, int n) {
        UUID pointId = UUID.nameUUIDFromBytes(deviceId.getBytes());

        RecommendPoints request = RecommendPoints.newBuilder()
                .setCollectionName(collection)
                .addPositive(id(pointId))
                .setLimit(n)
                .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                .build();

        try {
            List<ScoredPoint> results = qdrant.recommendAsync(request).get();
            return results.stream()
                    .filter(p -> {
                        // Exclude the queried device itself from results
                        var payload = p.getPayloadMap();
                        String rid = payload.containsKey("device_id")
                                ? payload.get("device_id").getStringValue()
                                : null;
                        return !deviceId.equals(rid);
                    })
                    .map(p -> {
                        var payload = p.getPayloadMap();
                        return new SimilarDevice(
                            payload.containsKey("device_id")
                                    ? payload.get("device_id").getStringValue() : "unknown",
                            p.getScore(),
                            payload.containsKey("anomaly_score")
                                    ? payload.get("anomaly_score").getDoubleValue() : 0.0,
                            payload.containsKey("event_count")
                                    ? (long) payload.get("event_count").getDoubleValue() : 0L
                        );
                    })
                    .toList();
        } catch (Exception e) {
            // Device has no embedding yet or Qdrant is unreachable.
            log.debug("Similarity search failed for device={}: {}", deviceId, e.getMessage());
            return List.of();
        }
    }
}
