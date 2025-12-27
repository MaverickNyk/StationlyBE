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
     * Uses consolidated Station keys and performs in-memory filtering
     * 
     * @param stations   Comma-separated station IDs
     * @param modes      Comma-separated mode/line IDs
     * @param directions Comma-separated directions
     * @return Map of cache keys to predictions, or empty map if none found
     */
    public Map<String, DirectionPredictions> getPredictions(String stations, String modes, String directions) {
        List<String> stationIds = parseCommaSeparated(stations);
        List<String> modeList = parseCommaSeparated(modes);
        List<String> directionList = parseCommaSeparated(directions);

        Map<String, DirectionPredictions> results = new HashMap<>();

        for (String stationId : stationIds) {
            String redisKey = "Station_" + normalize(stationId);
            Station cachedStation = redisService.get(redisKey, Station.class);

            if (cachedStation != null && cachedStation.getLines() != null) {
                cachedStation.getLines().forEach((lineId, lineData) -> {
                    // Filter by mode/line if requested
                    if (modeList.isEmpty() || modeList.contains(lineId)) {
                        if (lineData.getDirections() != null) {
                            lineData.getDirections().forEach((direction, predictions) -> {
                                // Filter by direction if requested
                                if (directionList.isEmpty() || directionList.contains(direction)) {
                                    String resultKey = String.format("%s-%s-%s",
                                            normalize(stationId),
                                            normalize(lineId),
                                            normalize(direction));
                                    results.put(resultKey, predictions);
                                }
                            });
                        }
                    }
                });
            }
        }

        return results;
    }

    /**
     * Retrieve predictions from Redis cache and structure them as Station objects
     * Uses consolidated Station keys and performs in-memory filtering
     * 
     * @param stations   Comma-separated station IDs
     * @param modes      Comma-separated mode/line IDs
     * @param directions Comma-separated directions
     * @return List of Station objects with nested line and direction data
     */
    public List<Station> getStations(String stations, String modes, String directions) {
        List<String> stationIds = parseCommaSeparated(stations);
        List<String> modeList = parseCommaSeparated(modes);
        List<String> directionList = parseCommaSeparated(directions);

        List<Station> results = new ArrayList<>();

        for (String stationId : stationIds) {
            String redisKey = "Station_" + normalize(stationId);
            Station station = redisService.get(redisKey, Station.class);

            if (station != null) {
                // Filter the station's lines and directions in-memory
                Station filteredStation = filterStation(station, modeList, directionList);
                if (filteredStation != null) {
                    results.add(filteredStation);
                }
            }
        }

        return results;
    }

    /**
     * Helper to filter a Station's lines and directions
     */
    private Station filterStation(Station station, List<String> modeList, List<String> directionList) {
        if (station.getLines() == null)
            return null;

        Map<String, LineData> filteredLines = new HashMap<>();

        station.getLines().forEach((lineId, lineData) -> {
            if (modeList.isEmpty() || modeList.contains(lineId)) {
                if (lineData.getDirections() != null) {
                    Map<String, DirectionPredictions> filteredDirections = new HashMap<>();
                    lineData.getDirections().forEach((direction, predictions) -> {
                        if (directionList.isEmpty() || directionList.contains(direction)) {
                            filteredDirections.put(direction, predictions);
                        }
                    });

                    if (!filteredDirections.isEmpty()) {
                        lineData.setDirections(filteredDirections);
                        filteredLines.put(lineId, lineData);
                    }
                }
            }
        });

        if (filteredLines.isEmpty())
            return null;

        station.setLines(filteredLines);
        return station;
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
