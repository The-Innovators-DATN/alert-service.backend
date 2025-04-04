package com.aquatech.alert.service;

import com.aquatech.alert.entity.Alert;
import com.aquatech.alert.model.AlertCondition;
import com.aquatech.alert.utils.CacheUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class CacheService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * This method is used to set the cache for an alert.
     * It creates Redis entries for each condition in the alert.
     *
     * @param alert The alert object to be cached.
     */
    public void setCache(Alert alert) {
        if (alert == null || alert.getStationId() == null || alert.getUid() == null) {
            log.warn("Cannot cache alert with null values");
            return;
        }

        try {
            if (alert.getConditions() == null || alert.getConditions().isEmpty()) {
                log.debug("No conditions to cache for alert {}", alert.getUid());
                return;
            }

            for (AlertCondition condition : alert.getConditions()) {
                if (condition.getMetricId() == null) {
                    log.warn("Skipping condition with null metricId in alert: {}", alert.getUid());
                    continue;
                }

                // Build the Redis key
                String cacheKey = CacheUtils.buildCacheKey(
                        alert.getStationId(),
                        alert.getUid().toString(),
                        condition.getMetricId(),
                        condition.getUid() != null ? condition.getUid().toString() : "null"
                );

                Map<String, Object> valueMap = getValueMap(alert, condition);

                // Convert to JSON and store in Redis with expiration
                String valueJson = objectMapper.writeValueAsString(valueMap);
                redisTemplate.opsForValue().set(cacheKey, valueJson);

                log.debug("Cached alert condition: {}", cacheKey);
            }
        } catch (Exception e) {
            log.error("Error setting cache for alert {}: {}", alert.getUid(), e.getMessage());
        }
    }

    private static Map<String, Object> getValueMap(Alert alert, AlertCondition condition) {
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("alert_id", alert.getUid());
        valueMap.put("alert_name", alert.getName());
        valueMap.put("station_id", alert.getStationId());
        valueMap.put("user_id", alert.getUserId());
        valueMap.put("severity", condition.getSeverity());
        valueMap.put("operator", condition.getOperator());
        valueMap.put("threshold", condition.getThreshold());
        valueMap.put("threshold_min", condition.getThresholdMin());
        valueMap.put("threshold_max", condition.getThresholdMax());
        valueMap.put("message", alert.getMessage());
        valueMap.put("condition_uid", condition.getUid());
        return valueMap;
    }

    /**
     * This method is used to remove the cache for an alert.
     * It removes all Redis entries associated with the alert.
     *
     * @param alert The alert object to be removed from cache.
     */
    public void removeCache(Alert alert) {
        if (alert == null || alert.getStationId() == null || alert.getUid() == null) {
            log.warn("Cannot remove cache for alert with null values");
            return;
        }

        try {
            // If conditions are available, use them to build the exact keys
            if (alert.getConditions() != null && !alert.getConditions().isEmpty()) {
                for (AlertCondition condition : alert.getConditions()) {
                    if (condition.getMetricId() == null) continue;

                    String cacheKey = CacheUtils.buildCacheKey(
                            alert.getStationId(),
                            alert.getUid().toString(),
                            condition.getMetricId(),
                            condition.getUid() != null ? condition.getUid().toString() : "null"
                    );

                    redisTemplate.delete(cacheKey);
                    log.debug("Removed cache for alert condition: {}", cacheKey);
                }
            } else {
                // If conditions aren't available, use a pattern to find and delete all related keys
                String keyPattern = CacheUtils.getStationPrefix() +
                        alert.getStationId() +
                        CacheUtils.getAlertSuffix() + ":" +
                        alert.getUid() + "*";

                // Use scan to find all keys matching the pattern
                redisTemplate.keys(keyPattern).forEach(key -> {
                    redisTemplate.delete(key);
                    log.debug("Removed cache using pattern: {}", key);
                });
            }
        } catch (Exception e) {
            log.error("Error removing cache for alert {}: {}", alert.getUid(), e.getMessage());
        }
    }
}