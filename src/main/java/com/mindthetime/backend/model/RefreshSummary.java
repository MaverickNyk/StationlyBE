package com.mindthetime.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshSummary {
    private String mode;
    private LocalDateTime timestamp;
    private String status;
    private Integer arrivalsReceived;
    private Integer cacheKeysCreated;
    private Integer fcmTopicsPublished;
    private Long ttlSeconds;
    private Long processingTimeMs;
    private String message;
}
