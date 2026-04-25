package com.netwatch.api.repository;

import com.netwatch.api.model.AlertResponse;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory bounded store for anomaly alerts.
 *
 * <p>Maintains three indexes for O(1) or O(k) lookups:
 * <ul>
 *   <li>{@code byEventId} — exact match on eventId</li>
 *   <li>{@code byDeviceId} — all alerts for a device</li>
 *   <li>{@code ordered} — insertion-ordered deque for paginated list queries</li>
 * </ul>
 *
 * <p>When the store reaches {@code MAX_SIZE} the oldest entry is evicted.
 * All writes are synchronised; reads are lock-free where possible.
 */
@Repository
public class AlertRepository {

    private static final int MAX_SIZE = 10_000;

    /** eventId → alert (mutable via replace on explanation update) */
    private final Map<String, AlertResponse> byEventId = new ConcurrentHashMap<>();

    /** deviceId → ordered list of eventIds (newest first within each device) */
    private final Map<String, CopyOnWriteArrayList<String>> byDeviceId = new ConcurrentHashMap<>();

    /** Global insertion order (newest at front) — used for paginated list */
    private final Deque<String> ordered = new ConcurrentLinkedDeque<>();

    public synchronized void save(AlertResponse alert) {
        String eventId = alert.eventId();
        if (byEventId.containsKey(eventId)) {
            // Idempotent re-save (e.g., duplicate Kafka delivery)
            return;
        }

        byEventId.put(eventId, alert);
        byDeviceId.computeIfAbsent(alert.deviceId(), k -> new CopyOnWriteArrayList<>())
                  .add(0, eventId);

        ordered.addFirst(eventId);

        // Evict oldest when over capacity
        if (ordered.size() > MAX_SIZE) {
            String evicted = ordered.pollLast();
            if (evicted != null) {
                AlertResponse evictedAlert = byEventId.remove(evicted);
                if (evictedAlert != null) {
                    CopyOnWriteArrayList<String> ids = byDeviceId.get(evictedAlert.deviceId());
                    if (ids != null) ids.remove(evicted);
                }
            }
        }
    }

    /**
     * Attach an explanation to an existing alert.  No-op if the eventId is
     * not found (the alert may have been evicted or not yet arrived).
     */
    public void updateExplanation(String eventId, String explanation) {
        byEventId.computeIfPresent(eventId, (k, existing) ->
            new AlertResponse(
                existing.eventId(), existing.timestamp(), existing.deviceId(),
                existing.srcIp(), existing.dstIp(), existing.protocol(),
                existing.anomalyScore(), explanation
            )
        );
    }

    /** Return up to {@code limit} alerts starting at zero-based {@code offset}, newest first. */
    public List<AlertResponse> findAll(int offset, int limit) {
        List<String> ids = new ArrayList<>(ordered);
        return ids.stream()
                .skip(offset)
                .limit(limit)
                .map(byEventId::get)
                .filter(a -> a != null)
                .toList();
    }

    public Optional<AlertResponse> findByEventId(String eventId) {
        return Optional.ofNullable(byEventId.get(eventId));
    }

    /** All alerts for a device, newest first. */
    public List<AlertResponse> findByDeviceId(String deviceId) {
        var ids = byDeviceId.getOrDefault(deviceId, new CopyOnWriteArrayList<>());
        return ids.stream()
                .map(byEventId::get)
                .filter(a -> a != null)
                .toList();
    }

    public long countByDeviceId(String deviceId) {
        var ids = byDeviceId.get(deviceId);
        return ids == null ? 0 : ids.size();
    }

    public int size() {
        return byEventId.size();
    }
}
