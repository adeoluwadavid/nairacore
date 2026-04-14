package com.adewole.nairacore.accounts.dto;

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
public class AccountResponse {

    private UUID id;
    private UUID userId;
    private String accountNumber;
    private String accountName;
    private String accountType;
    private String status;
    private BigDecimal balance;
    private String currency;
    private LocalDateTime createdAt;
}