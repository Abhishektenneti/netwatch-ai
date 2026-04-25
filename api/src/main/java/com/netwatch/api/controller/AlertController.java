package com.netwatch.api.controller;

import com.netwatch.api.model.AlertResponse;
import com.netwatch.api.repository.AlertRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for querying anomaly alerts and explanations.
 */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertRepository repository;

    public AlertController(AlertRepository repository) {
        this.repository = repository;
    }

    /**
     * List recent alerts, newest first.
     *
     * @param page  zero-based page index (default 0)
     * @param limit max results per page (default 50, capped at 200)
     */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int limit) {
        int effectiveLimit = Math.min(limit, 200);
        int offset = page * effectiveLimit;
        List<AlertResponse> alerts = repository.findAll(offset, effectiveLimit);
        return Map.of(
            "page", page,
            "limit", effectiveLimit,
            "total", repository.size(),
            "results", alerts
        );
    }

    /**
     * Get a single alert by eventId.  Returns 404 if not found (may have been
     * evicted from the bounded store or not yet processed).
     */
    @GetMapping("/{eventId}")
    public ResponseEntity<AlertResponse> getByEventId(@PathVariable String eventId) {
        return repository.findByEventId(eventId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * All alerts for a specific device, newest first.  Returns an empty list
     * (not 404) when the device has no alerts.
     */
    @GetMapping("/device/{deviceId}")
    public List<AlertResponse> getByDevice(@PathVariable String deviceId) {
        return repository.findByDeviceId(deviceId);
    }
}
