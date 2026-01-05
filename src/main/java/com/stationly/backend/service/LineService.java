package com.stationly.backend.service;

import com.stationly.backend.client.TflApi;
import com.stationly.backend.model.LineInfo;
import com.stationly.backend.model.LineRouteResponse;
import com.stationly.backend.model.LineStatusResponse;
import com.stationly.backend.repository.DataRepository;
import com.stationly.backend.util.TflUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class LineService {

    private final TflApi tflApiClient;
    private final DataRepository<LineInfo, String> lineRepository;
    private final DataRepository<LineRouteResponse, String> routeRepository;
    private final DataRepository<LineStatusResponse, String> lineStatusRepository;
    private final NotificationService fcmService;

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

    public List<LineStatusResponse> syncLineStatuses() {
        String[] modes = tflTransportModes.split(",");
        List<LineStatusResponse> allStatuses = new ArrayList<>();
        Map<String, Object> fcmUpdates = new HashMap<>();

        // 1. Fetch existing statuses to compare against
        Map<String, LineStatusResponse> existingStatuses = lineStatusRepository.findAll().stream()
                .collect(Collectors.toMap(LineStatusResponse::getId, Function.identity(), (a, b) -> a));

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

                for (Map<String, Object> raw : rawStatuses) {
                    LineStatusResponse newStatus = mapToLineStatusResponse(raw, trimmedMode);
                    LineStatusResponse oldStatus = existingStatuses.get(newStatus.getId());

                    // OPTIMIZATION: If new status is "Good Service" (which generates a random
                    // reason if empty)
                    // and we already have a valid random reason stored, KEEP the stored one to
                    // avoid noise.
                    if (oldStatus != null
                            && "Good Service".equalsIgnoreCase(newStatus.getStatusSeverityDescription())
                            && TflUtils.GOOD_SERVICE_MESSAGES.contains(oldStatus.getReason())) {
                        newStatus.setReason(oldStatus.getReason());
                    }

                    // Change Detection
                    boolean changed = false;
                    if (oldStatus == null) {
                        changed = true; // New line
                    } else {
                        boolean statusChanged = !Objects.equals(oldStatus.getStatusSeverityDescription(),
                                newStatus.getStatusSeverityDescription());
                        boolean reasonChanged = !Objects.equals(oldStatus.getReason(), newStatus.getReason());
                        changed = statusChanged || reasonChanged;
                    }

                    if (changed) {
                        String topic = "mode_" + newStatus.getId();
                        log.info("🔔 Status changed for line {} (Mode: {}). Queuing FCM update for topic: {}",
                                newStatus.getId(), newStatus.getMode(), topic);
                        fcmUpdates.put(topic, newStatus);
                    }

                    allStatuses.add(newStatus);
                }

            } catch (Exception e) {
                log.error("❌ Error polling line statuses for mode: {}", trimmedMode, e);
            }
        }

        if (!allStatuses.isEmpty()) {
            lineStatusRepository.saveAll(allStatuses);
            log.info("✅ Successfully polled and saved {} line statuses to Firestore", allStatuses.size());
        }

        if (!fcmUpdates.isEmpty()) {
            log.info("🚀 Pushing {} batched line status updates to FCM...", fcmUpdates.size());
            fcmService.publishAll(fcmUpdates);
        }

        return allStatuses;
    }

    @SuppressWarnings("unchecked")
    private LineStatusResponse mapToLineStatusResponse(Map<String, Object> l, String mode) {
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
                .reason(updateReason(statusSeverityDescription, reason))
                .mode(mode)
                .lastUpdatedTime(java.time.LocalDateTime.now().toString())
                .build();
    }

    public List<LineStatusResponse> getLineStatuses(String lineId, String mode) {
        List<LineStatusResponse> statuses = lineStatusRepository.findAll();

        Stream<LineStatusResponse> stream = statuses.stream();
        if (mode != null && !mode.isEmpty()) {
            stream = stream.filter(s -> mode.equalsIgnoreCase(s.getMode()));
        }
        if (lineId != null && !lineId.isEmpty()) {
            stream = stream.filter(s -> lineId.equalsIgnoreCase(s.getId()));
        }
        List<LineStatusResponse> filtered = stream.collect(Collectors.toList());

        if (statuses.isEmpty()) {
            log.info("DATA: ⚪ Firestore MISS for line statuses. Triggering poll...");
            // Sync returns all, so we must filter the result of sync as well
            List<LineStatusResponse> fresh = syncLineStatuses();
            // Re-apply filter
            Stream<LineStatusResponse> freshStream = fresh.stream();
            if (mode != null && !mode.isEmpty())
                freshStream = freshStream.filter(s -> mode.equalsIgnoreCase(s.getMode()));
            if (lineId != null && !lineId.isEmpty())
                freshStream = freshStream.filter(s -> lineId.equalsIgnoreCase(s.getId()));
            return freshStream.collect(Collectors.toList());
        }

        log.info("DATA: 🟢 Firestore HIT for {} line statuses (filtered from {})", filtered.size(), statuses.size());
        return filtered;
    }

    private String updateReason(String statusSeverityDescriptionString, String reasonString) {
        // 1. Check if status is Good Service AND the existing reason is empty/null
        if ("Good Service".equalsIgnoreCase(statusSeverityDescriptionString)
                && (reasonString == null || reasonString.trim().isEmpty())) {

            // 2. Assign a random message from your list
            int index = new Random().nextInt(TflUtils.GOOD_SERVICE_MESSAGES.size());
            return TflUtils.GOOD_SERVICE_MESSAGES.get(index);
        }

        // 3. If there is already a reason (like a delay description), return it as is
        return reasonString;
    }

}
