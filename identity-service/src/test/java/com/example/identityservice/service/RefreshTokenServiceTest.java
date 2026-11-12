package com.example.identityservice.service;

import com.example.identityservice.entity.RefreshToken;
import com.example.identityservice.entity.User;
import com.example.identityservice.exception.ResourceNotFoundException;
import com.example.identityservice.exception.TokenRefreshException;
import com.example.identityservice.repository.RefreshTokenRepository;
import com.example.identityservice.repository.UserRepository;
import com.example.identityservice.service.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenDurationMs", 604800000L); // 7 days
        user = new User("user-id-123", "testuser", "hashed_pwd", "USER");
    }

    @Test
    @DisplayName("createRefreshToken should generate valid UUID, calculate expiryDate and save to repository")
    void createRefreshToken_Success() {
        when(userRepository.findById("user-id-123")).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.empty());
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken refreshToken = refreshTokenService.createRefreshToken("user-id-123");

        assertNotNull(refreshToken);
        assertEquals(user, refreshToken.getUser());
        assertNotNull(refreshToken.getToken());
        // Verify valid UUID string format
        assertDoesNotThrow(() -> UUID.fromString(refreshToken.getToken()));
        assertTrue(refreshToken.getExpiryDate().isAfter(Instant.now()));
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("createRefreshToken should throw ResourceNotFoundException when user does not exist")
    void createRefreshToken_UserNotFound_ThrowsException() {
        when(userRepository.findById("non_existent")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> refreshTokenService.createRefreshToken("non_existent"));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("verifyExpiration should return token when it is not expired")
    void verifyExpiration_ValidToken() {
        RefreshToken token = new RefreshToken("uuid-token", Instant.now().plusSeconds(3600), user);

        RefreshToken result = refreshTokenService.verifyExpiration(token);

        assertEquals(token, result);
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    @DisplayName("verifyExpiration should delete token and throw TokenRefreshException when token is expired")
    void verifyExpiration_ExpiredToken_ThrowsException() {
        RefreshToken token = new RefreshToken("uuid-token", Instant.now().minusSeconds(10), user);

        assertThrows(TokenRefreshException.class, () -> refreshTokenService.verifyExpiration(token));
        verify(refreshTokenRepository, times(1)).delete(token);
    }
}
