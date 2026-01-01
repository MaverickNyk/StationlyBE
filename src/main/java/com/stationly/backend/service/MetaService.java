package com.stationly.backend.service;

import com.stationly.backend.client.TflApiClient;
import com.stationly.backend.model.LineInfo;
import com.stationly.backend.model.LineRouteResponse;
import com.stationly.backend.model.StationBrief;
import com.stationly.backend.model.TransportMode;
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
    private final FirebaseCacheService cacheService;

    private static final String CACHE_KEY_MODES = "meta:modes";
    private static final String CACHE_KEY_LINES_PREFIX = "meta:lines:";
    private static final String CACHE_KEY_STATIONS_PREFIX = "meta:stations:";
    private static final String CACHE_KEY_ROUTE_PREFIX = "meta:route:";

    // Exemption list for modes that are TflService but we don't want to show
    private static final Set<String> EXEMPT_MODES = Set.of(
            "national-rail", "tram", "river-bus", "cable-car", "river-tour", "cycle-hire", "replacement-bus");

    // Mapping of transport mode to its corresponding stopType for station filtering
    private static final Map<String, String> MODE_STOPTYPE_MAP = Map.of(
            "bus", "NaptanPublicBusCoachTram",
            "tube", "NaptanMetroStation",
            "underground", "NaptanMetroStation",
            "overground", "NaptanRailStation",
            "elizabeth-line", "NaptanRailStation",
            "dlr", "NaptanMetroStation",
            "national-rail", "NaptanRailStation",
            "tram", "NaptanPublicBusCoachTram",
            "river-bus", "NaptanFerryPort",
            "cable-car", "NaptanCableCarStation");

    // Display name mapping
    private static final Map<String, String> DISPLAY_NAME_MAP = Map.of(
            "tube", "Tube",
            "dlr", "DLR",
            "overground", "Overground",
            "elizabeth-line", "Elizabeth Line",
            "bus", "Bus");

    public List<TransportMode> getModes() {
        TransportMode[] cached = cacheService.get(CACHE_KEY_MODES, TransportMode[].class);
        if (cached != null) {
            log.info("META: 🟢 Firebase HIT for transport modes");
            return Arrays.asList(cached);
        }

        log.info("META: ⚪ Firebase MISS for transport modes. Fetching from TfL...");
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

        cacheService.save(CACHE_KEY_MODES, modes);
        return modes;
    }

    public List<LineInfo> getLines(String mode) {
        String cacheKey = CACHE_KEY_LINES_PREFIX + mode;
        LineInfo[] cached = cacheService.get(cacheKey, LineInfo[].class);
        if (cached != null) {
            log.info("META: 🟢 Firebase HIT for lines (mode: {})", mode);
            return Arrays.asList(cached);
        }

        log.info("META: ⚪ Firebase MISS for lines (mode: {}). Fetching from TfL...", mode);
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

        cacheService.save(cacheKey, lines);
        return lines;
    }

    public List<StationBrief> getStationsOnLine(String lineId) {
        String cacheKey = CACHE_KEY_STATIONS_PREFIX + lineId;
        StationBrief[] cached = cacheService.get(cacheKey, StationBrief[].class);
        if (cached != null) {
            log.info("META: 🟢 Firebase HIT for stations (line: {})", lineId);
            return Arrays.asList(cached);
        }

        log.info("META: ⚪ Firebase MISS for stations (line: {}). Fetching from TfL...", lineId);
        List<Map<String, Object>> rawStations = tflApiClient.getStopPointsByLine(lineId);
        if (rawStations == null) {
            return Collections.emptyList();
        }

        List<StationBrief> stations = rawStations.stream()
                .filter(s -> {
                    String stopType = (String) s.get("stopType");
                    List<String> modes = (List<String>) s.get("modes");
                    if (modes == null)
                        return false;
                    for (String mode : modes) {
                        String mapped = MODE_STOPTYPE_MAP.get(mode.toLowerCase());
                        if (mapped != null && mapped.equals(stopType)) {
                            return true;
                        }
                    }
                    return false;
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

        cacheService.save(cacheKey, stations);
        return stations;
    }

    public LineRouteResponse getLineRoute(String lineId) {
        String cacheKey = CACHE_KEY_ROUTE_PREFIX + lineId;
        LineRouteResponse cached = cacheService.get(cacheKey, LineRouteResponse.class);
        if (cached != null) {
            log.info("META: 🟢 Firebase HIT for route (line: {})", lineId);
            return cached;
        }

        log.info("META: ⚪ Firebase MISS for route (line: {}). Fetching from TfL...", lineId);
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

        cacheService.save(cacheKey, response);
        return response;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty())
            return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).replace("-", " ");
    }
}
