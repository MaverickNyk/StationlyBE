package com.mindthetime.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionItem {
    private String destinationName;
    private String destinationNaptanId;
    private String towards;
    private String platformName;
    private String expectedArrival; // ISO-8601 string
}
