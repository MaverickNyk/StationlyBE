package com.mindthetime.backend.controller;

import com.mindthetime.backend.model.RefreshSummary;
import com.mindthetime.backend.service.TflPollingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/refresh")
@RequiredArgsConstructor
@Slf4j
public class RefreshController {

    private final TflPollingService tflPollingService;

    /**
     * Manually trigger a refresh for all configured transport modes
     * 
     * @return List of summaries for the refresh operations
     */
    @GetMapping
    public ResponseEntity<List<RefreshSummary>> refreshConfiguredModes() {
        log.info("🔄 Manual refresh triggered for all configured modes");

        List<RefreshSummary> summaries = tflPollingService.refreshAll();

        return ResponseEntity.ok(summaries);
    }
}
