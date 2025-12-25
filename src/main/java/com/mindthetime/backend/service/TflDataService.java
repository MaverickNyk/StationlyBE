package com.mindthetime.backend.service;

import com.mindthetime.backend.client.TflApiClient;
import com.mindthetime.backend.model.ArrivalPrediction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TflDataService {

    private final TflApiClient tflApiClient;

    @Cacheable(value = "arrivals", key = "#stationId")
    public List<ArrivalPrediction> getArrivalsForStation(String stationId) {
        log.info("Cache miss. Fetching fresh data from TFL API for station: {}", stationId);
        return tflApiClient.getArrivals(stationId);
    }
}
