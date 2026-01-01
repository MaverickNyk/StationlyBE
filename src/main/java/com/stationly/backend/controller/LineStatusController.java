package com.stationly.backend.controller;

import com.stationly.backend.model.LineStatusResponse;
import com.stationly.backend.service.LineStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;

@RestController
@RequestMapping("/api/v1/status")
@RequiredArgsConstructor
@Tag(name = "Line Status", description = "Current status of TFL lines")
public class LineStatusController {

    private final LineStatusService lineStatusService;

    @Operation(summary = "Get All Line Statuses", description = "Retrieves the latest status for all TFL transport lines (e.g., Tube, DLR, Overground) from the Firebase cache.")
    @ApiResponse(responseCode = "200", description = "Successful retrieval")
    @GetMapping("/lines")
    public List<LineStatusResponse> getLineStatuses() {
        return lineStatusService.getLineStatusesFromFirebase();
    }
}
