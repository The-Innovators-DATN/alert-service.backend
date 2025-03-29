package com.aquatech.alert.service;

import com.aquatech.alert.entity.Alert;
import com.aquatech.alert.utils.CacheUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CacheService {
    private static final String STATION_PREFIX = "station:";
    private static final String ALERT_SUFFIX = ":alert";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * This method is used to set the cache for an alert.
     *
     * @param alert The alert object to be cached.
     */
    public void setCache(Alert alert) {
        try {
            Integer stationId = alert.getStationId();
            String alertId = alert.getId().toString();
            String stationKey = CacheUtils.buildStationKey(stationId);
            String alertKey = CacheUtils.buildAlertKey(stationId, alertId);
            String alertJson = objectMapper.writeValueAsString(alert);

            redisTemplate.opsForValue().set(alertKey, alertJson);
            redisTemplate.opsForSet().add(stationKey, alertKey);
        } catch (Exception e) {
            log.error("Error setting cache for alert", e);
        }
    }

    public void removeCache(Alert alert) {
        try {
            Integer stationId = alert.getStationId();
            String alertId = alert.getId().toString();
            String stationKey = CacheUtils.buildStationKey(stationId);
            String alertKey = CacheUtils.buildAlertKey(stationId, alertId);

            redisTemplate.opsForValue().getOperations().delete(alertKey);
            redisTemplate.opsForSet().remove(stationKey, alertKey);
        } catch (Exception e) {
            log.error("Error removing cache for alert", e);
        }
    }
}
