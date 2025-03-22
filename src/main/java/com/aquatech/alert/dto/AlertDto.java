package com.aquatech.alert.dto;

import com.aquatech.alert.model.AlertCondition;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AlertDto {
    private String name;

    private Integer stationId;

    private String message;

    public List<AlertCondition> conditions;

    private Integer silenced;

    private String status;
}
