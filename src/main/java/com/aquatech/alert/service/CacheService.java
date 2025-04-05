package com.aquatech.alert.service;

import com.aquatech.alert.entity.Alert;
import com.aquatech.alert.model.AlertCondition;
import com.aquatech.alert.utils.CacheUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.aquatech.alert.utils.CacheUtils.getValueKey;

@Service
@Slf4j
public class CacheService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

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
                        alert.getStationId().toString(),
                        alert.getUid().toString(),
                        condition.getMetricId().toString(),
                        condition.getUid().toString()
                );

                Map<String, Object> valueMap = getValueKey(alert, condition);

                // Convert to JSON and store in Redis with expiration
                String valueJson = objectMapper.writeValueAsString(valueMap);
                redisTemplate.opsForValue().set(cacheKey, valueJson);

                log.debug("Cached alert condition: {}", cacheKey);
            }
        } catch (Exception e) {
            log.error("Error setting cache for alert {}: {}", alert.getUid(), e.getMessage());
        }
    }

    public void removeCache(Alert alert) {
        if (alert == null || alert.getStationId() == null || alert.getUid() == null) {
            log.warn("Cannot remove cache for alert with null values");
            return;
        }

        try {
            String keyPattern = CacheUtils.buildCacheKey(
                    alert.getStationId().toString(),
                    alert.getUid().toString(),
                    "*",
                    "*"
            );
            // Use scan to find all keys matching the pattern
            redisTemplate.keys(keyPattern).forEach(key -> {
                redisTemplate.delete(key);
                log.debug("Removed cache using pattern: {}", key);
            });
        } catch (Exception e) {
            log.error("Error removing cache for alert {}: {}", alert.getUid(), e.getMessage());
        }

    }

    public Set<String> updateActiveAlerts(List<Alert> alerts) {
        Set<String> activeKeys = new HashSet<>();
        List<Map<String, Object>> batchOperations = new ArrayList<>();

        for (Alert alert : alerts) {
            if (alert.getStationId() == null || alert.getUid() == null || alert.getConditions() == null) {
                log.warn("Skipping alert with null stationId, id, or conditions: {}", alert.getUid());
                continue;
            }

            try {
                for (AlertCondition condition : alert.getConditions()) {
                    if (condition.getUid() == null) {
                        log.warn("Skipping condition with null UID for alert: {}", alert.getUid());
                        continue;
                    }

                    String cacheKey = CacheUtils.buildCacheKey(
                            alert.getStationId().toString(),
                            alert.getUid().toString(),
                            condition.getMetricId().toString(),
                            condition.getUid().toString()
                    );

                    Map<String, Object> valueKey = getValueKey(alert, condition);

                    Map<String, Object> operation = new HashMap<>();
                    operation.put("key", cacheKey);
                    operation.put("value", objectMapper.writeValueAsString(valueKey));
                    batchOperations.add(operation);

                    activeKeys.add(cacheKey);
                }
            } catch (Exception e) {
                log.error("Error processing alert {}: {}", alert.getUid(), e.getMessage());
            }
        }

        // Use a loop for batch processing (100 items per batch)
        int batchSize = 100;
        for (int i = 0; i < batchOperations.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, batchOperations.size());
            List<Map<String, Object>> batch = batchOperations.subList(i, endIndex);

            processBatch(batch);
        }

        return activeKeys;
    }

    private void processBatch(List<Map<String, Object>> batch) {
        batch.forEach(op -> {
            try {
                String key = (String) op.get("key");
                Object value = op.get("value");

                // Set value with expiration
                redisTemplate.opsForValue().set(key, value);

                log.debug("Saved key: {}", key);
            } catch (Exception e) {
                log.error("Error processing batch item: {}", e.getMessage());
            }
        });
    }

    public void cleanupInactiveAlerts(Set<String> activeKeys) {
        try {
            // Get all keys matching the pattern
            Set<String> allKeys = scanKeys(CacheUtils.buildCacheKey(
                    "*",
                    "*",
                    "*",
                    "*"
            ));

            // Find keys to delete (keys in Redis but not in active list)
            Set<String> keysToDelete = allKeys.stream()
                    .filter(key -> !activeKeys.contains(key))
                    .collect(Collectors.toSet());

            if (!keysToDelete.isEmpty()) {
                // Delete in batches of 100
                List<String> keysList = new ArrayList<>(keysToDelete);
                int batchSize = 100;

                for (int i = 0; i < keysList.size(); i += batchSize) {
                    int endIndex = Math.min(i + batchSize, keysList.size());
                    List<String> batch = keysList.subList(i, endIndex);
                    redisTemplate.delete(batch);
                }

                log.info("Cleaned up {} inactive alert keys", keysToDelete.size());
            } else {
                log.info("No inactive keys to clean up");
            }
        } catch (Exception e) {
            log.error("Error during cleanup process: {}", e.getMessage());
        }
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();

        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(pattern).build())) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            log.error("Error scanning Redis keys: {}", e.getMessage());
        }

        return keys;
    }
}