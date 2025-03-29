package com.aquatech.alert.utils;

import com.aquatech.alert.model.AlertCondition;

public class AlertConditionUtils {
    public static boolean checkAlertCondition(AlertCondition alertCondition, Double value) {
        if (alertCondition == null || value == null) {
            return false;
        }

        String operator = alertCondition.getOperator();
        if (operator == null) {
            return false;
        }
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
            default -> false;
        };
    }
}
