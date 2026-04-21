package com.adewole.nairacore.accounts.service;

import com.adewole.nairacore.accounts.dto.*;
import com.adewole.nairacore.accounts.entity.*;
import com.adewole.nairacore.accounts.repository.AccountRepository;
import com.adewole.nairacore.accounts.repository.KycDetailsRepository;
import com.adewole.nairacore.shared.exception.BadRequestException;
import com.adewole.nairacore.shared.exception.ResourceNotFoundException;
import com.adewole.nairacore.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private KycDetailsRepository kycDetailsRepository;

    @Mock
    private AccountNumberGenerator accountNumberGenerator;

    @InjectMocks
    private AccountService accountService;

    private UUID testUserId;
    private Account testSavingsAccount;
    private Account testCurrentAccount;
    private Account savedAccount;
    private CreateAccountRequest createAccountRequest;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();

        testSavingsAccount = Account.builder()
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

        testCurrentAccount = Account.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .accountNumber("0123100001")
                .accountName("David Adewole")
                .accountType(AccountType.CURRENT)
                .status(AccountStatus.ACTIVE)
                .balance(BigDecimal.valueOf(20000))
                .currency("NGN")
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        savedAccount = Account.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .accountNumber("0123100000")
                .accountName("David Adewole")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .balance(BigDecimal.ZERO)
                .currency("NGN")
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        createAccountRequest = new CreateAccountRequest();
        createAccountRequest.setAccountType("SAVINGS");
        createAccountRequest.setAccountName("David Adewole");
        createAccountRequest.setCurrency("NGN");
    }

    // ─── Create Account Tests ─────────────────────────────────────────

    @Test
    @DisplayName("Should create SAVINGS account successfully")
    void shouldCreateSavingsAccountSuccessfully() {
        when(accountRepository.existsByUserIdAndAccountType(
                any(UUID.class), any(AccountType.class))
        ).thenReturn(false);
        when(accountNumberGenerator.generate()).thenReturn("0123100000");
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        AccountResponse response = accountService.createAccount(
                createAccountRequest, testUserId
        );

        assertThat(response).isNotNull();
        assertThat(response.getAccountNumber()).isEqualTo("0123100000");
        assertThat(response.getAccountType()).isEqualTo("SAVINGS");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getCurrency()).isEqualTo("NGN");
        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getUserId()).isEqualTo(testUserId);

        verify(accountRepository, times(1)).save(any(Account.class));
        verify(accountNumberGenerator, times(1)).generate();
    }

    @Test
    @DisplayName("Should create CURRENT account successfully")
    void shouldCreateCurrentAccountSuccessfully() {
        createAccountRequest.setAccountType("CURRENT");
        createAccountRequest.setAccountName("David Adewole Current");

        Account savedCurrentAccount = Account.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .accountNumber("0123100001")
                .accountName("David Adewole Current")
                .accountType(AccountType.CURRENT)
                .status(AccountStatus.ACTIVE)
                .balance(BigDecimal.ZERO)
                .currency("NGN")
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(accountRepository.existsByUserIdAndAccountType(
                any(UUID.class), any(AccountType.class))
        ).thenReturn(false);
        when(accountNumberGenerator.generate()).thenReturn("0123100001");
        when(accountRepository.save(any(Account.class))).thenReturn(savedCurrentAccount);

        AccountResponse response = accountService.createAccount(
                createAccountRequest, testUserId
        );

        assertThat(response).isNotNull();
        assertThat(response.getAccountType()).isEqualTo("CURRENT");
        assertThat(response.getAccountNumber()).isEqualTo("0123100001");
    }

    @Test
    @DisplayName("Should default currency to NGN when not provided")
    void shouldDefaultCurrencyToNgn() {
        createAccountRequest.setCurrency(null);

        when(accountRepository.existsByUserIdAndAccountType(
                any(UUID.class), any(AccountType.class))
        ).thenReturn(false);
        when(accountNumberGenerator.generate()).thenReturn("0123100000");
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        AccountResponse response = accountService.createAccount(
                createAccountRequest, testUserId
        );

        assertThat(response.getCurrency()).isEqualTo("NGN");
    }

    @Test
    @DisplayName("Should throw BadRequestException when SAVINGS account already exists")
    void shouldThrowWhenSavingsAccountAlreadyExists() {
        when(accountRepository.existsByUserIdAndAccountType(
                any(UUID.class), any(AccountType.class))
        ).thenReturn(true);

        assertThatThrownBy(() ->
                accountService.createAccount(createAccountRequest, testUserId)
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("You already have a SAVINGS account");

        verify(accountRepository, never()).save(any(Account.class));
        verify(accountNumberGenerator, never()).generate();
    }

    @Test
    @DisplayName("Should throw BadRequestException for invalid account type")
    void shouldThrowForInvalidAccountType() {
        createAccountRequest.setAccountType("INVALID_TYPE");

        assertThatThrownBy(() ->
                accountService.createAccount(createAccountRequest, testUserId)
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid account type");

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException for null account type")
    void shouldThrowForNullAccountType() {
        createAccountRequest.setAccountType(null);

        assertThatThrownBy(() ->
                accountService.createAccount(createAccountRequest, testUserId)
        )
                .isInstanceOf(BadRequestException.class);

        verify(accountRepository, never()).save(any(Account.class));
    }

    // ─── Get Account By Number Tests ──────────────────────────────────

    @Test
    @DisplayName("Should get account by number for account owner")
    void shouldGetAccountByNumberForOwner() {
        when(accountRepository.findByAccountNumber("0123100000"))
                .thenReturn(Optional.of(testSavingsAccount));

        AccountResponse response = accountService.getAccountByNumber(
                "0123100000", testUserId, "ROLE_CUSTOMER"
        );

        assertThat(response).isNotNull();
        assertThat(response.getAccountNumber()).isEqualTo("0123100000");
        assertThat(response.getAccountType()).isEqualTo("SAVINGS");
        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Should get account by number for ADMIN role")
    void shouldGetAccountByNumberForAdmin() {
        UUID adminId = UUID.randomUUID();
        when(accountRepository.findByAccountNumber("0123100000"))
                .thenReturn(Optional.of(testSavingsAccount));

        AccountResponse response = accountService.getAccountByNumber(
                "0123100000", adminId, "ROLE_ADMIN"
        );

        assertThat(response).isNotNull();
        assertThat(response.getAccountNumber()).isEqualTo("0123100000");
    }

    @Test
    @DisplayName("Should get account by number for TELLER role")
    void shouldGetAccountByNumberForTeller() {
        UUID tellerId = UUID.randomUUID();
        when(accountRepository.findByAccountNumber("0123100000"))
                .thenReturn(Optional.of(testSavingsAccount));

        AccountResponse response = accountService.getAccountByNumber(
                "0123100000", tellerId, "ROLE_TELLER"
        );

        assertThat(response).isNotNull();
        assertThat(response.getAccountNumber()).isEqualTo("0123100000");
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when CUSTOMER views another user account")
    void shouldThrowWhenCustomerViewsAnotherAccount() {
        UUID anotherUserId = UUID.randomUUID();
        when(accountRepository.findByAccountNumber("0123100000"))
                .thenReturn(Optional.of(testSavingsAccount));

        assertThatThrownBy(() ->
                accountService.getAccountByNumber(
                        "0123100000", anotherUserId, "ROLE_CUSTOMER"
                )
        )
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("You do not have permission to view this account");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when account number does not exist")
    void shouldThrowWhenAccountNumberNotFound() {
        when(accountRepository.findByAccountNumber(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                accountService.getAccountByNumber(
                        "9999999999", testUserId, "ROLE_CUSTOMER"
                )
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found");
    }

    // ─── Get My Accounts Tests ────────────────────────────────────────

    @Test
    @DisplayName("Should return all accounts for a user")
    void shouldReturnAllAccountsForUser() {
        when(accountRepository.findAllByUserId(testUserId))
                .thenReturn(List.of(testSavingsAccount, testCurrentAccount));

        List<AccountSummaryResponse> accounts =
                accountService.getMyAccounts(testUserId);

        assertThat(accounts).hasSize(2);
        assertThat(accounts.get(0).getAccountNumber()).isEqualTo("0123100000");
        assertThat(accounts.get(0).getAccountType()).isEqualTo("SAVINGS");
        assertThat(accounts.get(1).getAccountNumber()).isEqualTo("0123100001");
        assertThat(accounts.get(1).getAccountType()).isEqualTo("CURRENT");
    }

    @Test
    @DisplayName("Should return empty list when user has no accounts")
    void shouldReturnEmptyListWhenUserHasNoAccounts() {
        when(accountRepository.findAllByUserId(testUserId))
                .thenReturn(List.of());

        List<AccountSummaryResponse> accounts =
                accountService.getMyAccounts(testUserId);

        assertThat(accounts).isEmpty();
    }

    // ─── Get Balance Tests ────────────────────────────────────────────

    @Test
    @DisplayName("Should get balance for account owner")
    void shouldGetBalanceForOwner() {
        when(accountRepository.findByAccountNumber("0123100000"))
                .thenReturn(Optional.of(testSavingsAccount));

        AccountSummaryResponse response = accountService.getBalance(
                "0123100000", testUserId, "ROLE_CUSTOMER"
        );

        assertThat(response).isNotNull();
        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(response.getAccountNumber()).isEqualTo("0123100000");
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when customer checks another account balance")
    void shouldThrowWhenCustomerChecksAnotherAccountBalance() {
        UUID anotherUserId = UUID.randomUUID();
        when(accountRepository.findByAccountNumber("0123100000"))
                .thenReturn(Optional.of(testSavingsAccount));

        assertThatThrownBy(() ->
                accountService.getBalance(
                        "0123100000", anotherUserId, "ROLE_CUSTOMER"
                )
        )
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("You do not have permission to view this account");
    }

    // ─── Deactivate Account Tests ─────────────────────────────────────

    @Test
    @DisplayName("Should deactivate ACTIVE account successfully")
    void shouldDeactivateActiveAccountSuccessfully() {
        Account closedAccount = Account.builder()
                .id(testSavingsAccount.getId())
                .userId(testUserId)
                .accountNumber("0123100000")
                .accountName("David Adewole")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.CLOSED)
                .balance(BigDecimal.valueOf(50000))
                .currency("NGN")
                .version(0L)
                .createdAt(testSavingsAccount.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        when(accountRepository.findById(testSavingsAccount.getId()))
                .thenReturn(Optional.of(testSavingsAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(closedAccount);

        AccountResponse response = accountService.deactivateAccount(
                testSavingsAccount.getId()
        );

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("CLOSED");

        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    @DisplayName("Should deactivate FROZEN account successfully")
    void shouldDeactivateFrozenAccountSuccessfully() {
        testSavingsAccount.setStatus(AccountStatus.FROZEN);

        Account closedAccount = Account.builder()
                .id(testSavingsAccount.getId())
                .userId(testUserId)
                .accountNumber("0123100000")
                .accountName("David Adewole")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.CLOSED)
                .balance(BigDecimal.valueOf(50000))
                .currency("NGN")
                .version(0L)
                .createdAt(testSavingsAccount.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        when(accountRepository.findById(testSavingsAccount.getId()))
                .thenReturn(Optional.of(testSavingsAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(closedAccount);

        AccountResponse response = accountService.deactivateAccount(
                testSavingsAccount.getId()
        );

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("Should throw BadRequestException when deactivating already closed account")
    void shouldThrowWhenDeactivatingClosedAccount() {
        testSavingsAccount.setStatus(AccountStatus.CLOSED);

        when(accountRepository.findById(testSavingsAccount.getId()))
                .thenReturn(Optional.of(testSavingsAccount));

        assertThatThrownBy(() ->
                accountService.deactivateAccount(testSavingsAccount.getId())
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Account is already closed");

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when account to deactivate not found")
    void shouldThrowWhenAccountToDeactivateNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        when(accountRepository.findById(nonExistentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                accountService.deactivateAccount(nonExistentId)
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Account not found");
    }

    // ─── KYC Tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("Should submit KYC successfully with all fields")
    void shouldSubmitKycSuccessfullyWithAllFields() {
        KycRequest kycRequest = new KycRequest();
        kycRequest.setBvn("12345678901");
        kycRequest.setNin("98765432101");
        kycRequest.setDateOfBirth(LocalDate.of(1990, 5, 15));
        kycRequest.setAddress("12 Awolowo Road");
        kycRequest.setCity("Ibadan");
        kycRequest.setState("Oyo");
        kycRequest.setIdType("NATIONAL_ID");
        kycRequest.setIdNumber("AB1234567");
        kycRequest.setIdExpiryDate(LocalDate.of(2028, 12, 31));

        KycDetails savedKyc = KycDetails.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .bvn("12345678901")
                .nin("98765432101")
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .address("12 Awolowo Road")
                .city("Ibadan")
                .state("Oyo")
                .idType("NATIONAL_ID")
                .idNumber("AB1234567")
                .idExpiryDate(LocalDate.of(2028, 12, 31))
                .isVerified(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(kycDetailsRepository.existsByUserId(testUserId)).thenReturn(false);
        when(kycDetailsRepository.existsByBvn("12345678901")).thenReturn(false);
        when(kycDetailsRepository.save(any(KycDetails.class))).thenReturn(savedKyc);

        KycResponse response = accountService.submitKyc(kycRequest, testUserId);

        assertThat(response).isNotNull();
        assertThat(response.getBvn()).isEqualTo("12345678901");
        assertThat(response.getNin()).isEqualTo("98765432101");
        assertThat(response.getCity()).isEqualTo("Ibadan");
        assertThat(response.getState()).isEqualTo("Oyo");
        assertThat(response.getIdType()).isEqualTo("NATIONAL_ID");
        assertThat(response.isVerified()).isFalse();

        verify(kycDetailsRepository, times(1)).save(any(KycDetails.class));
    }

    @Test
    @DisplayName("Should submit KYC successfully with required fields only")
    void shouldSubmitKycWithRequiredFieldsOnly() {
        KycRequest kycRequest = new KycRequest();
        kycRequest.setBvn("12345678901");
        kycRequest.setAddress("12 Awolowo Road");
        kycRequest.setCity("Ibadan");
        kycRequest.setState("Oyo");

        KycDetails savedKyc = KycDetails.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .bvn("12345678901")
                .address("12 Awolowo Road")
                .city("Ibadan")
                .state("Oyo")
                .isVerified(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(kycDetailsRepository.existsByUserId(testUserId)).thenReturn(false);
        when(kycDetailsRepository.existsByBvn("12345678901")).thenReturn(false);
        when(kycDetailsRepository.save(any(KycDetails.class))).thenReturn(savedKyc);

        KycResponse response = accountService.submitKyc(kycRequest, testUserId);

        assertThat(response).isNotNull();
        assertThat(response.getBvn()).isEqualTo("12345678901");
        assertThat(response.isVerified()).isFalse();
    }

    @Test
    @DisplayName("Should throw BadRequestException when KYC already submitted")
    void shouldThrowWhenKycAlreadySubmitted() {
        KycRequest kycRequest = new KycRequest();
        kycRequest.setBvn("12345678901");
        kycRequest.setAddress("12 Awolowo Road");
        kycRequest.setCity("Ibadan");
        kycRequest.setState("Oyo");

        when(kycDetailsRepository.existsByUserId(testUserId)).thenReturn(true);

        assertThatThrownBy(() ->
                accountService.submitKyc(kycRequest, testUserId)
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessage("KYC details already submitted");

        verify(kycDetailsRepository, never()).save(any(KycDetails.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException when BVN already registered to another user")
    void shouldThrowWhenBvnAlreadyRegisteredToAnotherUser() {
        KycRequest kycRequest = new KycRequest();
        kycRequest.setBvn("12345678901");
        kycRequest.setAddress("12 Awolowo Road");
        kycRequest.setCity("Ibadan");
        kycRequest.setState("Oyo");

        when(kycDetailsRepository.existsByUserId(testUserId)).thenReturn(false);
        when(kycDetailsRepository.existsByBvn("12345678901")).thenReturn(true);

        assertThatThrownBy(() ->
                accountService.submitKyc(kycRequest, testUserId)
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessage("BVN already registered to another user");

        verify(kycDetailsRepository, never()).save(any(KycDetails.class));
    }

    @Test
    @DisplayName("Should get KYC details successfully")
    void shouldGetKycDetailsSuccessfully() {
        KycDetails kyc = KycDetails.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .bvn("12345678901")
                .nin("98765432101")
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .address("12 Awolowo Road")
                .city("Ibadan")
                .state("Oyo")
                .isVerified(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(kycDetailsRepository.findByUserId(testUserId))
                .thenReturn(Optional.of(kyc));

        KycResponse response = accountService.getKyc(testUserId);

        assertThat(response).isNotNull();
        assertThat(response.getBvn()).isEqualTo("12345678901");
        assertThat(response.getNin()).isEqualTo("98765432101");
        assertThat(response.getCity()).isEqualTo("Ibadan");
        assertThat(response.getState()).isEqualTo("Oyo");
        assertThat(response.getUserId()).isEqualTo(testUserId);
        assertThat(response.isVerified()).isFalse();
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when KYC not found")
    void shouldThrowWhenKycNotFound() {
        when(kycDetailsRepository.findByUserId(testUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getKyc(testUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("KYC details not found");
    }
}