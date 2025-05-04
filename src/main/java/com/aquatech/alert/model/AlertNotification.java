package com.aquatech.alert.model;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Setter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlertNotification {

    @JsonProperty("alert_id")
    private UUID alertId;

    @JsonProperty("alert_name")
    private String alertName;

    @JsonProperty("station_id")
    private Integer stationId;

    @JsonProperty("user_id")
    private Integer userId;

    @JsonProperty("message")
    private String message;

    @JsonProperty("severity")
    private Integer severity;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("type_message")
    private String typeMessage;

    @JsonProperty("silenced")
    private Integer silenced;

    // Trigger Condition
    @JsonProperty("metric_id")
    private Integer triggeredMetricId;

    @JsonProperty("metric_name")
    private String triggeredMetricName;

    @JsonProperty("operator")
    private String triggeredOperator;

    @JsonProperty("threshold")
    private Double triggeredThreshold;

    @JsonProperty("threshold_min")
    private Double triggeredThresholdMin;

    @JsonProperty("threshold_max")
    private Double triggeredThresholdMax;

    @JsonProperty("value")
    private Double triggeredValue;
}
