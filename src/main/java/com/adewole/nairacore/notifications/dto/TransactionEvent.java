package com.adewole.nairacore.notifications.dto;

import com.adewole.nairacore.transactions.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {

    private UUID userId;
    private String accountNumber;
    private String transactionReference;
    private TransactionType transactionType;
    private BigDecimal amount;
    private String currency;
    private BigDecimal balanceAfter;
    private String description;
    private LocalDateTime transactionTime;
    private String recipientEmail;
    private String recipientName;
}