package com.mindthetime.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LineRouteResponse {
    private String id;
    private String name;
    private String modeName;
    private List<DirectionInfo> directions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DirectionInfo {
        private String direction; // e.g. inbound, outbound
        private List<String> destinations; // e.g. ["Upminster Underground Station", ...]
    }
}
