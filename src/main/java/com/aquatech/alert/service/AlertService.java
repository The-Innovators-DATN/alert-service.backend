package com.aquatech.alert.service;

import com.aquatech.alert.dto.AlertDto;
import com.aquatech.alert.entity.Alert;
import com.aquatech.alert.repository.AlertRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AlertService {
    @Autowired()
    private AlertRepository alertRepository;

    public Alert createAlert(Integer userId, AlertDto alertDto) {
        if (userId == null || alertDto == null) {
            throw new IllegalArgumentException("User ID and alert data must be provided");
        }

        alertRepository.findActiveAlertByUserIdAndStationId(userId, alertDto.getStationId())
                .ifPresent(alert -> {
                    throw new IllegalArgumentException("Alert already exists for this user and station");
                });

        Alert alert = new Alert();
        alert.setUserId(userId);
        alert.setName(alertDto.getName());
        alert.setStationId(alertDto.getStationId());
        alert.setSilenced(alertDto.getSilenced());
        alert.setStatus(alertDto.getStatus());
        alert.setCreatedAt(LocalDateTime.now());
        alert.setUpdatedAt(LocalDateTime.now());
        alert.setMessage(alertDto.getMessage());
        alert.setConditions(alertDto.getConditions());
        return alertRepository.save(alert);
    }

    public List<Alert> getAlertsByUserId(Integer userId) {
        return alertRepository.findByUserId(userId);
    }

    public Alert updateAlert(String alertId, AlertDto alertDto) {
        if (alertId == null || alertDto == null) {
            throw new IllegalArgumentException("Alert ID and alert data must be provided");
        }

        Alert alert = alertRepository.findById(UUID.fromString(alertId))
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));

        alert.setName(alertDto.getName());
        alert.setStationId(alertDto.getStationId());
        alert.setSilenced(alertDto.getSilenced());
        alert.setStatus(alertDto.getStatus());
        alert.setUpdatedAt(LocalDateTime.now());
        alert.setMessage(alertDto.getMessage());
        alert.setConditions(alertDto.getConditions());
        return alertRepository.save(alert);
    }

    public void deleteAlert(String alertId) {
        if (alertId == null) {
            throw new IllegalArgumentException("Alert ID must be provided");
        }
        alertRepository.findById(UUID.fromString(alertId))
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));
        alertRepository.deleteByAlertId(UUID.fromString(alertId));
    }

    public Alert updateAlertStatus(String alertId, String status) {
        if (alertId == null || status == null) {
            throw new IllegalArgumentException("Alert ID and status must be provided");
        }

        Alert alert = alertRepository.findById(UUID.fromString(alertId))
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));

        alert.setStatus(status);
        alert.setUpdatedAt(LocalDateTime.now());
        return alertRepository.save(alert);
    }
}
