package com.mindthetime.backend.service;

import com.mindthetime.backend.model.ArrivalPrediction;
import com.mindthetime.backend.model.DirectionPredictions;
import com.mindthetime.backend.model.PredictionItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DataTransformationService {

    private String normalize(String input) {
        if (input == null)
            return "";
        // Replace spaces and other non-FCM-friendly characters with ~
        return input.toUpperCase().replaceAll("[^A-Z0-9-_.~%]", "~");
    }

    /**
     * Transform TfL arrivals into grouped predictions by
     * StationId::LineId::Direction
     * 
     * @param arrivals Raw TfL arrival predictions
     * @return Map with key pattern "StationId::LineId::Direction" and
     *         DirectionPredictions as value
     */
    public Map<String, DirectionPredictions> transformToGroupedPredictions(List<ArrivalPrediction> arrivals) {
        Map<String, DirectionPredictions> groupedPredictions = new HashMap<>();
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);

        // Group by StationId-LineId-Direction (all uppercase for case-insensitive
        // matching)
        Map<String, List<ArrivalPrediction>> grouped = arrivals.stream()
                .filter(a -> a.getNaptanId() != null && a.getLineId() != null && a.getDirection() != null)
                .collect(Collectors.groupingBy(
                        a -> String.format("%s-%s-%s",
                                normalize(a.getNaptanId()),
                                normalize(a.getLineId()),
                                normalize(a.getDirection()))));

        // Transform each group into DirectionPredictions
        grouped.forEach((key, predictions) -> {
            log.debug("Processing group {}: found {} arrivals", key, predictions.size());
            List<PredictionItem> items = predictions.stream()
                    .map(this::toPredictionItem)
                    .sorted(Comparator.comparing(PredictionItem::getExpectedArrival,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());

            if (!items.isEmpty()) {
                ArrivalPrediction first = predictions.get(0);
                DirectionPredictions directionPredictions = DirectionPredictions.builder()
                        .stationId(first.getNaptanId())
                        .stationName(first.getStationName())
                        .lineId(first.getLineId())
                        .lineName(first.getLineName())
                        .mode(first.getModeName())
                        .direction(first.getDirection())
                        .lastUpdatedTime(now)
                        .predictions(items)
                        .build();

                groupedPredictions.put(key, directionPredictions);
            }
        });

        log.debug("Transformed {} arrivals into {} grouped predictions", arrivals.size(), groupedPredictions.size());
        return groupedPredictions;
    }

    /**
     * Helper to group arrivals
     */

    private PredictionItem toPredictionItem(ArrivalPrediction arrival) {
        return PredictionItem.builder()
                .destinationName(arrival.getDestinationName())
                .destinationNaptanId(arrival.getDestinationNaptanId())
                .towards(arrival.getTowards())
                .platformName(arrival.getPlatformName())
                .expectedArrival(arrival.getExpectedArrival() != null
                        ? arrival.getExpectedArrival().format(DateTimeFormatter.ISO_INSTANT)
                        : null)
                .build();
    }
}
