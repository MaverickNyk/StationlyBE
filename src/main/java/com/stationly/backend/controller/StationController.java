package com.stationly.backend.controller;

import com.stationly.backend.model.Station;
import com.stationly.backend.service.StationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    @Operation(summary = "Search Stations", description = "Search stations by mode, line, direction, or combination. Optionally filter by location.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stations found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Station.class))),
            @ApiResponse(responseCode = "400", description = "Invalid parameters", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @GetMapping("/search")
    public ResponseEntity<List<Station>> searchStations(
            @Parameter(description = "Search key. Examples: 'tube' (all tube stations), 'northern' (all northern line), 'tube_northern' (tube + northern), 'northern_inbound' (northern line inbound)", required = false, example = "tube_northern") @RequestParam(value = "searchKey", required = false) String searchKey,
            @Parameter(description = "Latitude", required = false) @RequestParam(required = false) Double lat,
            @Parameter(description = "Longitude", required = false) @RequestParam(required = false) Double lon,
            @Parameter(description = "Radius in KM", required = false) @RequestParam(required = false, defaultValue = "1.0") Double radius) {

        if (searchKey != null && !searchKey.isEmpty()) {
            return ResponseEntity.ok(stationService.searchStations(searchKey));
        } else if (lat != null && lon != null) {
            if (radius <= 0) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(stationService.searchByLocation(lat, lon, radius));
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Sync Line Stations", description = "Triggers a sync of stations for a specific line from TfL API.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sync initiated", content = @Content),
            @ApiResponse(responseCode = "400", description = "Missing required parameters", content = @Content)
    })
    @PostMapping("/sync/{lineId}")
    public ResponseEntity<String> syncLine(
            @Parameter(description = "Line ID (e.g. northern)", required = true) @PathVariable String lineId,
            @Parameter(description = "Mode Name (e.g. tube)", required = true) @RequestParam String mode) {

        if (lineId == null || lineId.trim().isEmpty() || mode == null || mode.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Line ID and Mode are required.");
        }

        stationService.syncLine(lineId, mode);
        return ResponseEntity.ok("Sync initiated for line: " + lineId);
    }
}
