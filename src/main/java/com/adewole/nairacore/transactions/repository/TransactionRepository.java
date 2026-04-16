package com.adewole.nairacore.transactions.repository;

import com.adewole.nairacore.transactions.entity.Transaction;
import com.adewole.nairacore.transactions.entity.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByReference(String reference);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findAllBySourceAccountNumberOrderByCreatedAtDesc(
            String accountNumber
    );

    List<Transaction> findAllByDestinationAccountNumberOrderByCreatedAtDesc(
            String accountNumber
    );

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.sourceAccountNumber = :accountNumber
        OR t.destinationAccountNumber = :accountNumber
        ORDER BY t.createdAt DESC
    """)
    List<Transaction> findAllByAccountNumber(String accountNumber);

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Query(value = "SELECT nextval('transactions.transaction_reference_seq')",
            nativeQuery = true)
    Long getNextReferenceSequence();
}