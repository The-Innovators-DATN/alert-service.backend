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

@Service
@Slf4j
public class KafkaService {
    @Value("${kafka.message-topic}")
    private String alertNotificationTopic;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @KafkaListener(topics = "${kafka.alert-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeSensorData(String message) {
        log.info("Received message: {}", message);

        try {
            // Convert the JSON string to SensorData object
            SensorData sensorData = objectMapper.readValue(message, SensorData.class);

            // Log the converted object
            log.info("Converted to SensorData: metric={}, value={}, station={}, datetime={}, unit={}",
                    sensorData.getMetric(), sensorData.getValue(), sensorData.getStationId(),
                    sensorData.getDatetime(), sensorData.getUnit());

            processSensorData(sensorData);

        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage(), e);
        }
    }

    /**
     * Send alert notification to Kafka
     */
    public void sendAlertNotification(AlertNotification notification) {
        try {
            String message = objectMapper.writeValueAsString(notification);
            kafkaTemplate.send(alertNotificationTopic, message);
            log.info("Alert notification sent to topic: {}", alertNotificationTopic);
        } catch (Exception e) {
            log.error("Error sending alert notification: {}", e.getMessage(), e);
        }
    }


    private void processSensorData(SensorData sensorData) {
        String keyPattern = CacheUtils.buildCacheKey(
                sensorData.getStationId().toString(),
                "*",
                sensorData.getSensorId().toString(),
                "*"
        );

        Set<String> matchingKeys = redisTemplate.keys(keyPattern);

        if (matchingKeys.isEmpty()) {
            log.info("No alert conditions found for stationId: {} and sensorId: {}",
                    sensorData.getStationId(), sensorData.getSensorId());
            return;
        }

        for (String key: matchingKeys) {
            try {
                String jsonValue = (String) redisTemplate.opsForValue().get(key);
                if (jsonValue == null) {
                    continue;
                }

                Map<String, Object> conditionData = objectMapper.readValue(jsonValue, new TypeReference<Map<String, Object>>() {});

                String alertId = (String) conditionData.get(RedisConstant.KEY_ALERT_ID);
                String alertName = (String) conditionData.get(RedisConstant.KEY_ALERT_NAME);
                Integer userId = (Integer) conditionData.get(RedisConstant.KEY_USER_ID);
                String message = (String) conditionData.get(RedisConstant.KEY_MESSAGE);
                String conditionUid = (String) conditionData.get(RedisConstant.KEY_CONDITION_UID);
                Integer severity = (Integer) conditionData.get(RedisConstant.KEY_SEVERITY);
                String operator = (String) conditionData.get(RedisConstant.KEY_OPERATOR);
                Double threshold = parseDoubleValue(conditionData.get(RedisConstant.KEY_THRESHOLD));
                Double thresholdMin = parseDoubleValue(conditionData.get(RedisConstant.KEY_THRESHOLD_MIN));
                Double thresholdMax = parseDoubleValue(conditionData.get(RedisConstant.KEY_THRESHOLD_MAX));

                Double currentValue = sensorData.getValue();

                boolean conditionMet = evaluateCondition(
                        operator, currentValue, threshold, thresholdMin, thresholdMax
                );

                Set<String> trackingKeyCondition = redisTemplate.keys(RedisConstant.TRACKING_PREFIX + conditionUid);

                if (conditionMet && trackingKeyCondition.isEmpty()) {
                    log.info("Alert condition met for key: {}", key);
                    triggerAlert(userId, sensorData, severity, message, alertName, alertId, operator,
                            threshold, thresholdMin, thresholdMax, currentValue, AlertConstant.STATUS_ALERT);
                    redisTemplate.opsForValue().set(RedisConstant.TRACKING_PREFIX + conditionUid, "true", Duration.ofHours(RedisConstant.TRACKING_DURATION_HOURS));
                } else if (!conditionMet && !trackingKeyCondition.isEmpty()) {
                    log.info("Alert condition resolved {}", key);
                    message = "Alert condition resolved for " + sensorData.getMetric() +
                            " with value: " + currentValue;
                    triggerAlert(userId, sensorData, severity, message, alertName, alertId, operator,
                            threshold, thresholdMin, thresholdMax, currentValue, AlertConstant.STATUS_RESOLVED);
                    redisTemplate.delete(RedisConstant.TRACKING_PREFIX + conditionUid);
                }
            } catch (Exception e) {
                log.error("Error processing key {}: {}", key, e.getMessage(), e);
            }
        }

    }

    private Double parseDoubleValue(Object value) {
        switch (value) {
            case null -> {
                return null;
            }
            case Number number -> {
                return number.doubleValue();
            }
            case String s -> {
                try {
                    return Double.parseDouble((String) value);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            default -> {
            }
        }

        return null;
    }

    private boolean evaluateCondition(
            String operator,
            Double currentValue,
            Double threshold,
            Double thresholdMin,
            Double thresholdMax
    ) {
        if (currentValue == null || operator == null) {
            log.warn("Current value or operator is null");
            return false;
        }

        return switch (operator.toUpperCase()) {
            case OperatorConstant.EQUAL ->
                    threshold != null &&
                            Math.abs(currentValue - threshold) < OperatorConstant.THRESHOLD_PRECISION;

            case OperatorConstant.NOT_EQUAL ->
                    threshold != null &&
                            Math.abs(currentValue - threshold) >= OperatorConstant.THRESHOLD_PRECISION;

            case OperatorConstant.GREATER_THAN ->
                    threshold != null &&
                            currentValue > threshold;

            case OperatorConstant.GREATER_THAN_EQUAL ->
                    threshold != null &&
                            currentValue >= threshold;

            case OperatorConstant.LESS_THAN ->
                    threshold != null &&
                            currentValue < threshold;

            case OperatorConstant.LESS_THAN_EQUAL ->
                    threshold != null &&
                            currentValue <= threshold;

            case OperatorConstant.RANGE ->
                    thresholdMin != null && thresholdMax != null &&
                            currentValue >= thresholdMin && currentValue <= thresholdMax;

            case OperatorConstant.OUTSIDE_RANGE ->
                    thresholdMin != null && thresholdMax != null &&
                            (currentValue < thresholdMin || currentValue > thresholdMax);

            default -> {
                log.warn("Unknown operator: {}", operator);
                yield false;
            }
        };
    }

    /**
     * Trigger an alert based on the condition evaluation
     */
    private void triggerAlert(
            Integer userId,
            SensorData sensorData,
            Integer severity,
            String message,
            String alertName,
            String alertId,
            String operator,
            Double threshold,
            Double thresholdMin,
            Double thresholdMax,
            Double currentValue,
            String status
    ) {
        // Implement alert notification logic
        // This could send an email, SMS, push notification, etc.
        AlertNotification notification = new AlertNotification();
        notification.setAlertId(UUID.fromString(alertId));
        notification.setAlertName(alertName);
        notification.setStationId(sensorData.getStationId());
        notification.setUserId(userId);
        notification.setMessage(message);
        notification.setSeverity(severity);
        notification.setTimestamp(LocalDateTime.now());
        notification.setTriggeredMetricId(sensorData.getSensorId());
        notification.setTriggeredMetricName(sensorData.getMetric());
        notification.setTriggeredOperator(operator);
        notification.setTriggeredThreshold(threshold);
        notification.setTriggeredThresholdMin(thresholdMin);
        notification.setTriggeredThresholdMax(thresholdMax);
        notification.setTriggeredValue(currentValue);
        notification.setStatus(status);

        // Send the notification
        sendAlertNotification(notification);

        log.info("Alert triggered for user {} on station {}, metric {}, value {}, severity {}",
                userId, sensorData.getStationId(), sensorData.getMetric(),
                sensorData.getValue(), severity);
    }
}
