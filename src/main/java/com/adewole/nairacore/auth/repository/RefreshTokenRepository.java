package com.adewole.nairacore.auth.repository;

import com.adewole.nairacore.auth.entity.RefreshToken;
import com.adewole.nairacore.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByUser(User user);

    void deleteAllByUser(User user);
}