package com.stationly.backend.controller;

import com.stationly.backend.model.LineInfo;
import com.stationly.backend.model.LineRouteResponse;
import com.stationly.backend.model.StationBrief;
import com.stationly.backend.model.TransportMode;
import com.stationly.backend.service.MetaService;
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
@RequestMapping("/api/v1/meta")
@RequiredArgsConstructor
@Tag(name = "Meta Data", description = "Static/Meta data for Transport Modes, Lines, and Stations")
public class MetaController {

    private final MetaService metaService;

    @Operation(summary = "Get Transport Modes", description = "Retrieves all supported transport modes (e.g., tube, bus, dlr).")
    @GetMapping("/modes")
    public List<TransportMode> getModes() {
        return metaService.getModes();
    }

    @Operation(summary = "Get Lines by Mode", description = "Retrieves all lines for a specific transport mode.")
    @GetMapping("/lines/{mode}")
    public List<LineInfo> getLines(
            @Parameter(description = "Transport mode ID (e.g. tube)", required = true) @PathVariable String mode) {
        return metaService.getLines(mode);
    }

    @Operation(summary = "Get Stations on Line", description = "Retrieves all stations associated with a specific line.")
    @GetMapping("/stations/{lineId}")
    public List<StationBrief> getStations(
            @Parameter(description = "Line ID (e.g. northern)", required = true) @PathVariable String lineId) {
        return metaService.getStationsOnLine(lineId);
    }

    @Operation(summary = "Get Line Route", description = "Retrieves the ordered route of stations for a specific line, including branches.")
    @GetMapping("/routes/{lineId}")
    public LineRouteResponse getRoute(
            @Parameter(description = "Line ID (e.g. victoria)", required = true) @PathVariable String lineId) {
        return metaService.getLineRoute(lineId);
    }
}
