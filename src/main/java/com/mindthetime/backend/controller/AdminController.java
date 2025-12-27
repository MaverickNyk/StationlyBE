package com.mindthetime.backend.controller;

import com.mindthetime.backend.model.RefreshSummary;
import com.mindthetime.backend.service.FcmService;
import com.mindthetime.backend.service.RedisService;
import com.mindthetime.backend.service.TflPollingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final RedisService redisService;
    private final FcmService fcmService;
    private final TflPollingService tflPollingService;

    @GetMapping("/refresh")
    public ResponseEntity<List<RefreshSummary>> refresh() {
        log.info("🔄 ADMIN: Manual refresh triggered for all configured modes");
        List<RefreshSummary> summaries = tflPollingService.refreshAll();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/cleanup")
    public ResponseEntity<String> cleanup() {
        log.info("🔥 ADMIN: Cleanup requested. Clearing Redis and signaling FCM...");

        // 1. Find all active topics in Redis (both new and legacy)
        Set<String> keys = redisService.getKeys("*");
        if (keys != null && !keys.isEmpty()) {
            log.info("Found {} keys in Redis to signal...", keys.size());

            // Send CLEAR signal to relevant topics in parallel
            keys.parallelStream().forEach(key -> {
                // Topic name from Redis key
                // New format: Station_940GZZLUMGT -> Station_940GZZLUMGT
                // Legacy format: 940GZZLUMGT-Northern-Inbound -> 940GZZLUMGT-Northern-Inbound
                // (usually topic is normalized)
                if (key.startsWith("Station_") || (key.contains("-") && !key.contains(" "))) {
                    fcmService.sendClearSignal(key);
                }
            });
        }

        // 2. Perform nuclear flush of Redis
        redisService.flushAll();

        return ResponseEntity.ok("Cleanup completed successfully. Redis flushed and FCM signals sent.");
    }
}
