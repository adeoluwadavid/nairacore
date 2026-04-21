package com.adewole.nairacore.transactions.service;

import com.adewole.nairacore.accounts.entity.Account;
import com.adewole.nairacore.accounts.entity.AccountStatus;
import com.adewole.nairacore.accounts.entity.AccountType;
import com.adewole.nairacore.accounts.repository.AccountRepository;
import com.adewole.nairacore.auth.entity.Role;
import com.adewole.nairacore.auth.entity.User;
import com.adewole.nairacore.auth.repository.UserRepository;
import com.adewole.nairacore.notifications.service.TransactionEventPublisher;
import com.adewole.nairacore.shared.exception.BadRequestException;
import com.adewole.nairacore.shared.exception.ResourceNotFoundException;
import com.adewole.nairacore.shared.exception.UnauthorizedException;
import com.adewole.nairacore.transactions.dto.*;
import com.adewole.nairacore.transactions.entity.*;
import com.adewole.nairacore.transactions.repository.LedgerEntryRepository;
import com.adewole.nairacore.transactions.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService Unit Tests")
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionEventPublisher eventPublisher;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TransactionService transactionService;

    private UUID testUserId;
    private Account sourceAccount;
    private Account destinationAccount;
    private User testUser;
    private Transaction savedTransaction;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();

        testUser = User.builder()
                .id(testUserId)
                .firstName("David")
                .lastName("Adewole")
                .email("david@nairacore.com")
                .phoneNumber("+2348102395070")
                .passwordHash("$2a$10$hashedpassword")
                .role(Role.CUSTOMER)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        sourceAccount = Account.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .accountNumber("0123100000")
                .accountName("David Adewole")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .balance(BigDecimal.valueOf(50000))
                .currency("NGN")
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        destinationAccount = Account.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .accountNumber("0123100001")
                .accountName("Chidi Okeke")
                .accountType(AccountType.CURRENT)
                .status(AccountStatus.ACTIVE)
                .balance(BigDecimal.valueOf(20000))
                .currency("NGN")
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        savedTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .reference("NRC100000")
                .idempotencyKey("test-idempotency-key")
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.SUCCESS)
                .amount(BigDecimal.valueOf(10000))
                .currency("NGN")
                .destinationAccountNumber("0123100000")
                .initiatedBy(testUserId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ─── Deposit Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("Should deposit successfully")
    void shouldDepositSuccessfully() {
        DepositRequest request = new DepositRequest();
        request.setAccountNumber("0123100000");
        request.setAmount(BigDecimal.valueOf(10000));
        request.setDescription("Test deposit");
        request.setIdempotencyKey("unique-key-001");

        Transaction depositTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .reference("NRC100000")
                .idempotencyKey("unique-key-001")
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.SUCCESS)
                .amount(BigDecimal.valueOf(10000))
                .currency("NGN")
                .destinationAccountNumber("0123100000")
                .initiatedBy(testUserId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(transactionRepository.existsByIdempotencyKey("unique-key-001"))
                .thenReturn(false);
        when(accountRepository.findByAccountNumber("0123100000"))
                .thenReturn(Optional.of(sourceAccount));
        when(transactionRepository.getNextReferenceSequence()).thenReturn(100000L);
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(depositTransaction);
        when(accountRepository.save(any(Account.class))).thenReturn(sourceAccount);
        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .thenReturn(LedgerEntry.builder().build());
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        TransactionResponse response = transactionService.deposit(
                request, testUserId, "ROLE_CUSTOMER"
        );

        assertThat(response).isNotNull();
        assertThat(response.getReference()).isEqualTo("NRC100000");
        assertThat(response.getType()).isEqualTo("DEPOSIT");
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));

        verify(accountRepository, atLeastOnce()).save(any(Account.class));
        verify(ledgerEntryRepository, times(1)).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Should return existing transaction on duplicate deposit idempotency key")
    void shouldReturnExistingTransactionOnDuplicateDepositKey() {
        DepositRequest request = new DepositRequest();
        request.setAccountNumber("0123100000");
        request.setAmount(BigDecimal.valueOf(10000));
        request.setIdempotencyKey("duplicate-key");

        when(transactionRepository.existsByIdempotencyKey("duplicate-key"))
                .thenReturn(true);
        when(transactionRepository.findByIdempotencyKey("duplicate-key"))
                .thenReturn(Optional.of(savedTransaction));

        TransactionResponse response = transactionService.deposit(
                request, testUserId, "ROLE_CUSTOMER"
        );

        assertThat(response).isNotNull();
        assertThat(response.getReference()).isEqualTo("NRC100000");

        verify(accountRepository, never()).save(any(Account.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException when deposit account not found")
    void shouldThrowWhenDepositAccountNotFound() {
        DepositRequest request = new DepositRequest();
        request.setAccountNumber("9999999999");
        request.setAmount(BigDecimal.valueOf(10000));
        request.setIdempotencyKey("unique-key-002");

        when(transactionRepository.existsByIdempotencyKey(anyString()))
                .thenReturn(false);
        when(accountRepository.findByAccountNumber("9999999999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                transactionService.deposit(request, testUserId, "ROLE_CUSTOMER")
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    @DisplayName("Should throw BadRequestException when deposit account is not active")
    void shouldThrowWhenDepositAccountNotActive() {
        DepositRequest request = new DepositRequest();
        request.setAccountNumber("0123100000");
        request.setAmount(BigDecimal.valueOf(10000));
        request.setIdempotencyKey("unique-key-003");

        sourceAccount.setStatus(AccountStatus.CLOSED);

        when(transactionRepository.existsByIdempotencyKey(anyString()))
                .thenReturn(false);
        when(accountRepository.findByAccountNumber("0123100000"))
                .thenReturn(Optional.of(sourceAccount));

        assertThatThrownBy(() ->
                transactionService.deposit(request, testUserId, "ROLE_CUSTOMER")
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("is not active");
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when customer deposits to another account")
    void shouldThrowWhenCustomerDepositsToAnotherAccount() {
        DepositRequest request = new DepositRequest();
        request.setAccountNumber("0123100000");
        request.setAmount(BigDecimal.valueOf(10000));
        request.setIdempotencyKey("unique-key-004");

        UUID anotherUserId = UUID.randomUUID();

        when(transactionRepository.existsByIdempotencyKey(anyString()))
                .thenReturn(false);
        when(accountRepository.findByAccountNumber("0123100000"))
                .thenReturn(Optional.of(sourceAccount));

        assertThatThrownBy(() ->
                transactionService.deposit(request, anotherUserId, "ROLE_CUSTOMER")
        )
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("You do not have permission to transact on this account");
    }

    // ─── Withdrawal Tests ─────────────────────────────────────────────

    @Test
    @DisplayName("Should withdraw successfully")
    void shouldWithdrawSuccessfully() {
        WithdrawalRequest request = new WithdrawalRequest();
        request.setAccountNumber("0123100000");
        request.setAmount(BigDecimal.valueOf(5000));
        request.setDescription("ATM withdrawal");
        request.setIdempotencyKey("unique-key-005");

        Transaction withdrawalTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .reference("NRC100001")
                .idempotencyKey("unique-key-005")
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.SUCCESS)
                .amount(BigDecimal.valueOf(5000))
                .currency("NGN")
                .sourceAccountNumber("0123100000")
                .initiatedBy(testUserId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(transactionRepository.existsByIdempotencyKey("unique-key-005"))
                .thenReturn(false);
        when(accountRepository.findByAccountNumber("0123100000"))
                .thenReturn(Optional.of(sourceAccount));
        when(transactionRepository.getNextReferenceSequence()).thenReturn(100001L);
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(withdrawalTransaction);
        when(accountRepository.save(any(Account.class))).thenReturn(sourceAccount);
        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .thenReturn(LedgerEntry.builder().build());
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        TransactionResponse response = transactionService.withdraw(
                request, testUserId, "ROLE_CUSTOMER"
        );

        assertThat(response).isNotNull();
        assertThat(response.getType()).isEqualTo("WITHDRAWAL");
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));

        verify(accountRepository, atLeastOnce()).save(any(Account.class));
        verify(ledgerEntryRepository, times(1)).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException when insufficient balance")
    void shouldThrowWhenInsufficientBalance() {
        WithdrawalRequest request = new WithdrawalRequest();
        request.setAccountNumber("0123100000");
        request.setAmount(BigDecimal.valueOf(999999));
        request.setIdempotencyKey("unique-key-006");

        when(transactionRepository.existsByIdempotencyKey(anyString()))
                .thenReturn(false);
        when(accountRepository.findByAccountNumber("0123100000"))
                .thenReturn(Optional.of(sourceAccount));

        assertThatThrownBy(() ->
                transactionService.withdraw(request, testUserId, "ROLE_CUSTOMER")
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Insufficient balance");

        verify(accountRepository, never()).save(any(Account.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException when withdrawal account is not active")
    void shouldThrowWhenWithdrawalAccountNotActive() {
        WithdrawalRequest request = new WithdrawalRequest();
        request.setAccountNumber("0123100000");
        request.setAmount(BigDecimal.valueOf(5000));
        request.setIdempotencyKey("unique-key-007");

        sourceAccount.setStatus(AccountStatus.FROZEN);

        when(transactionRepository.existsByIdempotencyKey(anyString()))
                .thenReturn(false);
        when(accountRepository.findByAccountNumber("0123100000"))
                .thenReturn(Optional.of(sourceAccount));

        assertThatThrownBy(() ->
                transactionService.withdraw(request, testUserId, "ROLE_CUSTOMER")
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("is not active");
    }

    @Test
    @DisplayName("Should return existing transaction on duplicate withdrawal idempotency key")
    void shouldReturnExistingTransactionOnDuplicateWithdrawalKey() {
        WithdrawalRequest request = new WithdrawalRequest();
        request.setAccountNumber("0123100000");
        request.setAmount(BigDecimal.valueOf(5000));
        request.setIdempotencyKey("duplicate-withdrawal-key");

        Transaction existingTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .reference("NRC100001")
                .idempotencyKey("duplicate-withdrawal-key")
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.SUCCESS)
                .amount(BigDecimal.valueOf(5000))
                .currency("NGN")
                .sourceAccountNumber("0123100000")
                .initiatedBy(testUserId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(transactionRepository.existsByIdempotencyKey("duplicate-withdrawal-key"))
                .thenReturn(true);
        when(transactionRepository.findByIdempotencyKey("duplicate-withdrawal-key"))
                .thenReturn(Optional.of(existingTransaction));

        TransactionResponse response = transactionService.withdraw(
                request, testUserId, "ROLE_CUSTOMER"
        );

        assertThat(response).isNotNull();
        assertThat(response.getReference()).isEqualTo("NRC100001");

        verify(accountRepository, never()).save(any(Account.class));
    }

    // ─── Transfer Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("Should transfer successfully")
    void shouldTransferSuccessfully() {
        TransferRequest request = new TransferRequest();
        request.setSourceAccountNumber("0123100000");
        request.setDestinationAccountNumber("0123100001");
        request.setAmount(BigDecimal.valueOf(10000));
        request.setDescription("Transfer to Chidi");
        request.setIdempotencyKey("unique-key-008");

        Transaction transferTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .reference("NRC100002")
                .idempotencyKey("unique-key-008")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .amount(BigDecimal.valueOf(10000))
                .currency("NGN")
                .sourceAccountNumber("0123100000")
                .destinationAccountNumber("0123100001")
                .initiatedBy(testUserId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(transactionRepository.existsByIdempotencyKey("unique-key-008"))
                .thenReturn(false);
        when(accountRepository.findByAccountNumber("0123100000"))
                .thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByAccountNumber("0123100001"))
                .thenReturn(Optional.of(destinationAccount));
        when(transactionRepository.getNextReferenceSequence()).thenReturn(100002L);
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(transferTransaction);
        when(accountRepository.save(any(Account.class)))
                .thenReturn(sourceAccount);
        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .thenReturn(LedgerEntry.builder().build());
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        TransactionResponse response = transactionService.transfer(
                request, testUserId, "ROLE_CUSTOMER"
        );

        assertThat(response).isNotNull();
        assertThat(response.getType()).isEqualTo("TRANSFER");
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getSourceAccountNumber()).isEqualTo("0123100000");
        assertThat(response.getDestinationAccountNumber()).isEqualTo("0123100001");

        verify(accountRepository, atLeastOnce()).save(any(Account.class));
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException when source and destination are the same")
    void shouldThrowWhenSourceAndDestinationAreSame() {
        TransferRequest request = new TransferRequest();
        request.setSourceAccountNumber("0123100000");
        request.setDestinationAccountNumber("0123100000");
        request.setAmount(BigDecimal.valueOf(10000));
        request.setIdempotencyKey("unique-key-009");

        when(transactionRepository.existsByIdempotencyKey(anyString()))
                .thenReturn(false);

        assertThatThrownBy(() ->
                transactionService.transfer(request, testUserId, "ROLE_CUSTOMER")
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Source and destination accounts cannot be the same");

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException when insufficient balance for transfer")
    void shouldThrowWhenInsufficientBalanceForTransfer() {
        TransferRequest request = new TransferRequest();
        request.setSourceAccountNumber("0123100000");
        request.setDestinationAccountNumber("0123100001");
        request.setAmount(BigDecimal.valueOf(999999));
        request.setIdempotencyKey("unique-key-010");

        when(transactionRepository.existsByIdempotencyKey(anyString()))
                .thenReturn(false);
        when(accountRepository.findByAccountNumber("0123100000"))
                .thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByAccountNumber("0123100001"))
                .thenReturn(Optional.of(destinationAccount));

        assertThatThrownBy(() ->
                transactionService.transfer(request, testUserId, "ROLE_CUSTOMER")
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Insufficient balance");

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when customer transfers from another account")
    void shouldThrowWhenCustomerTransfersFromAnotherAccount() {
        TransferRequest request = new TransferRequest();
        request.setSourceAccountNumber("0123100000");
        request.setDestinationAccountNumber("0123100001");
        request.setAmount(BigDecimal.valueOf(10000));
        request.setIdempotencyKey("unique-key-011");

        UUID anotherUserId = UUID.randomUUID();

        when(transactionRepository.existsByIdempotencyKey(anyString()))
                .thenReturn(false);
        when(accountRepository.findByAccountNumber("0123100000"))
                .thenReturn(Optional.of(sourceAccount));

        assertThatThrownBy(() ->
                transactionService.transfer(request, anotherUserId, "ROLE_CUSTOMER")
        )
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("You do not have permission to transact on this account");
    }

    @Test
    @DisplayName("Should return existing transaction on duplicate transfer idempotency key")
    void shouldReturnExistingTransactionOnDuplicateTransferKey() {
        TransferRequest request = new TransferRequest();
        request.setSourceAccountNumber("0123100000");
        request.setDestinationAccountNumber("0123100001");
        request.setAmount(BigDecimal.valueOf(10000));
        request.setIdempotencyKey("duplicate-transfer-key");

        Transaction existingTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .reference("NRC100002")
                .idempotencyKey("duplicate-transfer-key")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .amount(BigDecimal.valueOf(10000))
                .currency("NGN")
                .sourceAccountNumber("0123100000")
                .destinationAccountNumber("0123100001")
                .initiatedBy(testUserId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(transactionRepository.existsByIdempotencyKey("duplicate-transfer-key"))
                .thenReturn(true);
        when(transactionRepository.findByIdempotencyKey("duplicate-transfer-key"))
                .thenReturn(Optional.of(existingTransaction));

        TransactionResponse response = transactionService.transfer(
                request, testUserId, "ROLE_CUSTOMER"
        );

        assertThat(response).isNotNull();
        assertThat(response.getReference()).isEqualTo("NRC100002");

        verify(accountRepository, never()).save(any(Account.class));
    }

    // ─── Get Transaction Tests ────────────────────────────────────────

    @Test
    @DisplayName("Should get transaction by reference successfully")
    void shouldGetTransactionByReferenceSuccessfully() {
        when(transactionRepository.findByReference("NRC100000"))
                .thenReturn(Optional.of(savedTransaction));

        TransactionResponse response = transactionService.getByReference("NRC100000");

        assertThat(response).isNotNull();
        assertThat(response.getReference()).isEqualTo("NRC100000");
        assertThat(response.getType()).isEqualTo("DEPOSIT");
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when reference not found")
    void shouldThrowWhenReferenceNotFound() {
        when(transactionRepository.findByReference("NRC999999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                transactionService.getByReference("NRC999999")
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transaction not found");
    }

    // ─── Get Account Transactions Tests ──────────────────────────────

    @Test
    @DisplayName("Should get account transaction history successfully")
    void shouldGetAccountTransactionHistorySuccessfully() {
        Transaction secondTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .reference("NRC100001")
                .idempotencyKey("key-002")
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.SUCCESS)
                .amount(BigDecimal.valueOf(5000))
                .currency("NGN")
                .sourceAccountNumber("0123100000")
                .initiatedBy(testUserId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(transactionRepository.findAllByAccountNumber("0123100000"))
                .thenReturn(List.of(savedTransaction, secondTransaction));

        List<TransactionResponse> responses =
                transactionService.getAccountTransactions("0123100000");

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getReference()).isEqualTo("NRC100000");
        assertThat(responses.get(1).getReference()).isEqualTo("NRC100001");
    }

    @Test
    @DisplayName("Should return empty list when no transactions found")
    void shouldReturnEmptyListWhenNoTransactionsFound() {
        when(transactionRepository.findAllByAccountNumber(anyString()))
                .thenReturn(List.of());

        List<TransactionResponse> responses =
                transactionService.getAccountTransactions("0123100000");

        assertThat(responses).isEmpty();
    }

    // ─── Get Ledger Entries Tests ─────────────────────────────────────

    @Test
    @DisplayName("Should get ledger entries for account")
    void shouldGetLedgerEntriesForAccount() {
        LedgerEntry entry1 = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .transaction(savedTransaction)
                .accountNumber("0123100000")
                .entryType(EntryType.CREDIT)
                .amount(BigDecimal.valueOf(10000))
                .balanceBefore(BigDecimal.valueOf(40000))
                .balanceAfter(BigDecimal.valueOf(50000))
                .createdAt(LocalDateTime.now())
                .build();

        when(ledgerEntryRepository.findAllByAccountNumberOrderByCreatedAtDesc(
                "0123100000")
        ).thenReturn(List.of(entry1));

        List<LedgerEntryResponse> responses =
                transactionService.getLedgerEntries("0123100000");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getAccountNumber()).isEqualTo("0123100000");
        assertThat(responses.get(0).getEntryType()).isEqualTo("CREDIT");
        assertThat(responses.get(0).getBalanceBefore())
                .isEqualByComparingTo(BigDecimal.valueOf(40000));
        assertThat(responses.get(0).getBalanceAfter())
                .isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(responses.get(0).getTransactionReference())
                .isEqualTo("NRC100000");
    }

    @Test
    @DisplayName("Should return empty list when no ledger entries found")
    void shouldReturnEmptyListWhenNoLedgerEntriesFound() {
        when(ledgerEntryRepository.findAllByAccountNumberOrderByCreatedAtDesc(
                anyString())
        ).thenReturn(List.of());

        List<LedgerEntryResponse> responses =
                transactionService.getLedgerEntries("0123100000");

        assertThat(responses).isEmpty();
    }
}