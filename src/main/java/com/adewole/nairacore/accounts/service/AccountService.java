package com.adewole.nairacore.accounts.service;

import com.adewole.nairacore.accounts.dto.*;
import com.adewole.nairacore.accounts.entity.*;
import com.adewole.nairacore.accounts.repository.AccountRepository;
import com.adewole.nairacore.accounts.repository.KycDetailsRepository;
import com.adewole.nairacore.shared.exception.BadRequestException;
import com.adewole.nairacore.shared.exception.ResourceNotFoundException;
import com.adewole.nairacore.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final KycDetailsRepository kycDetailsRepository;
    private final AccountNumberGenerator accountNumberGenerator;

    // ─── Create Account ──────────────────────────────────────────────

    @Transactional
    public AccountResponse createAccount(
            CreateAccountRequest request,
            UUID userId
    ) {
        AccountType accountType;
        if (request.getAccountType() == null) {
            throw new BadRequestException(
                    "Invalid account type. Valid types are: SAVINGS, CURRENT, FIXED_DEPOSIT"
            );
        }
        try {
            accountType = AccountType.valueOf(request.getAccountType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "Invalid account type. Valid types are: SAVINGS, CURRENT, FIXED_DEPOSIT"
            );
        }

        boolean accountExists = accountRepository
                .existsByUserIdAndAccountType(userId, accountType);

        if (accountExists) {
            throw new BadRequestException(
                    "You already have a " + accountType + " account"
            );
        }

        String accountNumber = accountNumberGenerator.generate();

        Account account = Account.builder()
                .userId(userId)
                .accountNumber(accountNumber)
                .accountName(request.getAccountName())
                .accountType(accountType)
                .currency(request.getCurrency() != null ? request.getCurrency() : "NGN")
                .build();

        Account saved = accountRepository.save(account);
        return mapToAccountResponse(saved);

    }

    // ─── Get Account By Account Number ───────────────────────────────

    public AccountResponse getAccountByNumber(
            String accountNumber,
            UUID requestingUserId,
            String requestingUserRole
    ) {
        Account account = accountRepository
                .findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + accountNumber
                ));

        boolean isOwner = account.getUserId().equals(requestingUserId);
        boolean isPrivileged = requestingUserRole.equals("ROLE_ADMIN")
                || requestingUserRole.equals("ROLE_TELLER");

        if (!isOwner && !isPrivileged) {
            throw new UnauthorizedException(
                    "You do not have permission to view this account"
            );
        }

        return mapToAccountResponse(account);
    }

    // ─── Get My Accounts ─────────────────────────────────────────────

    public List<AccountSummaryResponse> getMyAccounts(UUID userId) {
        return accountRepository.findAllByUserId(userId)
                .stream()
                .map(this::mapToAccountSummaryResponse)
                .collect(Collectors.toList());
    }

    // ─── Get Balance ─────────────────────────────────────────────────

    public AccountSummaryResponse getBalance(
            String accountNumber,
            UUID requestingUserId,
            String requestingUserRole
    ) {
        Account account = accountRepository
                .findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + accountNumber
                ));

        boolean isOwner = account.getUserId().equals(requestingUserId);
        boolean isPrivileged = requestingUserRole.equals("ROLE_ADMIN")
                || requestingUserRole.equals("ROLE_TELLER");

        if (!isOwner && !isPrivileged) {
            throw new UnauthorizedException(
                    "You do not have permission to view this account"
            );
        }

        return mapToAccountSummaryResponse(account);
    }

    // ─── Deactivate Account ──────────────────────────────────────────

    @Transactional
    public AccountResponse deactivateAccount(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found"
                ));

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BadRequestException("Account is already closed");
        }

        account.setStatus(AccountStatus.CLOSED);

        Account saved = accountRepository.save(account);
        return mapToAccountResponse(saved);
    }

    // ─── Submit KYC ──────────────────────────────────────────────────

    @Transactional
    public KycResponse submitKyc(KycRequest request, UUID userId) {

        if (kycDetailsRepository.existsByUserId(userId)) {
            throw new BadRequestException(
                    "KYC details already submitted"
            );
        }

        if (kycDetailsRepository.existsByBvn(request.getBvn())) {
            throw new BadRequestException(
                    "BVN already registered to another user"
            );
        }

        KycDetails kyc = KycDetails.builder()
                .userId(userId)
                .bvn(request.getBvn())
                .nin(request.getNin())
                .dateOfBirth(request.getDateOfBirth())
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .idType(request.getIdType())
                .idNumber(request.getIdNumber())
                .idExpiryDate(request.getIdExpiryDate())
                .build();

        kycDetailsRepository.save(kyc);

        return mapToKycResponse(kyc);
    }

    // ─── Get KYC ─────────────────────────────────────────────────────

    public KycResponse getKyc(UUID userId) {
        KycDetails kyc = kycDetailsRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "KYC details not found"
                ));
        return mapToKycResponse(kyc);
    }

    // ─── Mappers ─────────────────────────────────────────────────────

    private AccountResponse mapToAccountResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .userId(account.getUserId())
                .accountNumber(account.getAccountNumber())
                .accountName(account.getAccountName())
                .accountType(account.getAccountType().name())
                .status(account.getStatus().name())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .createdAt(account.getCreatedAt())
                .build();
    }

    private AccountSummaryResponse mapToAccountSummaryResponse(Account account) {
        return AccountSummaryResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountName(account.getAccountName())
                .accountType(account.getAccountType().name())
                .status(account.getStatus().name())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .build();
    }

    private KycResponse mapToKycResponse(KycDetails kyc) {
        return KycResponse.builder()
                .id(kyc.getId())
                .userId(kyc.getUserId())
                .bvn(kyc.getBvn())
                .nin(kyc.getNin())
                .dateOfBirth(kyc.getDateOfBirth())
                .address(kyc.getAddress())
                .city(kyc.getCity())
                .state(kyc.getState())
                .idType(kyc.getIdType())
                .idNumber(kyc.getIdNumber())
                .idExpiryDate(kyc.getIdExpiryDate())
                .isVerified(kyc.isVerified())
                .createdAt(kyc.getCreatedAt())
                .build();
    }
}