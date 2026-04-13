package com.adewole.nairacore.auth.service;

import com.adewole.nairacore.auth.dto.*;
import com.adewole.nairacore.auth.entity.RefreshToken;
import com.adewole.nairacore.auth.entity.Role;
import com.adewole.nairacore.auth.entity.User;
import com.adewole.nairacore.auth.repository.RefreshTokenRepository;
import com.adewole.nairacore.auth.repository.UserRepository;
import com.adewole.nairacore.shared.config.JwtUtil;
import com.adewole.nairacore.shared.exception.BadRequestException;
import com.adewole.nairacore.shared.exception.ResourceNotFoundException;
import com.adewole.nairacore.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    // ─── Register ────────────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already in use");
        }

        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new BadRequestException("Phone number already in use");
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .build();

        userRepository.save(user);

        return generateAuthResponse(user);
    }

    // ─── Login ───────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (!user.isActive()) {
            throw new UnauthorizedException("Account is deactivated");
        }

        // Invalidate all existing refresh tokens on new login
        refreshTokenRepository.deleteAllByUser(user);

        return generateAuthResponse(user);
    }

    // ─── Refresh Token ───────────────────────────────────────────────

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {

        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(request.getRefreshToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new UnauthorizedException("Refresh token expired. Please login again");
        }

        User user = refreshToken.getUser();

        if (!user.isActive()) {
            throw new UnauthorizedException("Account is deactivated");
        }

        // Token rotation — delete old token, issue new one
        refreshTokenRepository.delete(refreshToken);

        return generateAuthResponse(user);
    }

    // ─── Logout ──────────────────────────────────────────────────────

    @Transactional
    public void logout(RefreshTokenRequest request) {

        refreshTokenRepository.findByToken(request.getRefreshToken())
                .ifPresent(refreshTokenRepository::delete);
    }

    // ─── Private Helpers ─────────────────────────────────────────────

    private AuthResponse generateAuthResponse(User user) {

        String accessToken = jwtUtil.generateAccessToken(
                user.getEmail(),
                user.getRole().name(),
                user.getId()
        );

        String refreshTokenValue = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenValue)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpiration() / 1000)
                .user(mapToUserResponse(user))
                .build();
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole().name())
                .isActive(user.isActive())
                .build();
    }
}