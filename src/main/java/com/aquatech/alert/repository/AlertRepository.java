package com.aquatech.alert.repository;

import com.aquatech.alert.entity.Alert;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {
    List<Alert> findByUserId(Integer userId);

    @Query("SELECT a FROM Alert a WHERE a.userId = ?1 AND a.stationId = ?2 AND a.status != 'deleted'")
    Optional<Alert> findActiveAlertByUserIdAndStationId(Integer userId, Integer stationId);

    @Transactional
    @Modifying
    @Query("UPDATE Alert a SET a.status = 'deleted', a.updatedAt = CURRENT_TIMESTAMP WHERE a.id = ?1 ")
    void deleteByAlertId(UUID alertId);
}
