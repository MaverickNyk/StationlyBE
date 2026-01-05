package com.stationly.backend.controller;

import com.stationly.backend.model.LineInfo;
import com.stationly.backend.model.LineRouteResponse;
import com.stationly.backend.model.LineStatusResponse;
import com.stationly.backend.service.LineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;

@RestController
@RequestMapping("/api/v1/lines")
@RequiredArgsConstructor
@Tag(name = "Lines", description = "Transport lines data including status")
public class LineController {

    private final LineService lineService;

    @Operation(summary = "Get Lines by Mode", description = "Retrieves all lines for a specific transport mode.")
    @GetMapping("/mode/{mode}")
    public List<LineInfo> getLinesByMode(
            @Parameter(description = "Transport mode ID (e.g. tube)", required = true) @PathVariable String mode) {
        return lineService.getLinesByMode(mode);
    }

    @Operation(summary = "Get Line Route", description = "Retrieves the ordered route of stations for a specific line, including branches.")
    @GetMapping("/{lineId}/route")
    public LineRouteResponse getRoute(
            @Parameter(description = "Line ID (e.g. victoria)", required = true) @PathVariable String lineId) {
        return lineService.getLineRoute(lineId);
    }

    @Operation(summary = "Get All Line Statuses", description = "Retrieves the latest status for all TFL transport lines. Supports filtering by lineId and mode.")
    @ApiResponse(responseCode = "200", description = "Successful retrieval")
    @GetMapping("/status")
    public List<LineStatusResponse> getLineStatuses(
            @Parameter(description = "Optional Line ID filter") @org.springframework.web.bind.annotation.RequestParam(required = false) String lineId,
            @Parameter(description = "Optional Transport Mode filter") @org.springframework.web.bind.annotation.RequestParam(required = false) String mode) {
        return lineService.getLineStatuses(lineId, mode);
    }
}
