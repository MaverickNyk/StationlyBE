package com.stationly.backend.scheduler;

import com.stationly.backend.service.TflPollingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ArrivalPredictionScheduler {

    private final TflPollingService tflPollingService;

    /**
     * Poll TfL API for arrival predictions on the scheduled interval
     */
    @Scheduled(fixedRateString = "${tfl.polling.interval}", initialDelayString = "${tfl.polling.interval}")
    public void pollAndUpdate() {
        tflPollingService.refreshAll();
    }

}
