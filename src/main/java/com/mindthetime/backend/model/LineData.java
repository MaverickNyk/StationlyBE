package com.mindthetime.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LineData {
    private String lineId;
    private String lineName;
    private Map<String, DirectionPredictions> directions; // direction -> predictions
}
