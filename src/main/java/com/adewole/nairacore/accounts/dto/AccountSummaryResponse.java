package com.adewole.nairacore.accounts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountSummaryResponse {

    private UUID id;
    private String accountNumber;
    private String accountName;
    private String accountType;
    private String status;
    private BigDecimal balance;
    private String currency;
}