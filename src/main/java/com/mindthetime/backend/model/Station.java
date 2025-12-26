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
public class Station {
    private String stationId; // naptanId
    private String stationName; // stationName
    private Map<String, LineData> lines; // lineId -> LineData
}
