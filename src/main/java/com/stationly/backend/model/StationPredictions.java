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
public class StationPredictions {
    @JsonProperty("id")
    private String stationId; // naptanId

    @JsonProperty("name")
    private String stationName; // stationName

    @JsonProperty("lut")
    private String lastUpdatedTime;

    private Map<String, LineData> lines; // lineId -> LineData
}
