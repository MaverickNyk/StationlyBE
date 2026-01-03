package com.stationly.backend.service;

import com.stationly.backend.client.TflApiClient;
import com.stationly.backend.model.StationBrief;
import com.stationly.backend.repository.DataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StationService {

    private final TflApiClient tflApiClient;
    private final DataRepository<StationBrief, String> stationRepository;

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

    @SuppressWarnings("unchecked")
    public List<StationBrief> getStationsByLine(String lineId) {
        List<StationBrief> cached = stationRepository.findByField("lineIds", lineId);
        if (!cached.isEmpty()) {
            log.info("DATA: 🟢 Firestore HIT for stations (line: {})", lineId);
            return cached;
        }

        log.info("DATA: ⚪ Firestore MISS for stations (line: {}). Fetching from TfL...", lineId);
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

                    // Extract line IDs for efficient querying
                    List<String> lineIds = lines.stream()
                            .map(StationBrief.LineSummary::getId)
                            .collect(Collectors.toList());

                    return StationBrief.builder()
                            .stationId((String) s.get("naptanId"))
                            .stationName((String) s.get("commonName"))
                            .lines(lines)
                            .lineIds(lineIds)
                            .build();
                })
                .collect(Collectors.toList());

        stationRepository.saveAll(stations);
        return stations;
    }
}
