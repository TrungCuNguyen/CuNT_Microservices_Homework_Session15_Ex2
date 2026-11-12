package com.example.identityservice.service;

import com.example.identityservice.dto.LoginRequest;
import com.example.identityservice.dto.LoginResponse;
import com.example.identityservice.dto.RegisterRequest;
import com.example.identityservice.dto.TokenRefreshRequest;
import com.example.identityservice.dto.TokenRefreshResponse;
import com.example.identityservice.dto.UserResponse;

public interface UserService {
    UserResponse register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
    TokenRefreshResponse refreshToken(TokenRefreshRequest request);
    String generateTestToken(String username);
}

