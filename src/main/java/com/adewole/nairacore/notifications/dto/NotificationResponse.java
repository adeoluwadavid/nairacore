package com.adewole.nairacore.notifications.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private UUID id;
    private UUID userId;
    private String accountNumber;
    private String transactionReference;
    private String type;
    private String channel;
    private String recipient;
    private String subject;
    private String message;
    private String status;
    private String failureReason;
    private LocalDateTime createdAt;
}