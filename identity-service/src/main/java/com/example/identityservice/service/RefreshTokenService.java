package com.example.identityservice.service;

import com.example.identityservice.entity.RefreshToken;
import java.util.Optional;

public interface RefreshTokenService {
    RefreshToken createRefreshToken(String userId);
    RefreshToken verifyExpiration(RefreshToken token);
    Optional<RefreshToken> findByToken(String token);
    int deleteByUserId(String userId);
}
