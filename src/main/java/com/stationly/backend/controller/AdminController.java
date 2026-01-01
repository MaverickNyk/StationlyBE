package com.stationly.backend.controller;

import com.stationly.backend.model.RefreshSummary;
import com.stationly.backend.model.LineStatusResponse;
import com.stationly.backend.service.FirebaseCacheService;
import com.stationly.backend.service.LineStatusService;
import com.stationly.backend.service.TflPollingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin", description = "Administrative operations for manual data refreshing and cleanup")
public class AdminController {

    private final FirebaseCacheService cacheService;
    private final TflPollingService tflPollingService;
    private final LineStatusService lineStatusService;

    @Operation(summary = "Trigger Manual Refresh", description = "Manually triggers a data refresh for all configured transport modes from TFL API.")
    @ApiResponse(responseCode = "200", description = "Refresh completed successfully")
    @GetMapping("/refresh")
    public ResponseEntity<List<RefreshSummary>> refresh() {
        log.info("🔄 ADMIN: Manual refresh triggerred for all configured modes");
        List<RefreshSummary> summaries = tflPollingService.refreshAll();
        return ResponseEntity.ok(summaries);
    }

    @Operation(summary = "Trigger Line Status Refresh", description = "Manually triggers a refresh of line statuses from TFL API.")
    @ApiResponse(responseCode = "200", description = "Line statuses refreshed successfully")
    @GetMapping("/status/refresh")
    public ResponseEntity<List<LineStatusResponse>> refreshLineStatuses() {
        log.info("🔄 ADMIN: Manual line status refresh triggered");
        List<LineStatusResponse> statuses = lineStatusService.pollLineStatuses();
        return ResponseEntity.ok(statuses);
    }

    @Operation(summary = "System Cleanup", description = "Clears all metadata from Firebase and sends a generalized 'CLEAR' signal (if applicable) to reset client state.")
    @ApiResponse(responseCode = "200", description = "Cleanup completed")
    @GetMapping("/cleanup")
    public ResponseEntity<String> cleanup() {
        log.info("🔥 ADMIN: Cleanup requested. Clearing Firebase metadata cache...");

        // Perform flush of Firebase metadata cache
        cacheService.flushAll();

        return ResponseEntity.ok("Cleanup completed successfully. Firebase metadata cache flushed.");
    }
}
