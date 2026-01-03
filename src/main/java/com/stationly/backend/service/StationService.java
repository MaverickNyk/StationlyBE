package com.stationly.backend.service;

import ch.hsr.geohash.GeoHash;
import com.stationly.backend.client.TflApiClient;
import com.stationly.backend.model.Station;
import com.stationly.backend.repository.DataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StationService {

    private final TflApiClient tflApiClient;
    private final DataRepository<Station, String> stationRepository;

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

    /**
     * Search stations based on search keys.
     * Keys can be: mode, lineId, mode_lineId, lineId_direction,
     * mode_lineId_direction
     */
    public List<Station> searchStations(String key) {
        List<Station> results = stationRepository.findByField("searchKeys", key);
        log.info("🔍 Search stations with key '{}': found {}", key, results.size());
        return results;
    }

    public List<Station> getStationsByLine(String lineId) {
        return searchStations(lineId);
    }

    /**
     * Search stations within a given radius (km) of a location.
     */
    public List<Station> searchByLocation(double lat, double lon, double radiusKm) {
        List<Station> allStations = stationRepository.findAll();
        return allStations.stream()
                .filter(station -> calculateDistanceInKm(lat, lon, station.getLat(), station.getLon()) <= radiusKm)
                .collect(Collectors.toList());
    }

    private double calculateDistanceInKm(double lat1, double lon1, double lat2, double lon2) {
        // Haversine formula
        int R = 6371; // Radius of the earth in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public synchronized void syncLine(String lineId, String modeName) {
        log.info("🔄 Starting sync for line: {}", lineId);

        // 1. Fetch Basic Station Info (StopPoints)
        List<Map<String, Object>> stopPoints = tflApiClient.getStopPointsByLine(lineId);
        if (stopPoints == null || stopPoints.isEmpty()) {
            log.warn("⚠️ No stop points found for line: {}", lineId);
            return;
        }

        // 2. Fetch Route Sequences (Inbound & Outbound) and parse Ordered Routes
        Set<String> inboundIds = fetchNaptanIdsFromRouteSequence(lineId, "inbound");
        Set<String> outboundIds = fetchNaptanIdsFromRouteSequence(lineId, "outbound");

        // 3. Process each StopPoint
        for (Map<String, Object> sp : stopPoints) {
            processStopPoint(sp, lineId, modeName, inboundIds, outboundIds);
        }

        log.info("✅ Sync completed for line: {}", lineId);
    }

    private Set<String> fetchNaptanIdsFromRouteSequence(String lineId, String direction) {
        try {
            Map<String, Object> routeSeq = tflApiClient.getRouteSequence(lineId, direction);
            if (routeSeq == null)
                return Collections.emptySet();

            List<Map<String, Object>> orderedRoutes = (List<Map<String, Object>>) routeSeq.get("orderedLineRoutes");
            if (orderedRoutes == null)
                return Collections.emptySet();

            Set<String> naptanIds = new HashSet<>();
            for (Map<String, Object> route : orderedRoutes) {
                List<String> ids = (List<String>) route.get("naptanIds");
                if (ids != null) {
                    naptanIds.addAll(ids);
                }
            }
            return naptanIds;
        } catch (Exception e) {
            log.warn("⚠️ Failed to fetch {} route sequence for line {}: {}", direction, lineId, e.getMessage());
            return Collections.emptySet();
        }
    }

    private void processStopPoint(Map<String, Object> sp, String lineId, String modeName,
            Set<String> inboundIds, Set<String> outboundIds) {
        String stopType = (String) sp.get("stopType");
        String naptanId = (String) sp.get("naptanId");

        String expectedStopType = MODE_STOPTYPE_MAP.get(modeName.toLowerCase());
        if (expectedStopType == null || !expectedStopType.equals(stopType)) {
            return;
        }

        Station station = stationRepository.findById(naptanId).orElse(Station.builder()
                .naptanId(naptanId)
                .modes(new HashMap<>())
                .searchKeys(new ArrayList<>())
                .build());

        // Update basic fields
        station.setCommonName((String) sp.get("commonName"));
        station.setLat((Double) sp.get("lat"));
        station.setLon((Double) sp.get("lon"));
        station.setStopType(stopType);
        station.setGeoHash(GeoHash.geoHashStringWithCharacterPrecision(station.getLat(), station.getLon(), 9));
        station.setLastUpdatedTime(
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_DATE_TIME));

        // Update Mode Group
        Station.ModeGroup modeGroup = station.getModes().computeIfAbsent(modeName, k -> Station.ModeGroup.builder()
                .modeName(modeName)
                .lines(new HashMap<>())
                .build());

        // Determine Directions
        List<String> directions = new ArrayList<>();
        if (inboundIds.contains(naptanId))
            directions.add("inbound");
        if (outboundIds.contains(naptanId))
            directions.add("outbound");

        // Update Line Details within Mode Group
        Station.LineDetails lineDetails = modeGroup.getLines().computeIfAbsent(lineId,
                k -> Station.LineDetails.builder()
                        .id(lineId)
                        .name(lineId)
                        .directions(new ArrayList<>())
                        .build());

        // Merge directions
        for (String dir : directions) {
            if (!lineDetails.getDirections().contains(dir)) {
                lineDetails.getDirections().add(dir);
            }
        }

        // Regenerate Search Keys
        generateSearchKeys(station);

        // Save
        stationRepository.save(station);
    }

    private void generateSearchKeys(Station station) {
        Set<String> keys = new HashSet<>();

        // Iterate over Modes
        for (Map.Entry<String, Station.ModeGroup> modeEntry : station.getModes().entrySet()) {
            String modeName = modeEntry.getKey();
            Station.ModeGroup modeGroup = modeEntry.getValue();

            keys.add(modeName); // mode

            // Iterate over Lines within Mode
            for (Map.Entry<String, Station.LineDetails> lineEntry : modeGroup.getLines().entrySet()) {
                String lineId = lineEntry.getKey();
                Station.LineDetails details = lineEntry.getValue();

                keys.add(lineId); // lineId
                keys.add(modeName + "_" + lineId); // mode_lineId

                for (String dir : details.getDirections()) {
                    keys.add(lineId + "_" + dir); // lineId_direction
                    keys.add(modeName + "_" + lineId + "_" + dir); // mode_lineId_direction
                }
            }
        }

        station.setSearchKeys(new ArrayList<>(keys));
    }
}
