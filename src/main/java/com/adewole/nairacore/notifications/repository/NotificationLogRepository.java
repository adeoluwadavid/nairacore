package com.adewole.nairacore.notifications.repository;

import com.adewole.nairacore.notifications.entity.NotificationLog;
import com.adewole.nairacore.notifications.entity.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    List<NotificationLog> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<NotificationLog> findAllByTransactionReference(String transactionReference);

    List<NotificationLog> findAllByStatus(NotificationStatus status);
}