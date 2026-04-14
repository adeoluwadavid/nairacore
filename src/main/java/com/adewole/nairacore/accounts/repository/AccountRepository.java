package com.adewole.nairacore.accounts.repository;

import com.adewole.nairacore.accounts.entity.Account;
import com.adewole.nairacore.accounts.entity.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findAllByUserId(UUID userId);

    List<Account> findAllByUserIdAndStatus(UUID userId, AccountStatus status);

    boolean existsByAccountNumber(String accountNumber);

    boolean existsByUserIdAndAccountType(
            UUID userId,
            com.adewole.nairacore.accounts.entity.AccountType accountType
    );

    @Query(
            value = "SELECT nextval('accounts.account_number_seq')",
            nativeQuery = true
    )
    Long getNextAccountSequence();
}