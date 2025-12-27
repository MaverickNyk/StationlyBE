package com.mindthetime.backend.service;

import com.mindthetime.backend.client.TflApiClient;
import com.mindthetime.backend.model.LineInfo;
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

    // Exemption list for modes that are TflService but we don't want to show
    private static final Set<String> EXEMPT_MODES = Set.of(
            "national-rail", "tram", "river-bus", "cable-car", "river-tour");

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
            return Arrays.asList(cached);
        }

        log.info("Fetching transport modes from TfL API...");
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
        if (cached != null)
            return Arrays.asList(cached);

        log.info("Fetching lines for mode {} from TfL API...", mode);
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
        if (cached != null)
            return Arrays.asList(cached);

        log.info("Fetching stations for line {} from TfL API...", lineId);
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

    private String capitalize(String str) {
        if (str == null || str.isEmpty())
            return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).replace("-", " ");
    }
}
