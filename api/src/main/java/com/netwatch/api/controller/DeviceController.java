package com.netwatch.api.controller;

import com.netwatch.api.model.DeviceStats;
import com.netwatch.api.model.SimilarDevice;
import com.netwatch.api.service.AlertService;
import com.netwatch.api.service.DeviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for device statistics and similarity search.
 */
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final AlertService alertService;
    private final DeviceService deviceService;

    public DeviceController(AlertService alertService, DeviceService deviceService) {
        this.alertService = alertService;
        this.deviceService = deviceService;
    }

    /**
     * Return traffic and anomaly statistics for a device.  Returns 404 when
     * the device hasn't appeared in the event stream yet.
     */
    @GetMapping("/{deviceId}/stats")
    public ResponseEntity<DeviceStats> stats(@PathVariable String deviceId) {
        if (!alertService.deviceKnown(deviceId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(alertService.deviceStats(deviceId));
    }

    /**
     * Find devices with similar traffic behaviour using Qdrant vector search.
     *
     * @param n max number of similar devices to return (default 5, capped at 20)
     */
    @GetMapping("/{deviceId}/similar")
    public List<SimilarDevice> similar(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "5") int n) {
        int limit = Math.min(n, 20);
        return deviceService.findSimilar(deviceId, limit);
    }
}
