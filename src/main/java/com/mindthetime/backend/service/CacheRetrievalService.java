package com.mindthetime.backend.service;

import com.mindthetime.backend.model.DirectionPredictions;
import com.mindthetime.backend.model.LineData;
import com.mindthetime.backend.model.Station;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheRetrievalService {

    private final RedisService redisService;

    private String normalize(String input) {
        if (input == null)
            return "";
        // Replace spaces and other non-FCM-friendly characters with ~
        return input.toUpperCase().replaceAll("[^A-Z0-9-_.~%]", "~");
    }

    /**
     * Retrieve predictions from Redis cache for given parameters
     * Supports comma-separated values for station, mode, and direction
     * 
     * @param stations   Comma-separated station IDs
     * @param modes      Comma-separated mode/line IDs
     * @param directions Comma-separated directions
     * @return Map of cache keys to predictions, or empty map if none found
     */
    public Map<String, DirectionPredictions> getPredictions(String stations, String modes, String directions) {
        List<String> stationList = parseCommaSeparated(stations);
        List<String> modeList = parseCommaSeparated(modes);
        List<String> directionList = parseCommaSeparated(directions);

        Map<String, DirectionPredictions> results = new HashMap<>();

        // Generate all combinations of StationId::LineId::Direction
        for (String station : stationList) {
            for (String mode : modeList) {
                for (String direction : directionList) {
                    String key = String.format("%s-%s-%s",
                            normalize(station),
                            normalize(mode),
                            normalize(direction));

                    DirectionPredictions predictions = redisService.get(key, DirectionPredictions.class);

                    if (predictions != null) {
                        results.put(key, predictions);
                        log.debug("Cache hit for key: {}", key);
                    } else {
                        log.debug("Cache miss for key: {}", key);
                    }
                }
            }
        }

        return results;
    }

    /**
     * Retrieve predictions from Redis cache and structure them as Station objects
     * Supports comma-separated values for station, mode, and direction
     * 
     * @param stations   Comma-separated station IDs
     * @param modes      Comma-separated mode/line IDs
     * @param directions Comma-separated directions
     * @return List of Station objects with nested line and direction data
     */
    public List<Station> getStations(String stations, String modes, String directions) {
        List<String> stationList = parseCommaSeparated(stations);
        List<String> modeList = parseCommaSeparated(modes);
        List<String> directionList = parseCommaSeparated(directions);

        // Map to hold station data: stationId -> Station
        Map<String, Station> stationMap = new HashMap<>();

        // Generate all combinations and fetch from cache
        for (String stationId : stationList) {
            for (String mode : modeList) {
                for (String direction : directionList) {
                    String key = String.format("%s-%s-%s",
                            normalize(stationId),
                            normalize(mode),
                            normalize(direction));

                    DirectionPredictions predictions = redisService.get(key, DirectionPredictions.class);

                    if (predictions != null) {
                        log.debug("Cache hit for key: {}", key);

                        // Get or create Station
                        Station station = stationMap.computeIfAbsent(stationId, id -> Station.builder()
                                .stationId(id)
                                .stationName(predictions.getStationName())
                                .lines(new HashMap<>())
                                .build());

                        // Get or create LineData
                        LineData lineData = station.getLines().computeIfAbsent(mode, lineId -> LineData.builder()
                                .lineId(lineId)
                                .lineName(predictions.getLineName())
                                .directions(new HashMap<>())
                                .build());

                        // Remove redundant metadata from inner objects for the API response
                        // (They remain in Redis, but nulled here for the JSON response)
                        predictions.setStationId(null);
                        predictions.setStationName(null);
                        predictions.setLineId(null);
                        predictions.setLineName(null);

                        // Add DirectionPredictions
                        lineData.getDirections().put(direction, predictions);
                    } else {
                        log.debug("Cache miss for key: {}", key);
                    }
                }
            }
        }

        return new ArrayList<>(stationMap.values());
    }

    /**
     * Parse comma-separated string into list of trimmed values
     */
    private List<String> parseCommaSeparated(String input) {
        if (input == null || input.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
