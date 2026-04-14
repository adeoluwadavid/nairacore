package com.adewole.nairacore.accounts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycResponse {

    private UUID id;
    private UUID userId;
    private String bvn;
    private String nin;
    private LocalDate dateOfBirth;
    private String address;
    private String city;
    private String state;
    private String idType;
    private String idNumber;
    private LocalDate idExpiryDate;
    private boolean isVerified;
    private LocalDateTime createdAt;
}