package com.aquatech.alert.utils;

/**
 * Utility class for Redis cache key management.
 * Provides methods to build standardized cache keys for the alert system.
 */
public class CacheUtils {
    private static final String STATION_PREFIX = "station:";
    private static final String ALERT_SUFFIX = ":alert";
    private static final String METRIC_SUFFIX = ":metric";
    private static final String CONDITION_SUFFIX = ":condition";

    /**
     * Builds a cache key for storing alert condition data.
     * Format: station:{stationId}:alert:{alertId}:metric:{metricId}:condition:{conditionId}
     *
     * @param stationId   The station ID
     * @param alertId     The alert ID
     * @param metricId    The metric ID
     * @param conditionId The condition ID
     * @return A formatted cache key
     */
    public static String buildCacheKey(
            Integer stationId,
            String alertId,
            Integer metricId,
            String conditionId
    ) {
        return STATION_PREFIX + stationId + ALERT_SUFFIX + ":" + alertId +
                METRIC_SUFFIX + ":" + metricId + CONDITION_SUFFIX + ":" + conditionId;
    }

    /**
     * Gets the key pattern for scanning operations.
     *
     * @return Pattern string for Redis scan operations
     */
    public static String getCacheKeyPattern() {
        return STATION_PREFIX + "*" + ALERT_SUFFIX + ":*" +
                METRIC_SUFFIX + ":*" + CONDITION_SUFFIX + ":*";
    }

    /**
     * Gets the station prefix for key construction.
     *
     * @return The station prefix string
     */
    public static String getStationPrefix() {
        return STATION_PREFIX;
    }

    /**
     * Gets the alert suffix for key construction.
     *
     * @return The alert suffix string
     */
    public static String getAlertSuffix() {
        return ALERT_SUFFIX;
    }

    /**
     * Gets the metric suffix for key construction.
     *
     * @return The metric suffix string
     */
    public static String getMetricSuffix() {
        return METRIC_SUFFIX;
    }

    /**
     * Gets the condition suffix for key construction.
     *
     * @return The condition suffix string
     */
    public static String getConditionSuffix() {
        return CONDITION_SUFFIX;
    }
}