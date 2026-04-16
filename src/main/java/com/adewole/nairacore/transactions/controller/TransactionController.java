package com.adewole.nairacore.transactions.controller;

import com.adewole.nairacore.shared.config.UserPrincipal;
import com.adewole.nairacore.shared.response.ApiResponse;
import com.adewole.nairacore.transactions.dto.*;
import com.adewole.nairacore.transactions.service.TransactionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Transactions", description = "Endpoints for deposits, withdrawals, transfers and ledger")
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    // ─── Deposit ─────────────────────────────────────────────────────

    @PostMapping("/deposit")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @Valid @RequestBody DepositRequest request
    ) {
        UUID initiatedBy = extractUserId();
        String role = extractRole();
        TransactionResponse response = transactionService.deposit(
                request, initiatedBy, role
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Deposit successful", response));
    }

    // ─── Withdrawal ──────────────────────────────────────────────────

    @PostMapping("/withdraw")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @Valid @RequestBody WithdrawalRequest request
    ) {
        UUID initiatedBy = extractUserId();
        String role = extractRole();
        TransactionResponse response = transactionService.withdraw(
                request, initiatedBy, role
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Withdrawal successful", response));
    }

    // ─── Transfer ────────────────────────────────────────────────────

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransferRequest request
    ) {
        UUID initiatedBy = extractUserId();
        String role = extractRole();
        TransactionResponse response = transactionService.transfer(
                request, initiatedBy, role
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Transfer successful", response));
    }

    // ─── Get Transaction By Reference ────────────────────────────────

    @GetMapping("/{reference}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TransactionResponse>> getByReference(
            @PathVariable String reference
    ) {
        TransactionResponse response = transactionService.getByReference(reference);
        return ResponseEntity.ok(
                ApiResponse.success("Transaction retrieved successfully", response)
        );
    }

    // ─── Get Account Transaction History ─────────────────────────────

    @GetMapping("/account/{accountNumber}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getAccountTransactions(
            @PathVariable String accountNumber
    ) {
        List<TransactionResponse> response = transactionService
                .getAccountTransactions(accountNumber);
        return ResponseEntity.ok(
                ApiResponse.success("Transactions retrieved successfully", response)
        );
    }

    // ─── Get Ledger Entries ───────────────────────────────────────────

    @GetMapping("/ledger/{accountNumber}")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<LedgerEntryResponse>>> getLedgerEntries(
            @PathVariable String accountNumber
    ) {
        List<LedgerEntryResponse> response = transactionService
                .getLedgerEntries(accountNumber);
        return ResponseEntity.ok(
                ApiResponse.success("Ledger entries retrieved successfully", response)
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
                .map(a -> a.getAuthority())
                .findFirst()
                .orElse("");
    }
}