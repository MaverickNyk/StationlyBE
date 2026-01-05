package com.stationly.backend.scheduler;

import com.stationly.backend.service.LineService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LineStatusScheduler {

    private final LineService lineService;

    /**
     * Poll Line Statuses from TfL API on the scheduled interval
     */
    @Scheduled(fixedRateString = "${tfl.status.polling.interval}", initialDelayString = "${tfl.status.polling.interval}")
    public void pollAndUpdate() {
        lineService.syncLineStatuses();
    }

}
