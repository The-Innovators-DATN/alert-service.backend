package com.aquatech.alert.service;

import com.aquatech.alert.entity.Alert;
import com.aquatech.alert.model.AlertCondition;
import com.aquatech.alert.utils.CacheUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SyncService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AlertService alertService;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void loadAlertsToRedis() {
        try {
            log.info("Loading alerts to Redis on startup...");
            syncAlertsToRedis();
        } catch (Exception e) {
            log.error("Error loading alerts to Redis", e);
        }
    }

    @Scheduled(fixedRate = 60*60*1000) // 1 hour
    @Async("syncExecutor")
    public void scheduledSync() {
        try {
            log.info("Syncing alerts to Redis...");
            syncAlertsToRedis();
        } catch (Exception e) {
            log.error("Error syncing alerts to Redis", e);
        }
    }

    public void syncAlertsToRedis() {
        List<Alert> alerts = alertService.getAllActiveAlerts();

        if (alerts == null || alerts.isEmpty()) {
            log.info("No alerts found to sync to Redis");
            return;
        }

        try {
            Set<String> activeKeys = updateActiveAlerts(alerts);

            cleanupInactiveAlerts(activeKeys);
        } catch (Exception e) {
            log.error("Error in sync process", e);
        }
    }

    private Set<String> updateActiveAlerts(List<Alert> alerts) {
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
                            alert.getStationId(),
                            alert.getUid().toString(),
                            condition.getMetricId(),
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

    private static Map<String, Object> getValueKey(Alert alert, AlertCondition condition) {
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("user_id", alert.getUserId());
        valueMap.put("alert_id", alert.getUid());
        valueMap.put("alert_name", alert.getName());
        valueMap.put("severity", condition.getSeverity());
        valueMap.put("condition_uid", condition.getUid());
        valueMap.put("operator", condition.getOperator());
        valueMap.put("threshold", condition.getThreshold());
        valueMap.put("threshold_min", condition.getThresholdMin());
        valueMap.put("threshold_max", condition.getThresholdMax());
        valueMap.put("message", alert.getMessage());
        return valueMap;
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

    private void cleanupInactiveAlerts(Set<String> activeKeys) {
        try {
            // Get all keys matching the pattern
            Set<String> allKeys = scanKeys(CacheUtils.getCacheKeyPattern());

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