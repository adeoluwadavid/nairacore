package com.adewole.nairacore.notifications.service;

import com.adewole.nairacore.notifications.dto.NotificationResponse;
import com.adewole.nairacore.notifications.entity.NotificationLog;
import com.adewole.nairacore.notifications.repository.NotificationLogRepository;
import com.adewole.nairacore.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationLogRepository notificationLogRepository;

    // ─── Get My Notifications ─────────────────────────────────────────

    public List<NotificationResponse> getMyNotifications(UUID userId) {
        return notificationLogRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─── Get Notifications By Transaction Reference ───────────────────

    public List<NotificationResponse> getByTransactionReference(String reference) {
        List<NotificationLog> logs = notificationLogRepository
                .findAllByTransactionReference(reference);

        if (logs.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No notifications found for reference: " + reference
            );
        }

        return logs.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─── Mapper ──────────────────────────────────────────────────────

    private NotificationResponse mapToResponse(NotificationLog log) {
        return NotificationResponse.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .accountNumber(log.getAccountNumber())
                .transactionReference(log.getTransactionReference())
                .type(log.getType().name())
                .channel(log.getChannel().name())
                .recipient(log.getRecipient())
                .subject(log.getSubject())
                .message(log.getMessage())
                .status(log.getStatus().name())
                .failureReason(log.getFailureReason())
                .createdAt(log.getCreatedAt())
                .build();
    }
}