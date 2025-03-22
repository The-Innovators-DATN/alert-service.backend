package com.aquatech.alert.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlertCondition {
    @JsonProperty("metric_id")
    private Integer metricId;

    @JsonProperty("metric_name")
    private String metricName;

    private Double threshold;

    @JsonProperty("threshold_min")
    private Double thresholdMin;

    @JsonProperty("threshold_max")
    private Double thresholdMax;

    private String operator;

    private Integer severity;
}
