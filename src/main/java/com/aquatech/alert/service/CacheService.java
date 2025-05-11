package com.aquatech.alert.service;

import com.aquatech.alert.entity.Alert;
import com.aquatech.alert.model.AlertCondition;
import com.aquatech.alert.utils.CacheUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
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

    /* ------------------------------------------------------------------ */
    /* 1. setCache – write value + create INDEX-SET                       */
    /* ------------------------------------------------------------------ */
    public void setCache(Alert alertEntity) {
        if (alertEntity == null || alertEntity.getStationId() == null || alertEntity.getUid() == null) {
            log.warn("[setCache] Skip – alert or identifiers are null. alert={}", alertEntity);
            return;
        }

        if (alertEntity.getConditions() == null || alertEntity.getConditions().isEmpty()) {
            log.debug("[setCache] No conditions to cache for alertId={}", alertEntity.getUid());
            return;
        }

        alertEntity.getConditions().forEach(condition -> {
            if (condition.getMetricId() == null) {
                log.warn("[setCache] Skip condition with null metricId. alertId={} conditionUid={} ",
                        alertEntity.getUid(), condition.getUid());
                return;
            }

            String cacheKey = CacheUtils.buildCacheKey(
                    alertEntity.getStationId().toString(),
                    alertEntity.getUid().toString(),
                    condition.getMetricId().toString(),
                    condition.getUid().toString());

            try {
                String payloadJson = objectMapper.writeValueAsString(getValueKey(alertEntity, condition));
                redisTemplate.opsForValue().set(cacheKey, payloadJson);

                String indexKey = CacheUtils.buildIndexKey(alertEntity.getStationId(), condition.getMetricId());
                redisTemplate.opsForSet().add(indexKey, cacheKey);

                log.debug("[setCache] Cached condition. cacheKey={} indexKey={}", cacheKey, indexKey);
            } catch (Exception e) {
                log.error("[setCache] Error while caching condition. cacheKey={}", cacheKey, e);
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* 2. removeCache – delete by INDEX-SET                               */
    /* ------------------------------------------------------------------ */
    public void removeCache(Alert alertEntity) {
        if (alertEntity == null || alertEntity.getStationId() == null || alertEntity.getUid() == null) {
            log.warn("[removeCache] Skip – alert or identifiers are null. alert={}", alertEntity);
            return;
        }

        String indexPattern = "idx:station:" + alertEntity.getStationId() + ":metric:*";
        Set<String> indexKeys = scanKeys(indexPattern);
        log.debug("[removeCache] Found {} indexKeys with pattern {}", indexKeys.size(), indexPattern);

        indexKeys.forEach(indexKey -> {
            Set<Object> members = redisTemplate.opsForSet().members(indexKey);
            if (members == null) return;

            List<String> keysToDelete = members.stream()
                    .map(Object::toString)
                    .filter(key -> key.contains(":alert:" + alertEntity.getUid() + ":"))
                    .toList();

            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
                redisTemplate.opsForSet().remove(indexKey, keysToDelete.toArray());
                log.debug("[removeCache] Removed {} cacheKeys from indexKey={}", keysToDelete.size(), indexKey);
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* 3. updateActiveAlerts – batch write with pipeline                  */
    /* ------------------------------------------------------------------ */
    public Set<String> updateActiveAlerts(List<Alert> alertList) {
        Set<String> activeCacheKeys = new HashSet<>();
        List<Map<String, Object>> batchPayload = new ArrayList<>();

        alertList.forEach(alertEntity -> {
            if (alertEntity.getStationId() == null || alertEntity.getUid() == null) return;
            if (alertEntity.getConditions() == null) return;

            alertEntity.getConditions().forEach(condition -> {
                String cacheKey = CacheUtils.buildCacheKey(
                        alertEntity.getStationId().toString(),
                        alertEntity.getUid().toString(),
                        condition.getMetricId().toString(),
                        condition.getUid().toString());
                try {
                    batchPayload.add(Map.of(
                            "cacheKey", cacheKey,
                            "valueJson", objectMapper.writeValueAsString(getValueKey(alertEntity, condition)),
                            "indexKey", CacheUtils.buildIndexKey(alertEntity.getStationId(), condition.getMetricId())
                    ));
                    activeCacheKeys.add(cacheKey);
                } catch (Exception ex) {
                    log.error("[updateActiveAlerts] Build batch error. cacheKey={}", cacheKey, ex);
                }
            });
        });

        int batchSize = 100;
        for (int i = 0; i < batchPayload.size(); i += batchSize) {
            processBatch(batchPayload.subList(i, Math.min(i + batchSize, batchPayload.size())));
        }
        log.info("[updateActiveAlerts] Synced {} cacheKeys", activeCacheKeys.size());
        return activeCacheKeys;
    }

    /* ------------------------------------------------------------------ */
    /* Private: pipeline write                                            */
    /* ------------------------------------------------------------------ */
    private void processBatch(List<Map<String, Object>> operations) {
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            operations.forEach(op -> {
                byte[] cacheKeyBytes  = redisTemplate.getStringSerializer().serialize((String) op.get("cacheKey"));
                byte[] valueBytes     = redisTemplate.getStringSerializer().serialize((String) op.get("valueJson"));
                byte[] indexKeyBytes  = redisTemplate.getStringSerializer().serialize((String) op.get("indexKey"));

                connection.set(cacheKeyBytes, valueBytes);
                connection.sAdd(indexKeyBytes, cacheKeyBytes);
            });
            return null; // required by RedisCallback
        });
    }

    /* ------------------------------------------------------------------ */
    /* Utility: scanKeys using SCAN (non-blocking)                        */
    /* ------------------------------------------------------------------ */
    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        try (Cursor<byte[]> cursor = redisTemplate.getConnectionFactory()
                .getConnection()
                .scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
            while (cursor.hasNext()) keys.add(new String(cursor.next()));
        } catch (Exception e) {
            log.error("[scanKeys] Error pattern={}", pattern, e);
        }
        return keys;
    }

    /* Cleanup inactive cache keys (optional call by scheduler) */
    public void cleanupInactiveAlerts(Set<String> activeCacheKeys) {
        Set<String> allCacheKeys = scanKeys(CacheUtils.buildCacheKey("*", "*", "*", "*"));
        Set<String> staleKeys = allCacheKeys.stream()
                .filter(key -> !activeCacheKeys.contains(key))
                .collect(Collectors.toSet());
        if (!staleKeys.isEmpty()) {
            redisTemplate.delete(staleKeys);
            log.info("[cleanupInactiveAlerts] Removed {} staleKeys", staleKeys.size());
        }
    }
}
