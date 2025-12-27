package com.mindthetime.backend.service;

import com.mindthetime.backend.client.TflApiClient;
import com.mindthetime.backend.model.LineInfo;
import com.mindthetime.backend.model.LineRouteResponse;
import com.mindthetime.backend.model.StationBrief;
import com.mindthetime.backend.model.TransportMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetaService {

    private final TflApiClient tflApiClient;
    private final RedisService redisService;

    private static final long CACHE_TTL_SECONDS = 24 * 60 * 60; // 24 hours
    private static final String CACHE_KEY_MODES = "meta:modes";
    private static final String CACHE_KEY_LINES_PREFIX = "meta:lines:";
    private static final String CACHE_KEY_STATIONS_PREFIX = "meta:stations:";
    private static final String CACHE_KEY_ROUTE_PREFIX = "meta:route:";

    // Exemption list for modes that are TflService but we don't want to show
    private static final Set<String> EXEMPT_MODES = Set.of(
            "national-rail", "tram", "river-bus", "cable-car", "river-tour", "cycle-hire", "replacement-bus",
            "river-tour");

    // Display name mapping
    private static final Map<String, String> DISPLAY_NAME_MAP = Map.of(
            "tube", "Tube",
            "dlr", "DLR",
            "overground", "Overground",
            "elizabeth-line", "Elizabeth Line",
            "bus", "Bus");

    public List<TransportMode> getModes() {
        TransportMode[] cached = redisService.get(CACHE_KEY_MODES, TransportMode[].class);
        if (cached != null) {
            log.info("META: 🟢 Redis HIT for transport modes");
            return Arrays.asList(cached);
        }

        log.info("META: ⚪ Redis MISS for transport modes. Fetching from TfL...");
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

        redisService.save(CACHE_KEY_MODES, modes, CACHE_TTL_SECONDS);
        return modes;
    }

    public List<LineInfo> getLines(String mode) {
        String cacheKey = CACHE_KEY_LINES_PREFIX + mode;
        LineInfo[] cached = redisService.get(cacheKey, LineInfo[].class);
        if (cached != null) {
            log.info("META: 🟢 Redis HIT for lines (mode: {})", mode);
            return Arrays.asList(cached);
        }

        log.info("META: ⚪ Redis MISS for lines (mode: {}). Fetching from TfL...", mode);
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

        redisService.save(cacheKey, lines, CACHE_TTL_SECONDS);
        return lines;
    }

    public List<StationBrief> getStationsOnLine(String lineId) {
        String cacheKey = CACHE_KEY_STATIONS_PREFIX + lineId;
        StationBrief[] cached = redisService.get(cacheKey, StationBrief[].class);
        if (cached != null) {
            log.info("META: 🟢 Redis HIT for stations (line: {})", lineId);
            return Arrays.asList(cached);
        }

        log.info("META: ⚪ Redis MISS for stations (line: {}). Fetching from TfL...", lineId);
        List<Map<String, Object>> rawStations = tflApiClient.getStopPointsByLine(lineId);
        if (rawStations == null)
            return Collections.emptyList();

        List<StationBrief> stations = rawStations.stream()
                .filter(s -> {
                    String stopType = (String) s.get("stopType");
                    return "NaptanMetroStation".equals(stopType) || "NaptanRailStation".equals(stopType);
                })
                .map(s -> {
                    List<Map<String, Object>> rawLines = (List<Map<String, Object>>) s.get("lines");
                    List<StationBrief.LineSummary> lines = rawLines == null ? Collections.emptyList()
                            : rawLines.stream()
                                    .map(l -> StationBrief.LineSummary.builder()
                                            .id((String) l.get("id"))
                                            .name((String) l.get("name"))
                                            .build())
                                    .collect(Collectors.toList());

                    return StationBrief.builder()
                            .stationId((String) s.get("naptanId"))
                            .stationName((String) s.get("commonName"))
                            .lines(lines)
                            .build();
                })
                .collect(Collectors.toList());

        redisService.save(cacheKey, stations, CACHE_TTL_SECONDS);
        return stations;
    }

    public LineRouteResponse getLineRoute(String lineId) {
        String cacheKey = CACHE_KEY_ROUTE_PREFIX + lineId;
        LineRouteResponse cached = redisService.get(cacheKey, LineRouteResponse.class);
        if (cached != null) {
            log.info("META: 🟢 Redis HIT for route (line: {})", lineId);
            return cached;
        }

        log.info("META: ⚪ Redis MISS for route (line: {}). Fetching from TfL...", lineId);
        Map<String, Object> rawRoute = tflApiClient.getLineRoute(lineId);
        if (rawRoute == null)
            return null;

        List<Map<String, Object>> rawSections = (List<Map<String, Object>>) rawRoute.get("routeSections");

        // Group destinations by direction and ensure uniqueness
        Map<String, Set<String>> groupedDirections = new HashMap<>();
        if (rawSections != null) {
            for (Map<String, Object> section : rawSections) {
                String direction = (String) section.get("direction");
                String destination = (String) section.get("destinationName");
                if (direction != null && destination != null) {
                    groupedDirections.computeIfAbsent(direction, k -> new LinkedHashSet<>()).add(destination);
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

        redisService.save(cacheKey, response, CACHE_TTL_SECONDS);
        return response;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty())
            return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).replace("-", " ");
    }
}
