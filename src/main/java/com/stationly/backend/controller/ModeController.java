package com.stationly.backend.controller;

import com.stationly.backend.model.TransportMode;
import com.stationly.backend.service.ModeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

@RestController
@RequestMapping("/api/v1/modes")
@RequiredArgsConstructor
@Tag(name = "Modes", description = "Transport modes data")
public class ModeController {

    private final ModeService modeService;

    @Operation(summary = "Get Transport Modes", description = "Retrieves all supported transport modes (e.g., tube, bus, dlr).")
    @GetMapping
    public List<TransportMode> getModes() {
        return modeService.getModes();
    }
}
