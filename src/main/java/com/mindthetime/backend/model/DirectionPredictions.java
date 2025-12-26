package com.mindthetime.backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DirectionPredictions {
    private String stationId;
    private String stationName;
    private String lineId;
    private String lineName;
    private String mode;
    private String direction;
    private String lastUpdatedTime;
    private List<PredictionItem> predictions;
}
