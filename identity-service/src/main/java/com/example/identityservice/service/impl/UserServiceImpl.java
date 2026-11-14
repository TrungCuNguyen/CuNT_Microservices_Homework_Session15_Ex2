package com.example.identityservice.service.impl;

import com.example.identityservice.dto.LoginRequest;
import com.example.identityservice.dto.LoginResponse;
import com.example.identityservice.dto.RegisterRequest;
import com.example.identityservice.dto.TokenRefreshRequest;
import com.example.identityservice.dto.TokenRefreshResponse;
import com.example.identityservice.dto.UserResponse;
import com.example.identityservice.entity.RefreshToken;
import com.example.identityservice.entity.User;
import com.example.identityservice.exception.DuplicateResourceException;
import com.example.identityservice.exception.ResourceNotFoundException;
import com.example.identityservice.exception.TokenRefreshException;
import com.example.identityservice.repository.UserRepository;
import com.example.identityservice.service.RefreshTokenService;
import com.example.identityservice.service.UserService;
import com.example.identityservice.util.JwtUtils;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;

    public UserServiceImpl(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtils jwtUtils,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username '" + request.getUsername() + "' is already registered");
        }

        String role = (request.getRole() != null && !request.getRole().trim().isEmpty())
                ? request.getRole().trim().toUpperCase()
                : "USER";

        // Hash password using BCrypt before storing in database
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User(request.getUsername().trim(), hashedPassword, role);
        User savedUser = userRepository.save(user);

        // Return user info without password
        return new UserResponse(savedUser.getId(), savedUser.getUsername(), savedUser.getRole());
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 1. Xác thực username/password thành công
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Bad credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Bad credentials");
        }

        // 2. Gọi JwtUtils tạo Access Token
        String accessToken = jwtUtils.generateAccessToken(user);

        // 3. Gọi RefreshTokenService tạo Refresh Token (UUID lưu vào DB)
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

        // 4. Trả về DTO chứa cả hai token
        return new LoginResponse(accessToken, refreshToken.getToken(), "Bearer");
    }

    @Override
    @Transactional
    public TokenRefreshResponse refreshToken(TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String accessToken = jwtUtils.generateAccessToken(user);
                    return new TokenRefreshResponse(accessToken, requestRefreshToken, "Bearer");
                })
                .orElseThrow(() -> new TokenRefreshException("Refresh token is not in database!"));
    }

    @Override
    @Transactional(readOnly = true)
    public String generateTestToken(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
        return jwtUtils.generateAccessToken(user);
    }
}
