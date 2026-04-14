package com.adewole.nairacore.accounts.controller;

import com.adewole.nairacore.accounts.dto.*;
import com.adewole.nairacore.accounts.service.AccountService;
import com.adewole.nairacore.auth.service.UserService;
import com.adewole.nairacore.shared.config.UserPrincipal;
import com.adewole.nairacore.shared.exception.BadRequestException;
import com.adewole.nairacore.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final UserService userService;

    // ─── Create Account ──────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody CreateAccountRequest request
    ) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        UUID requestingUserId = principal.getUserId();
        String role = extractRole();
        UUID targetUserId;

        if (role.equals("ROLE_CUSTOMER")) {
            targetUserId = requestingUserId;
        } else {
            if (request.getTargetUserId() == null) {
                throw new BadRequestException(
                        "targetUserId is required when creating an account for a customer"
                );
            }
            userService.validateUserExists(request.getTargetUserId());
            targetUserId = request.getTargetUserId();
        }

        AccountResponse response = accountService.createAccount(request, targetUserId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created successfully", response));
    }

    // ─── Get My Accounts ─────────────────────────────────────────────

    @GetMapping("/my-accounts")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<AccountSummaryResponse>>> getMyAccounts() {
        UUID userId = extractUserId();
        List<AccountSummaryResponse> accounts = accountService.getMyAccounts(userId);
        return ResponseEntity.ok(
                ApiResponse.success("Accounts retrieved successfully", accounts)
        );
    }

    // ─── Get Account By Account Number ───────────────────────────────

    @GetMapping("/{accountNumber}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountByNumber(
            @PathVariable String accountNumber
    ) {
        UUID userId = extractUserId();
        String role = extractRole();
        AccountResponse response = accountService.getAccountByNumber(
                accountNumber, userId, role
        );
        return ResponseEntity.ok(
                ApiResponse.success("Account retrieved successfully", response)
        );
    }

    // ─── Get Balance ─────────────────────────────────────────────────

    @GetMapping("/{accountNumber}/balance")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountSummaryResponse>> getBalance(
            @PathVariable String accountNumber
    ) {
        UUID userId = extractUserId();
        String role = extractRole();
        AccountSummaryResponse response = accountService.getBalance(
                accountNumber, userId, role
        );
        return ResponseEntity.ok(
                ApiResponse.success("Balance retrieved successfully", response)
        );
    }

    // ─── Deactivate Account ──────────────────────────────────────────

    @PutMapping("/{accountId}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    public ResponseEntity<ApiResponse<AccountResponse>> deactivateAccount(
            @PathVariable UUID accountId
    ) {
        AccountResponse response = accountService.deactivateAccount(accountId);
        return ResponseEntity.ok(
                ApiResponse.success("Account deactivated successfully", response)
        );
    }

    // ─── Submit KYC ──────────────────────────────────────────────────

    @PostMapping("/kyc")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<KycResponse>> submitKyc(
            @Valid @RequestBody KycRequest request
    ) {
        UUID userId = extractUserId();
        KycResponse response = accountService.submitKyc(request, userId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("KYC submitted successfully", response));
    }

    // ─── Get KYC ─────────────────────────────────────────────────────

    @GetMapping("/kyc")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<KycResponse>> getKyc() {
        UUID userId = extractUserId();
        KycResponse response = accountService.getKyc(userId);
        return ResponseEntity.ok(
                ApiResponse.success("KYC retrieved successfully", response)
        );
    }

    // ─── Private Helpers ─────────────────────────────────────────────

    private UUID extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return principal.getUserId();
    }

    private String extractRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("");
    }
}