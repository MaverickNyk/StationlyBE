package com.mindthetime.backend.controller;

import com.mindthetime.backend.model.ErrorResponse;
import com.mindthetime.backend.model.Station;
import com.mindthetime.backend.service.CacheRetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/predictions")
@RequiredArgsConstructor
public class ArrivalsController {

    private final CacheRetrievalService cacheRetrievalService;

    /**
     * Get predictions from Redis cache
     * 
     * @param station   Required. Comma-separated station IDs (e.g.,
     *                  "940GZZLUKSX,940GZZLUOXC")
     * @param mode      Required. Comma-separated mode/line IDs (e.g.,
     *                  "central,northern")
     * @param direction Required. Comma-separated directions (e.g.,
     *                  "inbound,outbound")
     * @return List of Station objects with nested line and direction data
     */
    @GetMapping
    public ResponseEntity<?> getPredictions(
            @RequestParam(required = true) String station,
            @RequestParam(required = true) String mode,
            @RequestParam(required = true) String direction) {

        // Retrieve from cache and structure as Station objects
        List<Station> stations = cacheRetrievalService.getStations(station, mode, direction);

        // If no data found in cache, return error
        if (stations.isEmpty()) {
            ErrorResponse error = ErrorResponse.builder()
                    .timestamp(java.time.LocalDateTime.now())
                    .status(HttpStatus.NOT_FOUND.value())
                    .error("Cache Miss")
                    .message("The middleware hasn't updated the arrivals yet. Please try again after some time.")
                    .path("/api/v1/predictions")
                    .build();

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        return ResponseEntity.ok(stations);
    }
}
