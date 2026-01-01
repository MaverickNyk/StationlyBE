package com.stationly.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("id")
    private String lineId;

    @JsonProperty("name")
    private String lineName;

    @JsonProperty("dirs")
    private Map<String, DirectionPredictions> directions; // direction -> predictions
}
