package com.aquatech.alert.service;

import com.aquatech.alert.entity.Alert;
import com.aquatech.alert.model.AlertCondition;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class StationAlertService {

    private final static String STATION_PREFIX = "station:";
    private final static String ALERT_SUFFIX = ":alert";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private AlertService alertService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    @Transactional
    public void loadAlertsToRedis() {
        try {
            log.info("Loading alerts to Redis...");
            List<Alert> ls_alerts = alertService.getAllAlerts();
            clearExistingStationIndices();
            for (Alert alert : ls_alerts) {
                Integer stationId = alert.getStationId();
                String alertId = alert.getId().toString();
                String alertJson = objectMapper.writeValueAsString(alert);

                String alertKey = String.format("%s%d%s:%s", STATION_PREFIX, stationId, ALERT_SUFFIX, alertId);
                redisTemplate.opsForValue().set(alertKey, alertJson);

                String stationKey = String.format("%s%d%s", STATION_PREFIX, stationId, ALERT_SUFFIX);
                redisTemplate.opsForSet().add(stationKey, alertId);
            }
        } catch (Exception e) {
            log.error("Error loading alerts to Redis", e);
        }
    }

    private boolean isConditionViolated(AlertCondition alertCondition, Double value) {
        String operator = alertCondition.getOperator();

        return switch (operator) {
            case "eq" -> value.equals(alertCondition.getThreshold());
            case "ne" -> !value.equals(alertCondition.getThreshold());
            case "gt" -> value > alertCondition.getThreshold();
            case "lt" -> value < alertCondition.getThreshold();
            case "gte" -> value >= alertCondition.getThreshold();
            case "lte" -> value <= alertCondition.getThreshold();
            case "between" -> {
                Double min = alertCondition.getThresholdMin();
                Double max = alertCondition.getThresholdMax();
                yield (min != null && value >= min) && (max != null && value <= max);
            }
            case "not_between" -> {
                Double min = alertCondition.getThresholdMin();
                Double max = alertCondition.getThresholdMax();
                yield (min != null && value < min) || (max != null && value > max);
            }
            default -> {
                log.error("Invalid operator: {}", operator);
                yield false;
            }
        };
    }

    private void clearExistingStationIndices() {
        Set<String> stationKeys = redisTemplate.keys(STATION_PREFIX + "*" + ALERT_SUFFIX);
        Set<String> alertKeys = redisTemplate.keys(STATION_PREFIX + "*" + ALERT_SUFFIX + ":*");
        if (!stationKeys.isEmpty()) {
            redisTemplate.delete(stationKeys);
        }
        if (!alertKeys.isEmpty()) {
            redisTemplate.delete(alertKeys);
        }
    }
}
