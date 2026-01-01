package com.stationly.backend.service;

import com.stationly.backend.model.ArrivalPrediction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class NotificationService {

    public void broadcastUpdates(String stationId, List<ArrivalPrediction> predictions) {
        // TODO: Implement FCM or Async Queue push here
        log.info("Broadcasting updates for station {}: {} predictions", stationId, predictions.size());
    }
}
