package com.mindthetime.backend.controller;

import com.mindthetime.backend.model.LineInfo;
import com.mindthetime.backend.model.StationBrief;
import com.mindthetime.backend.model.TransportMode;
import com.mindthetime.backend.service.MetaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/meta")
@RequiredArgsConstructor
public class MetaController {

    private final MetaService metaService;

    @GetMapping("/modes")
    public List<TransportMode> getModes() {
        return metaService.getModes();
    }

    @GetMapping("/lines/{mode}")
    public List<LineInfo> getLines(@PathVariable String mode) {
        return metaService.getLines(mode);
    }

    @GetMapping("/stations/{lineId}")
    public List<StationBrief> getStations(@PathVariable String lineId) {
        return metaService.getStationsOnLine(lineId);
    }
}
