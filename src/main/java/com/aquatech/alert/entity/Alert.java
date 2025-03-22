package com.aquatech.alert.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.aquatech.alert.entity.converter.AlertConditionListConverter;
import com.aquatech.alert.model.AlertCondition;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "alert")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "station_id", nullable = false)
    private Integer stationId;

    @Column(name = "message", nullable = false, length = 255)
    private String message;

    @Column(name = "silenced", nullable = false)
    private Integer silenced;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP")
    private LocalDateTime updatedAt;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "conditions", columnDefinition = "TEXT")
    @Convert(converter = AlertConditionListConverter.class)
    private List<AlertCondition> conditions;
}
