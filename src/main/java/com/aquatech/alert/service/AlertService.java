package com.aquatech.alert.service;

import com.aquatech.alert.dto.AlertDto;
import com.aquatech.alert.entity.Alert;
import com.aquatech.alert.model.AlertCondition;
import com.aquatech.alert.repository.AlertRepository;
import com.aquatech.alert.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AlertService {
    @Autowired()
    private AlertRepository alertRepository;

    @Autowired()
    private CacheService cacheService;

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
        if (!CommonUtils.isEmptyCollection(alertDto.getConditions())) {
            alert.setConditions(addUidToConditions(alertDto.getConditions()));
        } else {
            alert.setConditions(null);
        }
        Alert createdAlert = alertRepository.save(alert);

        if (createdAlert.getSilenced() != 1 && createdAlert.getStatus().equals("active")) {
            cacheService.setCache(createdAlert);
        }

        return createdAlert;
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

        Integer silenced = alert.getSilenced();
        String status = alert.getStatus();

        alert.setName(alertDto.getName());
        alert.setStationId(alertDto.getStationId());
        alert.setSilenced(alertDto.getSilenced());
        alert.setStatus(alertDto.getStatus());

        alert.setMessage(alertDto.getMessage());

        if (!CommonUtils.isEmptyCollection(alertDto.getConditions())) {
            alert.setConditions(addUidToConditions(alertDto.getConditions()));
        } else {
            alert.setConditions(null);
        }
        alert.setUpdatedAt(LocalDateTime.now());
        Alert updatedAlert = alertRepository.save(alert);

        if (updatedAlert.getSilenced() != 1 && updatedAlert.getStatus().equals("active")) {
            cacheService.setCache(updatedAlert);
        } else {
            if (updatedAlert.getSilenced() == 1 && silenced == 0
                || !updatedAlert.getStatus().equals("active") && status.equals("active")) {
                cacheService.removeCache(updatedAlert);
            }
        }

        return updatedAlert;
    }

    public void deleteAlert(String alertId) {
        if (alertId == null) {
            throw new IllegalArgumentException("Alert ID must be provided");
        }
        Alert deletedAlert = alertRepository.findById(UUID.fromString(alertId))
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));
        alertRepository.deleteByAlertId(UUID.fromString(alertId));
        cacheService.removeCache(deletedAlert);
    }

    public Alert updateAlertStatus(String alertId, String status) {
        if (alertId == null || status == null) {
            throw new IllegalArgumentException("Alert ID and status must be provided");
        }

        Alert alert = alertRepository.findById(UUID.fromString(alertId))
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));

        String statusAlert = alert.getStatus();

        alert.setStatus(status);
        alert.setUpdatedAt(LocalDateTime.now());

        Alert updatedAlert = alertRepository.save(alert);

        if (updatedAlert.getSilenced() != 1 && status.equals("active")) {
            cacheService.setCache(updatedAlert);
        } else {
            if (updatedAlert.getSilenced() != 1 && statusAlert.equals("active")) {
                cacheService.removeCache(updatedAlert);
            }
        }

        return alertRepository.save(alert);
    }

    public List<Alert> getAllActiveAlerts() {
        return alertRepository.getAllActiveAlerts();
    }

    private List<AlertCondition> addUidToConditions(List<AlertCondition> conditions) {
        for (AlertCondition condition : conditions) {
            if (condition.getUid() == null) {
                condition.setUid(UUID.randomUUID());
            }
        }

        return conditions;
    }
}
