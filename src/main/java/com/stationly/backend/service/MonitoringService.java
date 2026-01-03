package com.stationly.backend.service;

public interface MonitoringService {
    /**
     * Records the duration and status of a polling operation.
     * 
     * @param mode       The transport mode (e.g., "tube", "total")
     * @param durationMs The duration of the operation in milliseconds
     * @param status     The status of the operation (e.g., "SUCCESS", "FAILED")
     */
    void recordPollingDuration(String mode, long durationMs, String status);

    /**
     * Records the count of arrivals received.
     * 
     * @param mode  The transport mode
     * @param count The number of arrivals
     */
    void recordArrivalsCount(String mode, int count);
}
