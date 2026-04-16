package com.adewole.nairacore.transactions.dto;

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
public class TransactionResponse {

    private UUID id;
    private String reference;
    private String type;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private String description;
    private String failureReason;
    private UUID initiatedBy;
    private LocalDateTime createdAt;
}