package com.stationly.backend.service;

import com.stationly.backend.client.TflApiClient;
import com.stationly.backend.model.LineInfo;
import com.stationly.backend.model.LineRouteResponse;
import com.stationly.backend.model.LineStatusResponse;
import com.stationly.backend.repository.DataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LineService {

    private final TflApiClient tflApiClient;
    private final DataRepository<LineInfo, String> lineRepository;
    private final DataRepository<LineRouteResponse, String> routeRepository;
    private final DataRepository<LineStatusResponse, String> lineStatusRepository;

    @Value("${tfl.transport.modes}")
    private String tflTransportModes;

    public List<LineInfo> getLinesByMode(String mode) {
        List<LineInfo> cached = lineRepository.findByField("modeName", mode);
        if (!cached.isEmpty()) {
            log.info("DATA: 🟢 Firestore HIT for lines (mode: {})", mode);
            return cached;
        }

        log.info("DATA: ⚪ Firestore MISS for lines (mode: {}). Fetching from TfL...", mode);
        List<Map<String, Object>> rawLines = tflApiClient.getLinesByMode(mode);
        if (rawLines == null)
            return Collections.emptyList();

        List<LineInfo> lines = rawLines.stream()
                .map(l -> LineInfo.builder()
                        .id((String) l.get("id"))
                        .name((String) l.get("name"))
                        .modeName((String) l.get("modeName"))
                        .build())
                .collect(Collectors.toList());

        lineRepository.saveAll(lines);
        return lines;
    }

    @SuppressWarnings("unchecked")
    public LineRouteResponse getLineRoute(String lineId) {
        Optional<LineRouteResponse> cached = routeRepository.findById(lineId);
        if (cached.isPresent()) {
            log.info("DATA: 🟢 Firestore HIT for route (line: {})", lineId);
            return cached.get();
        }

        log.info("DATA: ⚪ Firestore MISS for route (line: {}). Fetching from TfL...", lineId);
        Map<String, Object> rawRoute = tflApiClient.getLineRoute(lineId);
        if (rawRoute == null)
            return null;

        List<Map<String, Object>> rawSections = (List<Map<String, Object>>) rawRoute.get("routeSections");

        // Group destinations by direction and ensure uniqueness
        Map<String, Set<LineRouteResponse.Destination>> groupedDirections = new HashMap<>();
        if (rawSections != null) {
            for (Map<String, Object> section : rawSections) {
                String direction = (String) section.get("direction");
                String destinationName = (String) section.get("destinationName");
                String destinationId = (String) section.get("destination");
                if (direction != null && destinationName != null && destinationId != null) {
                    groupedDirections.computeIfAbsent(direction, k -> new LinkedHashSet<>())
                            .add(LineRouteResponse.Destination.builder()
                                    .id(destinationId)
                                    .name(destinationName)
                                    .build());
                }
            }
        }

        List<LineRouteResponse.DirectionInfo> directions = groupedDirections.entrySet().stream()
                .map(e -> LineRouteResponse.DirectionInfo.builder()
                        .direction(e.getKey())
                        .destinations(new ArrayList<>(e.getValue()))
                        .build())
                .collect(Collectors.toList());

        LineRouteResponse response = LineRouteResponse.builder()
                .id((String) rawRoute.get("id"))
                .name((String) rawRoute.get("name"))
                .modeName((String) rawRoute.get("modeName"))
                .directions(directions)
                .build();

        routeRepository.save(response);
        return response;
    }

    // Line Status Methods
    @Scheduled(fixedRateString = "${tfl.status.polling.interval:300000}") // Default 5 mins
    public List<LineStatusResponse> pollLineStatuses() {
        String[] modes = tflTransportModes.split(",");
        List<LineStatusResponse> allStatuses = new ArrayList<>();

        for (String mode : modes) {
            String trimmedMode = mode.trim();
            if (trimmedMode.isEmpty())
                continue;

            log.info("🚇 Starting line status polling for mode: {}", trimmedMode);
            try {
                List<Map<String, Object>> rawStatuses = tflApiClient.getLineStatuses(trimmedMode);
                if (rawStatuses == null || rawStatuses.isEmpty()) {
                    log.warn("⚠️ No line statuses received from TfL for mode: {}", trimmedMode);
                    continue;
                }

                List<LineStatusResponse> modeStatuses = rawStatuses.stream()
                        .map(this::mapToLineStatusResponse)
                        .collect(Collectors.toList());
                allStatuses.addAll(modeStatuses);

            } catch (Exception e) {
                log.error("❌ Error polling line statuses for mode: {}", trimmedMode, e);
            }
        }

        if (!allStatuses.isEmpty()) {
            lineStatusRepository.saveAll(allStatuses);
            log.info("✅ Successfully polled and saved {} line statuses to Firestore", allStatuses.size());
        }
        return allStatuses;
    }

    @SuppressWarnings("unchecked")
    private LineStatusResponse mapToLineStatusResponse(Map<String, Object> l) {
        String id = (String) l.get("id");
        String name = (String) l.get("name");
        List<Map<String, Object>> lineStatuses = (List<Map<String, Object>>) l.get("lineStatuses");

        String statusSeverityDescription = "Unknown";
        String reason = null;

        if (lineStatuses != null && !lineStatuses.isEmpty()) {
            Map<String, Object> firstStatus = lineStatuses.get(0);
            statusSeverityDescription = (String) firstStatus.get("statusSeverityDescription");
            reason = (String) firstStatus.get("reason");
        }

        return LineStatusResponse.builder()
                .id(id)
                .name(name)
                .statusSeverityDescription(statusSeverityDescription)
                .reason(reason)
                .lastUpdatedTime(java.time.LocalDateTime.now().toString())
                .build();
    }

    public List<LineStatusResponse> getLineStatuses() {
        List<LineStatusResponse> statuses = lineStatusRepository.findAll();
        if (statuses.isEmpty()) {
            log.info("DATA: ⚪ Firestore MISS for line statuses. Triggering poll...");
            return pollLineStatuses();
        }
        log.info("DATA: 🟢 Firestore HIT for {} line statuses", statuses.size());
        return statuses;
    }
}
