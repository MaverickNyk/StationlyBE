package com.stationly.backend.controller;

import com.stationly.backend.model.Station;
import com.stationly.backend.service.StationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;

@RestController
@RequestMapping("/api/v1/stations")
@RequiredArgsConstructor
@Tag(name = "Stations", description = "Station data")
public class StationController {

    private final StationService stationService;

    @Operation(summary = "Get Stations on Line", description = "Retrieves all stations associated with a specific line.")
    @GetMapping("/line/{lineId}")
    public List<Station> getStationsByLine(
            @Parameter(description = "Line ID (e.g. northern)", required = true) @PathVariable String lineId) {
        return stationService.getStationsByLine(lineId);
    }

    @Operation(summary = "Search Stations", description = "Search stations by mode, line, direction, or combination.")
    @GetMapping("/search")
    public ResponseEntity<List<Station>> searchStations(
            @Parameter(description = "Search key (e.g., 'tube', 'northern')", required = false) @RequestParam(required = false) String key,
            @Parameter(description = "Latitude", required = false) @RequestParam(required = false) Double lat,
            @Parameter(description = "Longitude", required = false) @RequestParam(required = false) Double lon,
            @Parameter(description = "Radius in KM", required = false) @RequestParam(required = false, defaultValue = "1.0") Double radius) {

        if (key != null && !key.isEmpty()) {
            return ResponseEntity.ok(stationService.searchStations(key));
        } else if (lat != null && lon != null) {
            return ResponseEntity.ok(stationService.searchByLocation(lat, lon, radius));
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Sync Line Stations", description = "Triggers a sync of stations for a specific line from TfL API.")
    @PostMapping("/sync/{lineId}")
    public ResponseEntity<String> syncLine(
            @Parameter(description = "Line ID (e.g. northern)", required = true) @PathVariable String lineId,
            @Parameter(description = "Mode Name (e.g. tube)", required = false) @RequestParam String mode) {
        stationService.syncLine(lineId, mode);
        return ResponseEntity.ok("Sync initiated for line: " + lineId);
    }
}
