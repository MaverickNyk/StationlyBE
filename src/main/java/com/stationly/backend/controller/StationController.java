package com.stationly.backend.controller;

import com.stationly.backend.model.StationBrief;
import com.stationly.backend.service.StationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public List<StationBrief> getStationsByLine(
            @Parameter(description = "Line ID (e.g. northern)", required = true) @PathVariable String lineId) {
        return stationService.getStationsByLine(lineId);
    }
}
