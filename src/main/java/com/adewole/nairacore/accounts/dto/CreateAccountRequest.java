package com.adewole.nairacore.accounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateAccountRequest {

    @NotNull(message = "Account type is required")
    private String accountType;

    @NotBlank(message = "Account name is required")
    private String accountName;

    @Pattern(
            regexp = "^[A-Z]{3}$",
            message = "Currency must be a 3-letter ISO code e.g. NGN, USD"
    )
    private String currency = "NGN";

    // Only required when TELLER or ADMIN is creating account for a customer
    private UUID targetUserId;
}