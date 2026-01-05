package com.stationly.backend.util;

import java.util.Map;

public class TflUtils {

    // Mapping of transport mode to its corresponding stopType for station filtering
    public static final Map<String, String> MODE_STOPTYPE_MAP = Map.of(
            "bus", "NaptanPublicBusCoachTram",
            "tube", "NaptanMetroStation",
            "underground", "NaptanMetroStation",
            "overground", "NaptanRailStation",
            "elizabeth-line", "NaptanRailStation",
            "dlr", "NaptanMetroStation",
            "national-rail", "NaptanRailStation",
            "tram", "NaptanPublicBusCoachTram",
            "river-bus", "NaptanFerryPort",
            "cable-car", "NaptanCableCarStation");

    public static String getExpectedStopType(String mode) {
        return MODE_STOPTYPE_MAP.get(mode.toLowerCase());
    }
}
