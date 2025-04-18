package com.aquatech.alert.controller;

import com.aquatech.alert.dto.AlertDto;
import com.aquatech.alert.payload.response.SuccessApiResponse;
import com.aquatech.alert.service.AlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/alert")
public class AlertController {
    @Autowired
    private AlertService alertService;

    @PostMapping("create/{userId}")
    public ResponseEntity<?> createAlert(
            @PathVariable Integer userId,
            @RequestBody AlertDto alertDto
    ) {
        return ResponseEntity.ok().body(new SuccessApiResponse<>(alertService.createAlert(userId, alertDto)));
    }

    @GetMapping("get/{userId}")
    public ResponseEntity<?> getAlertsByUserId(
            @PathVariable Integer userId
    ) {
        return ResponseEntity.ok().body(new SuccessApiResponse<>(alertService.getAlertsByUserId(userId)));
    }

    @PutMapping("update/{alertId}")
    public ResponseEntity<?> updateAlert(
            @PathVariable String alertId,
            @RequestBody AlertDto alertDto
    ) {
        return ResponseEntity.ok().body(new SuccessApiResponse<>(alertService.updateAlert(alertId, alertDto)));
    }

    @DeleteMapping("delete/{alertId}")
    public ResponseEntity<?> deleteAlert(
            @PathVariable String alertId
    ) {
        alertService.deleteAlert(alertId);
        return ResponseEntity.ok().body(new SuccessApiResponse<>("Alert deleted successfully"));
    }

    @PutMapping("{alertId}/status/{status}")
    public ResponseEntity<?> updateAlertStatus(
            @PathVariable String alertId,
            @PathVariable String status
    ) {
        return ResponseEntity.ok().body(new SuccessApiResponse<>(alertService.updateAlertStatus(alertId, status)));
    }
}
