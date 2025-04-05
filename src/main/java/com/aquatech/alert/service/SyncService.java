package com.aquatech.alert.service;

import com.aquatech.alert.entity.Alert;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class SyncService {

    @Autowired
    private  AlertService alertService;

    @Autowired
    private CacheService cacheService;

    @PostConstruct
    public void loadAlertsToRedis() {
        try {
            log.info("Loading alerts to Redis on startup...");
            syncAlertsToRedis();
        } catch (Exception e) {
            log.error("Error loading alerts to Redis", e);
        }
    }

    @Scheduled(fixedRate = 60*60*1000) // 1 hour
    @Async("syncExecutor")
    public void scheduledSync() {
        try {
            log.info("Syncing alerts to Redis...");
            syncAlertsToRedis();
        } catch (Exception e) {
            log.error("Error syncing alerts to Redis", e);
        }
    }

    private void syncAlertsToRedis() {
        List<Alert> alerts = alertService.getAllActiveAlerts();

        if (alerts == null || alerts.isEmpty()) {
            log.info("No alerts found to sync to Redis");
            return;
        }

        try {
            Set<String> activeKeys = cacheService.updateActiveAlerts(alerts);

            cacheService.cleanupInactiveAlerts(activeKeys);
        } catch (Exception e) {
            log.error("Error in sync process", e);
        }
    }


}