package com.adewole.nairacore.accounts.repository;

import com.adewole.nairacore.accounts.entity.KycDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KycDetailsRepository extends JpaRepository<KycDetails, UUID> {

    Optional<KycDetails> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    boolean existsByBvn(String bvn);
}