package com.stationly.backend.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Rate limiter to enforce TfL API limits (300 requests per minute).
 * This ensures a minimum gap of ~200ms between requests.
 */
@Component
@Slf4j
public class TflRateLimiter {

    // 300 requests per minute = 5 requests per second = 1 request every 200ms.
    // We add a small buffer to be safe, so 210ms.
    private static final long MIN_REQUEST_INTERVAL_MS = 210;

    private long nextAvailableTime = System.currentTimeMillis();

    /**
     * Blocks until a request permit is available.
     * Thread-safe.
     */
    public synchronized void acquire() {
        long now = System.currentTimeMillis();
        if (now < nextAvailableTime) {
            long waitTime = nextAvailableTime - now;
            try {
                TimeUnit.MILLISECONDS.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("⚠️ Rate limiter interrupted during wait", e);
            }
            // Advance the next available time relative to the PLANNED time to keep cadence
            nextAvailableTime += MIN_REQUEST_INTERVAL_MS;
        } else {
            // First request or after a long break
            nextAvailableTime = now + MIN_REQUEST_INTERVAL_MS;
        }
    }
}
