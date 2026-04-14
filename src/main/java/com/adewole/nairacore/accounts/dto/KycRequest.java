package com.adewole.nairacore.accounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class KycRequest {

    @NotBlank(message = "BVN is required")
    @Size(min = 11, max = 11, message = "BVN must be exactly 11 digits")
    @Pattern(regexp = "^[0-9]{11}$", message = "BVN must contain only digits")
    private String bvn;

    @Size(min = 11, max = 11, message = "NIN must be exactly 11 digits")
    @Pattern(regexp = "^[0-9]{11}$", message = "NIN must contain only digits")
    private String nin;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Address is required")
    private String address;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "State is required")
    private String state;

    private String idType;
    private String idNumber;
    private LocalDate idExpiryDate;
}