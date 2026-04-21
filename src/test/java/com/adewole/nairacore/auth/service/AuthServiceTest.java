package com.adewole.nairacore.auth.service;

import com.adewole.nairacore.auth.dto.*;
import com.adewole.nairacore.auth.entity.RefreshToken;
import com.adewole.nairacore.auth.entity.Role;
import com.adewole.nairacore.auth.entity.User;
import com.adewole.nairacore.auth.repository.RefreshTokenRepository;
import com.adewole.nairacore.auth.repository.UserRepository;
import com.adewole.nairacore.shared.config.JwtUtil;
import com.adewole.nairacore.shared.exception.BadRequestException;
import com.adewole.nairacore.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private UUID testUserId;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

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

        registerRequest = new RegisterRequest();
        registerRequest.setFirstName("David");
        registerRequest.setLastName("Adewole");
        registerRequest.setEmail("david@nairacore.com");
        registerRequest.setPhoneNumber("+2348102395070");
        registerRequest.setPassword("Password123");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("david@nairacore.com");
        loginRequest.setPassword("Password123");
    }

    // ─── Register Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("Should register user successfully")
    void shouldRegisterUserSuccessfully() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedpassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtUtil.generateAccessToken(
                anyString(),
                anyString(),
                any()
        )).thenReturn("mock.jwt.token");
        when(jwtUtil.getExpiration()).thenReturn(900000L);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenReturn(RefreshToken.builder()
                        .id(UUID.randomUUID())
                        .user(testUser)
                        .token(UUID.randomUUID().toString())
                        .expiresAt(LocalDateTime.now().plusDays(30))
                        .createdAt(LocalDateTime.now())
                        .build());

        AuthResponse response = authService.register(registerRequest);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUser().getEmail()).isEqualTo("david@nairacore.com");
        assertThat(response.getUser().getRole()).isEqualTo("CUSTOMER");

        verify(userRepository).save(any(User.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException when email already exists")
    void shouldThrowWhenEmailAlreadyExists() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Email already in use");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException when phone number already exists")
    void shouldThrowWhenPhoneNumberAlreadyExists() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Phone number already in use");

        verify(userRepository, never()).save(any(User.class));
    }

    // ─── Login Tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("Should login successfully with correct credentials")
    void shouldLoginSuccessfully() {
        when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtUtil.generateAccessToken(
                anyString(),
                anyString(),
                any()
        )).thenReturn("mock.jwt.token");
        when(jwtUtil.getExpiration()).thenReturn(900000L);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenReturn(RefreshToken.builder()
                        .id(UUID.randomUUID())
                        .user(testUser)
                        .token(UUID.randomUUID().toString())
                        .expiresAt(LocalDateTime.now().plusDays(30))
                        .createdAt(LocalDateTime.now())
                        .build());

        AuthResponse response = authService.login(loginRequest);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getUser().getEmail()).isEqualTo("david@nairacore.com");

        verify(refreshTokenRepository).deleteAllByUser(testUser);
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when user not found")
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when password is incorrect")
    void shouldThrowWhenPasswordIsIncorrect() {
        when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when account is deactivated")
    void shouldThrowWhenAccountIsDeactivated() {
        testUser.setActive(false);

        when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Account is deactivated");
    }

    // ─── Refresh Token Tests ──────────────────────────────────────────

    @Test
    @DisplayName("Should refresh token successfully")
    void shouldRefreshTokenSuccessfully() {
        RefreshToken refreshToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .token("valid-refresh-token")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .build();

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-refresh-token");

        when(refreshTokenRepository.findByToken(anyString()))
                .thenReturn(Optional.of(refreshToken));
        when(jwtUtil.generateAccessToken(
                anyString(),
                anyString(),
                any()
        )).thenReturn("new.jwt.token");
        when(jwtUtil.getExpiration()).thenReturn(900000L);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenReturn(RefreshToken.builder()
                        .id(UUID.randomUUID())
                        .user(testUser)
                        .token(UUID.randomUUID().toString())
                        .expiresAt(LocalDateTime.now().plusDays(30))
                        .createdAt(LocalDateTime.now())
                        .build());

        AuthResponse response = authService.refresh(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new.jwt.token");

        // Verify token rotation — old token deleted
        verify(refreshTokenRepository).delete(refreshToken);
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when refresh token not found")
    void shouldThrowWhenRefreshTokenNotFound() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("invalid-token");

        when(refreshTokenRepository.findByToken(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when refresh token is expired")
    void shouldThrowWhenRefreshTokenIsExpired() {
        RefreshToken expiredToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .token("expired-token")
                .expiresAt(LocalDateTime.now().minusDays(1))
                .createdAt(LocalDateTime.now().minusDays(31))
                .build();

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("expired-token");

        when(refreshTokenRepository.findByToken(anyString()))
                .thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token expired. Please login again");

        verify(refreshTokenRepository).delete(expiredToken);
    }

    // ─── Logout Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("Should logout successfully")
    void shouldLogoutSuccessfully() {
        RefreshToken refreshToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .token("valid-token")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .build();

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-token");

        when(refreshTokenRepository.findByToken(anyString()))
                .thenReturn(Optional.of(refreshToken));

        authService.logout(request);

        verify(refreshTokenRepository).delete(refreshToken);
    }

    @Test
    @DisplayName("Should not throw when logout with invalid token")
    void shouldNotThrowWhenLogoutWithInvalidToken() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("invalid-token");

        when(refreshTokenRepository.findByToken(anyString()))
                .thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(() -> authService.logout(request));
    }
}