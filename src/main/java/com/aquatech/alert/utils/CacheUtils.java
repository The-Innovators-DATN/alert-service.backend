package com.aquatech.alert.utils;

import com.aquatech.alert.constant.RedisConstant;
import com.aquatech.alert.entity.Alert;
import com.aquatech.alert.model.AlertCondition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for Redis cache key management.
 * Provides methods to build standardized cache keys for the alert system.
 */
public class CacheUtils {
    private static final String STATION = "station";
    private static final String ALERT = "alert";
    private static final String METRIC = "metric";
    private static final String CONDITION = "condition";

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
            String stationId,
            String alertId,
            String metricId,
            String conditionId
    ) {
        return STATION + ":" + stationId + ":" +
                ALERT + ":" + alertId + ":" +
                METRIC + ":" + metricId + ":" +
                CONDITION + ":" + conditionId;
    }

    public static Map<String, Object> getValueKey(Alert alert, AlertCondition condition) {
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put(RedisConstant.KEY_ALERT_ID, alert.getUid());
        valueMap.put(RedisConstant.KEY_ALERT_NAME, alert.getName());
        valueMap.put(RedisConstant.KEY_USER_ID, alert.getUserId());
        valueMap.put(RedisConstant.KEY_MESSAGE, alert.getMessage());

        valueMap.put(RedisConstant.KEY_CONDITION_UID, condition.getUid());
        valueMap.put(RedisConstant.KEY_SEVERITY, condition.getSeverity());
        valueMap.put(RedisConstant.KEY_OPERATOR, condition.getOperator());
        valueMap.put(RedisConstant.KEY_THRESHOLD, condition.getThreshold());
        valueMap.put(RedisConstant.KEY_THRESHOLD_MIN, condition.getThresholdMin());
        valueMap.put(RedisConstant.KEY_THRESHOLD_MAX, condition.getThresholdMax());
        valueMap.put(RedisConstant.KEY_SILENCED, alert.getSilenced());
        return valueMap;
    }

    public static String buildIndexKey(Integer stationId, Integer metricId) {
        return "idx:station:" + stationId + ":metric:" + metricId;
    }
}