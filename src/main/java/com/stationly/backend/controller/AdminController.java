package com.stationly.backend.controller;

import com.stationly.backend.model.RefreshSummary;
import com.stationly.backend.model.LineStatusResponse;
import com.stationly.backend.model.TransportMode;
import com.stationly.backend.model.LineInfo;
import com.stationly.backend.model.Station;
import com.stationly.backend.model.LineRouteResponse;
import com.stationly.backend.repository.DataRepository;
import com.stationly.backend.service.LineService;
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

    private final DataRepository<TransportMode, String> modeRepository;
    private final DataRepository<LineInfo, String> lineRepository;
    private final DataRepository<Station, String> stationRepository;
    private final DataRepository<LineRouteResponse, String> routeRepository;
    private final DataRepository<LineStatusResponse, String> lineStatusRepository;
    private final TflPollingService tflPollingService;
    private final LineService lineService;

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
        List<LineStatusResponse> statuses = lineService.syncLineStatuses();
        return ResponseEntity.ok(statuses);
    }

    @Operation(summary = "System Cleanup", description = "Clears all cached data from Firestore to reset state.")
    @ApiResponse(responseCode = "200", description = "Cleanup completed")
    @GetMapping("/cleanup")
    public ResponseEntity<String> cleanup() {
        log.info("🔥 ADMIN: Cleanup requested. Clearing Firestore data cache...");

        // Perform flush of all Firestore collections
        modeRepository.deleteAll();
        lineRepository.deleteAll();
        stationRepository.deleteAll();
        routeRepository.deleteAll();
        lineStatusRepository.deleteAll();

        return ResponseEntity.ok("Cleanup completed successfully. All Firestore data flushed.");
    }
}
