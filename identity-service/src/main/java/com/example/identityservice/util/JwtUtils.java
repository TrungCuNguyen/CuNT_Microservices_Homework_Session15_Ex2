package com.example.identityservice.util;

import com.example.identityservice.entity.User;
import org.springframework.stereotype.Component;

@Component
public class JwtUtils {

    private final JwtUtil jwtUtil;

    public JwtUtils(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public String generateAccessToken(User user) {
        return jwtUtil.generateAccessToken(user);
    }
}

