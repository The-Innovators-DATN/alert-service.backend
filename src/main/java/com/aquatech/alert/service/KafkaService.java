package com.aquatech.alert.service;

import com.aquatech.alert.constant.AlertConstant;
import com.aquatech.alert.constant.OperatorConstant;
import com.aquatech.alert.constant.RedisConstant;
import com.aquatech.alert.model.AlertNotification;
import com.aquatech.alert.model.SensorData;
import com.aquatech.alert.utils.CacheUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class KafkaService {

    @Value("${kafka.message-topic}")
    private String alertNotificationTopic;

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private RedisTemplate<String, String> customStringRedisTemplate;
    @Autowired private RedissonClient redissonClient;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    @KafkaListener(
            topics = "${kafka.alert-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeSensorData(String messagePayload) {
        SensorData sensorData = null;
        try {
            sensorData = objectMapper.readValue(messagePayload, SensorData.class);
//            log.debug("[consumeSensorData] stationId={} sensorId={} metric={} value={} unit={} datetime={}",
//                    sensorData.getStationId(), sensorData.getSensorId(), sensorData.getMetric(), sensorData.getValue(), sensorData.getUnit(), sensorData.getDatetime());
            evaluateSensorData(sensorData);
        } catch (Exception ex) {
            log.error("[consumeSensorData] Parse or processing error. payload={}", messagePayload, ex);
        }
    }

    private void evaluateSensorData(SensorData sensorData) {
        String indexKey = CacheUtils.buildIndexKey(sensorData.getStationId(), sensorData.getSensorId());
        Set<String> cacheKeys = customStringRedisTemplate.opsForSet().members(indexKey);
        if (cacheKeys == null || cacheKeys.isEmpty()) {
            log.trace("[evaluateSensorData] No conditions for indexKey={}", indexKey);
            return;
        }

        double currentValue = sensorData.getValue();
        for (String cacheKey : cacheKeys) {
            executorService.submit(() -> processCacheKey(cacheKey, currentValue, sensorData));
        }
//
//        log.info("[evaluateSensorData] Submitted {}/{} cacheKeys for async processing for indexKey={}",
//                cacheKeys.size(), cacheKeys.size(), indexKey);
    }

    private void processCacheKey(String cacheKey, double currentValue, SensorData sensorData) {
        try {
            String jsonValue = customStringRedisTemplate.opsForValue().get(cacheKey);

            if (jsonValue == null) {
                return;
            }

            Map<String, Object> conditionMap = objectMapper.readValue(
                    jsonValue, new TypeReference<>() {}
            );
            String conditionUid = (String) conditionMap.get(RedisConstant.KEY_CONDITION_UID);

            boolean isMet = evaluateCondition(
                    (String) conditionMap.get(RedisConstant.KEY_OPERATOR),
                    currentValue,
                    toDouble(conditionMap.get(RedisConstant.KEY_THRESHOLD)),
                    toDouble(conditionMap.get(RedisConstant.KEY_THRESHOLD_MIN)),
                    toDouble(conditionMap.get(RedisConstant.KEY_THRESHOLD_MAX))
            );

            RLock trackingLock = redissonClient.getLock("lock:tracking:" + conditionUid);
            boolean locked = false;
            try {
                locked = trackingLock.tryLock(1, 5, TimeUnit.MINUTES);
                if (!locked) {
                    log.warn("[processCacheKey] Could not acquire lock for conditionUid={} within 1s", conditionUid);
                    return;
                }

                String trackingKey = RedisConstant.TRACKING_PREFIX + conditionUid;
                boolean trackingExists = redisTemplate.hasKey(trackingKey);

                if (isMet && !trackingExists) {
                    publishNotification(conditionMap, sensorData, currentValue, AlertConstant.TYPE_ALERT);
                    redisTemplate.opsForValue().set(
                            trackingKey,
                            "1",
                            Duration.ofHours(RedisConstant.TRACKING_DURATION_HOURS)
                    );
                } else if (!isMet && trackingExists) {
                    publishNotification(conditionMap, sensorData, currentValue, AlertConstant.TYPE_RESOLVED);
                    redisTemplate.delete(trackingKey);
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("[processCacheKey] Interrupted while waiting for lock conditionUid={}", conditionUid, ie);
            } catch (Exception e) {
                log.error("[processCacheKey] Error processing cacheKey={}", cacheKey, e);
            } finally {
                if (locked && trackingLock.isHeldByCurrentThread()) {
                    trackingLock.unlock();
                }
            }
        } catch (Exception e) {
                log.error("[processCacheKey] Error processing cacheKey={}", cacheKey, e);
        }
    }

    private void publishNotification(Map<String, Object> conditionMap, SensorData sensorData,
                                     Double currentValue, String messageType) {
        try {
            AlertNotification notification = new AlertNotification();
            notification.setAlertId(UUID.fromString((String) conditionMap.get(RedisConstant.KEY_ALERT_ID)));
            notification.setAlertName((String) conditionMap.get(RedisConstant.KEY_ALERT_NAME));
            notification.setStationId(sensorData.getStationId());
            notification.setUserId((Integer) conditionMap.get(RedisConstant.KEY_USER_ID));
            notification.setMessage((String) conditionMap.get(RedisConstant.KEY_MESSAGE));
            notification.setSeverity((Integer) conditionMap.get(RedisConstant.KEY_SEVERITY));
            notification.setTimestamp(LocalDateTime.now());
            notification.setTypeMessage(messageType);
            notification.setSilenced((Integer) conditionMap.get(RedisConstant.KEY_SILENCED));

            notification.setTriggeredMetricId(sensorData.getSensorId());
            notification.setTriggeredMetricName(sensorData.getMetric());
            notification.setTriggeredOperator((String) conditionMap.get(RedisConstant.KEY_OPERATOR));
            notification.setTriggeredThreshold(toDouble(conditionMap.get(RedisConstant.KEY_THRESHOLD)));
            notification.setTriggeredThresholdMin(toDouble(conditionMap.get(RedisConstant.KEY_THRESHOLD_MIN)));
            notification.setTriggeredThresholdMax(toDouble(conditionMap.get(RedisConstant.KEY_THRESHOLD_MAX)));
            notification.setTriggeredValue(currentValue);

            String notificationJson = objectMapper.writeValueAsString(notification);
            kafkaTemplate.send(alertNotificationTopic, notificationJson);
//            log.debug("[publishNotification] Sent {} for alertId={} conditionUid={}",
//                    messageType, notification.getAlertId(), notification.getTriggeredMetricId());
        } catch (Exception e) {
            log.error("[publishNotification] Serialization/send error", e);
        }
    }

    private boolean evaluateCondition(String operator, Double value, Double threshold,
                                      Double minThreshold, Double maxThreshold) {
        if (value == null || operator == null) return false;
        return switch (operator.toUpperCase()) {
            case OperatorConstant.EQUAL              -> threshold != null && Math.abs(value - threshold) < OperatorConstant.THRESHOLD_PRECISION;
            case OperatorConstant.NOT_EQUAL          -> threshold != null && Math.abs(value - threshold) >= OperatorConstant.THRESHOLD_PRECISION;
            case OperatorConstant.GREATER_THAN       -> threshold != null && value > threshold;
            case OperatorConstant.GREATER_THAN_EQUAL -> threshold != null && value >= threshold;
            case OperatorConstant.LESS_THAN          -> threshold != null && value < threshold;
            case OperatorConstant.LESS_THAN_EQUAL    -> threshold != null && value <= threshold;
            case OperatorConstant.RANGE              -> minThreshold != null && maxThreshold != null && value >= minThreshold && value <= maxThreshold;
            case OperatorConstant.OUTSIDE_RANGE      -> minThreshold != null && maxThreshold != null && (value < minThreshold || value > maxThreshold);
            default -> false;
        };
    }

    private Double toDouble(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number num) return num.doubleValue();
        try { return Double.parseDouble(obj.toString()); } catch (NumberFormatException ignored) { return null; }
    }
}