package com.stationly.backend.service;

import com.stationly.backend.client.TflApiClient;
import com.stationly.backend.model.TransportMode;
import com.stationly.backend.repository.DataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModeService {

    private final TflApiClient tflApiClient;
    private final DataRepository<TransportMode, String> modeRepository;

    // Exemption list for modes that are TflService but we don't want to show
    private static final Set<String> EXEMPT_MODES = Set.of(
            "national-rail", "tram", "river-bus", "cable-car", "river-tour", "cycle-hire", "replacement-bus");

    // Display name mapping
    private static final Map<String, String> DISPLAY_NAME_MAP = Map.of(
            "tube", "Tube",
            "dlr", "DLR",
            "overground", "Overground",
            "elizabeth-line", "Elizabeth Line",
            "bus", "Bus");

    public List<TransportMode> getModes() {
        List<TransportMode> cached = modeRepository.findAll();
        if (!cached.isEmpty()) {
            log.info("DATA: 🟢 Firestore HIT for transport modes");
            return cached;
        }

        log.info("DATA: ⚪ Firestore MISS for transport modes. Fetching from TfL...");
        List<Map<String, Object>> rawModes = tflApiClient.getTransportModes();
        if (rawModes == null)
            return Collections.emptyList();

        List<TransportMode> modes = rawModes.stream()
                .filter(m -> Boolean.TRUE.equals(m.get("isTflService")))
                .map(m -> (String) m.get("modeName"))
                .filter(name -> !EXEMPT_MODES.contains(name))
                .map(name -> TransportMode.builder()
                        .modeName(name)
                        .displayName(DISPLAY_NAME_MAP.getOrDefault(name, capitalize(name)))
                        .build())
                .collect(Collectors.toList());

        modeRepository.saveAll(modes);
        return modes;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty())
            return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).replace("-", " ");
    }
}
