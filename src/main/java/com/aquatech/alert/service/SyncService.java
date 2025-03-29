package com.aquatech.alert.service;

import com.aquatech.alert.entity.Alert;
import com.aquatech.alert.utils.CacheUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

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

    @Scheduled(fixedRate = 5000)
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
            Map<String, Set<String>> stationToAlertIdsMap = updateActiveAlerts(alerts);
            cleanupInactiveAlerts(stationToAlertIdsMap.keySet(), stationToAlertIdsMap);
        } catch (Exception e) {
            log.error("Error in sync process", e);
        }
    }

    private Map<String, Set<String>> updateActiveAlerts(List<Alert> alerts) throws JsonProcessingException {
        Map<String, Set<String>> stationToAlertIdsMap = new HashMap<>();

        // Pre-process alerts to prepare data
        List<Map<String, Object>> batchOperations = new ArrayList<>();

        for (Alert alert : alerts) {
            Integer stationId = alert.getStationId();
            String alertId = alert.getId().toString();
            String stationKey = CacheUtils.buildStationKey(stationId);
            String alertKey = CacheUtils.buildAlertKey(stationId, alertId);
            String alertJson = objectMapper.writeValueAsString(alert);

            // Add to operations list
            Map<String, Object> operation = new HashMap<>();
            operation.put("alertKey", alertKey);
            operation.put("alertJson", alertJson);
            operation.put("stationKey", stationKey);
            operation.put("alertId", alertId);
            batchOperations.add(operation);

            // Track active alerts
            stationToAlertIdsMap
                    .computeIfAbsent(stationKey, k -> new HashSet<>())
                    .add(alertId);
        }

        // Use a loop for batch processing (100 items per batch)
        int batchSize = 100;
        for (int i = 0; i < batchOperations.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, batchOperations.size());
            List<Map<String, Object>> batch = batchOperations.subList(i, endIndex);

            processBatch(batch);
        }

        return stationToAlertIdsMap;
    }

    private void processBatch(List<Map<String, Object>> batch) {
        // Process alerts in batches
        batch.forEach(op -> {
            redisTemplate.opsForValue().set((String) op.get("alertKey"), op.get("alertJson"));
            redisTemplate.opsForSet().add((String) op.get("stationKey"), op.get("alertId"));
        });
    }

    private void cleanupInactiveAlerts(Set<String> stationKeys, Map<String, Set<String>> stationToAlertIdsMap) {
        Map<String, List<Object>> removalMap = new HashMap<>();
        List<String> keysToDelete = new ArrayList<>();

        // Process one station at a time to avoid memory issues with large datasets
        for (String stationKey : stationKeys) {
            Set<Object> currentAlertIds = redisTemplate.opsForSet().members(stationKey);
            if (currentAlertIds == null || currentAlertIds.isEmpty()) {
                continue;
            }

            Set<String> activeAlertIds = stationToAlertIdsMap.getOrDefault(stationKey, Collections.emptySet());
            List<Object> itemsToRemove = new ArrayList<>();

            // Find inactive alerts
            for (Object alertIdObj : currentAlertIds) {
                String alertId = alertIdObj.toString();
                if (!activeAlertIds.contains(alertId)) {
                    keysToDelete.add(String.format("%s:%s", stationKey, alertId));
                    itemsToRemove.add(alertId);
                }
            }

            if (!itemsToRemove.isEmpty()) {
                removalMap.put(stationKey, itemsToRemove);
            }
        }

        // Perform batch operations
        performBatchRemovals(removalMap, keysToDelete);
    }

    private void performBatchRemovals(Map<String, List<Object>> removalMap, List<String> keysToDelete) {
        // Remove members from sets
        removalMap.forEach((stationKey, itemsToRemove) -> {
            if (!itemsToRemove.isEmpty()) {
                redisTemplate.opsForSet().remove(stationKey, itemsToRemove.toArray());
                log.debug("Removed {} alerts from station set {}", itemsToRemove.size(), stationKey);
            }
        });

        // Delete keys in batches of 100
        if (!keysToDelete.isEmpty()) {
            int batchSize = 100;
            for (int i = 0; i < keysToDelete.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, keysToDelete.size());
                List<String> batch = keysToDelete.subList(i, endIndex);
                redisTemplate.delete(batch);
            }

            log.debug("Bulk deleted {} inactive alert keys", keysToDelete.size());
        }
    }


}