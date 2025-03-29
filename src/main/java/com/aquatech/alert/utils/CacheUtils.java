package com.aquatech.alert.utils;

public class CacheUtils {
    private static final String STATION_PREFIX = "station:";
    private static final String ALERT_SUFFIX = ":alert";

    public static String buildStationKey(Integer stationId) {
        return String.format("%s%d%s", STATION_PREFIX, stationId, ALERT_SUFFIX);
    }

    public static String buildAlertKey(Integer stationId, String alertId) {
        return String.format("%s%d%s:%s", STATION_PREFIX, stationId, ALERT_SUFFIX, alertId);
    }
}
