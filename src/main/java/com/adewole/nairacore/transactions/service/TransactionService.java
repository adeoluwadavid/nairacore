package com.adewole.nairacore.transactions.service;

import com.adewole.nairacore.accounts.entity.Account;
import com.adewole.nairacore.accounts.entity.AccountStatus;
import com.adewole.nairacore.accounts.repository.AccountRepository;
import com.adewole.nairacore.shared.exception.BadRequestException;
import com.adewole.nairacore.shared.exception.ResourceNotFoundException;
import com.adewole.nairacore.shared.exception.UnauthorizedException;
import com.adewole.nairacore.transactions.dto.*;
import com.adewole.nairacore.transactions.entity.*;
import com.adewole.nairacore.transactions.repository.LedgerEntryRepository;
import com.adewole.nairacore.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;

    private static final String BANK_PREFIX = "NRC";

    // ─── Deposit ─────────────────────────────────────────────────────

    @Transactional
    public TransactionResponse deposit(DepositRequest request,
                                       UUID initiatedBy,
                                       String role) {

        if (transactionRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
            return transactionRepository
                    .findByIdempotencyKey(request.getIdempotencyKey())
                    .map(this::mapToTransactionResponse)
                    .orElseThrow();
        }

        Account account = getActiveAccount(request.getAccountNumber());
        validateAccountOwnership(account, initiatedBy, role); //

        // Create transaction record
        Transaction transaction = Transaction.builder()
                .reference(generateReference())
                .idempotencyKey(request.getIdempotencyKey())
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .amount(request.getAmount())
                .destinationAccountNumber(request.getAccountNumber())
                .description(request.getDescription())
                .initiatedBy(initiatedBy)
                .build();

        transactionRepository.save(transaction);

        try {
            // Update status to processing
            transaction.setStatus(TransactionStatus.PROCESSING);
            transactionRepository.save(transaction);

            BigDecimal balanceBefore = account.getBalance();
            BigDecimal balanceAfter = balanceBefore.add(request.getAmount());

            // Update account balance
            account.setBalance(balanceAfter);
            accountRepository.save(account);

            // Create ledger entry
            createLedgerEntry(
                    transaction,
                    account.getAccountNumber(),
                    EntryType.CREDIT,
                    request.getAmount(),
                    balanceBefore,
                    balanceAfter
            );

            // Mark success
            transaction.setStatus(TransactionStatus.SUCCESS);
            transactionRepository.save(transaction);

            log.info("Deposit successful. Reference: {}, Amount: {}, Account: {}",
                    transaction.getReference(),
                    request.getAmount(),
                    account.getAccountNumber()
            );

        } catch (OptimisticLockingFailureException e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason("Concurrent transaction conflict. Please retry.");
            transactionRepository.save(transaction);
            throw new BadRequestException("Concurrent transaction conflict. Please retry.");

        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(e.getMessage());
            transactionRepository.save(transaction);
            throw new BadRequestException("Transaction failed: " + e.getMessage());
        }

        return mapToTransactionResponse(transaction);
    }

    // ─── Withdrawal ──────────────────────────────────────────────────

    @Transactional
    public TransactionResponse withdraw(WithdrawalRequest request, UUID initiatedBy, String role) {

        // Idempotency check
        if (transactionRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
            return transactionRepository
                    .findByIdempotencyKey(request.getIdempotencyKey())
                    .map(this::mapToTransactionResponse)
                    .orElseThrow();
        }

        Account account = getActiveAccount(request.getAccountNumber());
        validateAccountOwnership(account, initiatedBy, role);

        // Check sufficient balance
        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BadRequestException("Insufficient balance");
        }

        Transaction transaction = Transaction.builder()
                .reference(generateReference())
                .idempotencyKey(request.getIdempotencyKey())
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.PENDING)
                .amount(request.getAmount())
                .sourceAccountNumber(request.getAccountNumber())
                .description(request.getDescription())
                .initiatedBy(initiatedBy)
                .build();

        transactionRepository.save(transaction);

        try {
            transaction.setStatus(TransactionStatus.PROCESSING);
            transactionRepository.save(transaction);

            BigDecimal balanceBefore = account.getBalance();
            BigDecimal balanceAfter = balanceBefore.subtract(request.getAmount());

            account.setBalance(balanceAfter);
            accountRepository.save(account);

            createLedgerEntry(
                    transaction,
                    account.getAccountNumber(),
                    EntryType.DEBIT,
                    request.getAmount(),
                    balanceBefore,
                    balanceAfter
            );

            transaction.setStatus(TransactionStatus.SUCCESS);
            transactionRepository.save(transaction);

            log.info("Withdrawal successful. Reference: {}, Amount: {}, Account: {}",
                    transaction.getReference(),
                    request.getAmount(),
                    account.getAccountNumber()
            );

        } catch (OptimisticLockingFailureException e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason("Concurrent transaction conflict. Please retry.");
            transactionRepository.save(transaction);
            throw new BadRequestException("Concurrent transaction conflict. Please retry.");

        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(e.getMessage());
            transactionRepository.save(transaction);
            throw new BadRequestException("Transaction failed: " + e.getMessage());
        }

        return mapToTransactionResponse(transaction);
    }

    // ─── Transfer ────────────────────────────────────────────────────

    @Transactional
    public TransactionResponse transfer(TransferRequest request, UUID initiatedBy, String role) {

        // Idempotency check
        if (transactionRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
            return transactionRepository
                    .findByIdempotencyKey(request.getIdempotencyKey())
                    .map(this::mapToTransactionResponse)
                    .orElseThrow();
        }

        if (request.getSourceAccountNumber()
                .equals(request.getDestinationAccountNumber())) {
            throw new BadRequestException(
                    "Source and destination accounts cannot be the same"
            );
        }

        Account sourceAccount = getActiveAccount(request.getSourceAccountNumber());
        validateAccountOwnership(sourceAccount, initiatedBy, role);
        Account destinationAccount = getActiveAccount(
                request.getDestinationAccountNumber()
        );

        // Check sufficient balance
        if (sourceAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BadRequestException("Insufficient balance");
        }

        Transaction transaction = Transaction.builder()
                .reference(generateReference())
                .idempotencyKey(request.getIdempotencyKey())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.PENDING)
                .amount(request.getAmount())
                .sourceAccountNumber(request.getSourceAccountNumber())
                .destinationAccountNumber(request.getDestinationAccountNumber())
                .description(request.getDescription())
                .initiatedBy(initiatedBy)
                .build();

        transactionRepository.save(transaction);

        try {
            transaction.setStatus(TransactionStatus.PROCESSING);
            transactionRepository.save(transaction);

            // Source account — debit
            BigDecimal sourceBalanceBefore = sourceAccount.getBalance();
            BigDecimal sourceBalanceAfter = sourceBalanceBefore
                    .subtract(request.getAmount());

            sourceAccount.setBalance(sourceBalanceAfter);
            accountRepository.save(sourceAccount);

            createLedgerEntry(
                    transaction,
                    sourceAccount.getAccountNumber(),
                    EntryType.DEBIT,
                    request.getAmount(),
                    sourceBalanceBefore,
                    sourceBalanceAfter
            );

            // Destination account — credit
            BigDecimal destBalanceBefore = destinationAccount.getBalance();
            BigDecimal destBalanceAfter = destBalanceBefore.add(request.getAmount());

            destinationAccount.setBalance(destBalanceAfter);
            accountRepository.save(destinationAccount);

            createLedgerEntry(
                    transaction,
                    destinationAccount.getAccountNumber(),
                    EntryType.CREDIT,
                    request.getAmount(),
                    destBalanceBefore,
                    destBalanceAfter
            );

            transaction.setStatus(TransactionStatus.SUCCESS);
            transactionRepository.save(transaction);

            log.info("Transfer successful. Reference: {}, Amount: {}, From: {}, To: {}",
                    transaction.getReference(),
                    request.getAmount(),
                    sourceAccount.getAccountNumber(),
                    destinationAccount.getAccountNumber()
            );

        } catch (OptimisticLockingFailureException e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason("Concurrent transaction conflict. Please retry.");
            transactionRepository.save(transaction);
            throw new BadRequestException("Concurrent transaction conflict. Please retry.");

        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(e.getMessage());
            transactionRepository.save(transaction);
            throw new BadRequestException("Transaction failed: " + e.getMessage());
        }

        return mapToTransactionResponse(transaction);
    }

    // ─── Get Transaction By Reference ────────────────────────────────

    public TransactionResponse getByReference(String reference) {
        return transactionRepository.findByReference(reference)
                .map(this::mapToTransactionResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction not found: " + reference
                ));
    }

    // ─── Get Account Transaction History ─────────────────────────────

    public List<TransactionResponse> getAccountTransactions(String accountNumber) {
        return transactionRepository.findAllByAccountNumber(accountNumber)
                .stream()
                .map(this::mapToTransactionResponse)
                .collect(Collectors.toList());
    }

    // ─── Get Ledger Entries ───────────────────────────────────────────

    public List<LedgerEntryResponse> getLedgerEntries(String accountNumber) {
        return ledgerEntryRepository
                .findAllByAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream()
                .map(this::mapToLedgerEntryResponse)
                .collect(Collectors.toList());
    }

    // ─── Private Helpers ─────────────────────────────────────────────

    private Account getActiveAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + accountNumber
                ));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException(
                    "Account " + accountNumber + " is not active"
            );
        }

        return account;
    }

    private String generateReference() {
        Long next = transactionRepository.getNextReferenceSequence();
        return BANK_PREFIX + String.format("%06d", next);
    }

    private void createLedgerEntry(
            Transaction transaction,
            String accountNumber,
            EntryType entryType,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter
    ) {
        LedgerEntry entry = LedgerEntry.builder()
                .transaction(transaction)
                .accountNumber(accountNumber)
                .entryType(entryType)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .build();

        ledgerEntryRepository.save(entry);
    }

    private TransactionResponse mapToTransactionResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .reference(transaction.getReference())
                .type(transaction.getType().name())
                .status(transaction.getStatus().name())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .sourceAccountNumber(transaction.getSourceAccountNumber())
                .destinationAccountNumber(transaction.getDestinationAccountNumber())
                .description(transaction.getDescription())
                .failureReason(transaction.getFailureReason())
                .initiatedBy(transaction.getInitiatedBy())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private LedgerEntryResponse mapToLedgerEntryResponse(LedgerEntry entry) {
        return LedgerEntryResponse.builder()
                .id(entry.getId())
                .transactionReference(entry.getTransaction().getReference())
                .accountNumber(entry.getAccountNumber())
                .entryType(entry.getEntryType().name())
                .amount(entry.getAmount())
                .balanceBefore(entry.getBalanceBefore())
                .balanceAfter(entry.getBalanceAfter())
                .createdAt(entry.getCreatedAt())
                .build();
    }

    private void validateAccountOwnership(
            Account account,
            UUID requestingUserId,
            String requestingUserRole
    ) {
        boolean isOwner = account.getUserId().equals(requestingUserId);
        boolean isPrivileged = requestingUserRole.equals("ROLE_ADMIN")
                || requestingUserRole.equals("ROLE_TELLER");

        if (!isOwner && !isPrivileged) {
            throw new UnauthorizedException(
                    "You do not have permission to transact on this account"
            );
        }
    }
}