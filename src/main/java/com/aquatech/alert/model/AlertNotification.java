package com.aquatech.alert.model;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Setter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlertNotification {
//    private UUID notificationId;
    private UUID alertId;
    private String alertName;
    private Integer stationId;
    private Integer userId;
    private String message;
    private Integer severity;
    private LocalDateTime timestamp;
    private String status;

    // Trigger Condition
    private Integer triggeredMetricId;
    private String triggeredMetricName;
    private String triggeredOperator;
    private Double triggeredThreshold;
    private Double triggeredThresholdMin;
    private Double triggeredThresholdMax;
    private Double triggeredValue;
}