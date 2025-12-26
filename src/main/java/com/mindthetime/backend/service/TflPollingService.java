package com.mindthetime.backend.service;

import com.mindthetime.backend.client.TflApiClient;
import com.mindthetime.backend.model.ArrivalPrediction;
import com.mindthetime.backend.model.DirectionPredictions;
import com.mindthetime.backend.model.RefreshSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TflPollingService {

    private final TflApiClient tflApiClient;
    private final DataTransformationService transformationService;
    private final RedisService redisService;
    private final FcmService fcmService;

    @Value("${tfl.transport.modes}")
    private String tflTransportModes;

    @Value("${spring.cache.redis.time-to-live}")
    private long redisTtl;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Poll TfL API every 60 seconds and update Redis + FCM
     * Polls each configured mode separately
     */
    @Scheduled(fixedRateString = "${tfl.polling.interval:60000}")
    public void pollAndUpdate() {
        refreshAll();
    }

    /**
     * Refresh all configured transport modes
     * 
     * @return List of summaries for each mode
     */
    public List<RefreshSummary> refreshAll() {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        long startMillis = System.currentTimeMillis();
        List<RefreshSummary> summaries = new ArrayList<>();

        log.info("═══════════════════════════════════════════════════════════════════");
        log.info("🚇 TFL REFRESH STARTED | Modes: {} | Time: {}", tflTransportModes.toUpperCase(), timestamp);
        log.info("═══════════════════════════════════════════════════════════════════");

        String[] modes = tflTransportModes.split(",");
        for (String mode : modes) {
            String trimmedMode = mode.trim();
            if (!trimmedMode.isEmpty()) {
                summaries.add(refreshMode(trimmedMode));
            }
        }

        long totalDuration = System.currentTimeMillis() - startMillis;
        log.info("═══════════════════════════════════════════════════════════════════");
        log.info("🚇 TFL REFRESH ENDED | Total Time: {}ms", totalDuration);
        log.info("═══════════════════════════════════════════════════════════════════");
        return summaries;
    }

    /**
     * Manually refresh data for a specific mode
     * 
     * @param mode Transport mode (tube, dlr, bus, etc.)
     * @return Summary of the refresh operation
     */
    public RefreshSummary refreshMode(String mode) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        LocalDateTime startTime = LocalDateTime.now();
        long startMillis = System.currentTimeMillis();

        log.info("───────────────────────────────────────────────────────────────────");
        log.info("🚇 POLLING MODE: {} | Time: {}", mode.toUpperCase(), timestamp);
        log.info("───────────────────────────────────────────────────────────────────");

        try {
            // Fetch arrivals from TfL API
            log.info("📡 Fetching arrivals from TfL API for mode: {}", mode);
            List<ArrivalPrediction> arrivals = tflApiClient.getArrivalsByMode(mode);

            if (arrivals == null || arrivals.isEmpty()) {
                long duration = System.currentTimeMillis() - startMillis;
                log.warn("⚠️  STATUS: NO DATA | No arrivals received from TfL API for mode: {} | Took: {}ms", mode,
                        duration);

                return RefreshSummary.builder()
                        .mode(mode)
                        .timestamp(startTime)
                        .status("NO_DATA")
                        .arrivalsReceived(0)
                        .cacheKeysCreated(0)
                        .fcmTopicsPublished(0)
                        .ttlSeconds(0L)
                        .processingTimeMs(duration)
                        .message("No arrivals received from TfL API for mode: " + mode)
                        .build();
            }

            log.info("✅ STATUS: SUCCESS | Received {} arrivals from TfL API", arrivals.size());

            // Transform into grouped predictions
            log.info("🔄 Transforming data into grouped predictions...");
            Map<String, DirectionPredictions> groupedPredictions = transformationService
                    .transformToGroupedPredictions(arrivals);

            // Store in Redis and publish to FCM in parallel
            log.info("⚡ Parallel processing started: Storing in Redis and publishing to FCM ({} topics)...",
                    groupedPredictions.size());

            // 1. Redis Task
            CompletableFuture<Void> redisTask = CompletableFuture.runAsync(() -> {
                Map<String, Object> redisData = new HashMap<>(groupedPredictions);
                redisService.saveAll(redisData, redisTtl);
            });

            // 2. FCM Task (parallelizing individual sends if many)
            CompletableFuture<Integer> fcmTask = CompletableFuture.supplyAsync(() -> {
                // Use parallelStream to send FCM messages in parallel
                return (int) groupedPredictions.entrySet().parallelStream()
                        .map(entry -> {
                            fcmService.publishToTopic(entry.getKey(), entry.getValue());
                            return 1;
                        })
                        .count();
            });

            // Wait for both to complete
            CompletableFuture.allOf(redisTask, fcmTask).join();
            int fcmCount = fcmTask.join();

            long duration = System.currentTimeMillis() - startMillis;
            log.info("✅ SUMMARY: Mode={} | {} arrivals → {} cache keys → {} FCM topics | TTL: {}ms | Took: {}ms",
                    mode, arrivals.size(), groupedPredictions.size(), fcmCount, redisTtl, duration);

            return RefreshSummary.builder()
                    .mode(mode)
                    .timestamp(startTime)
                    .status("SUCCESS")
                    .arrivalsReceived(arrivals.size())
                    .cacheKeysCreated(groupedPredictions.size())
                    .fcmTopicsPublished(fcmCount)
                    .ttlSeconds(redisTtl)
                    .processingTimeMs(duration)
                    .message(String.format("Successfully processed %d arrivals into %d cache keys (Parallel)",
                            arrivals.size(), groupedPredictions.size()))
                    .build();

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startMillis;
            log.error("❌ STATUS: FAILED | Error during TfL polling for mode: {} | Took: {}ms", mode, duration, e);

            return RefreshSummary.builder()
                    .mode(mode)
                    .timestamp(startTime)
                    .status("FAILED")
                    .arrivalsReceived(0)
                    .cacheKeysCreated(0)
                    .fcmTopicsPublished(0)
                    .ttlSeconds(0L)
                    .processingTimeMs(duration)
                    .message("Error during polling: " + e.getMessage())
                    .build();
        }
    }
}
