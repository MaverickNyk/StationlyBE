package com.mindthetime.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArrivalPrediction {
    private String id;
    private String naptanId; // Station ID
    private String stationName;
    private String lineId;
    private String lineName;
    private String platformName;
    private String direction;
    private String destinationName;

    @JsonProperty("destinationId")
    @com.fasterxml.jackson.annotation.JsonAlias({ "destinationId", "destinationNaptanId" })
    private String destinationNaptanId;

    private String modeName;
    private Integer timeToStation; // seconds
    private String timeToLive; // ISO-8601 timestamp string
    private ZonedDateTime expectedArrival;
    private String currentLocation;
    private String towards;
}
