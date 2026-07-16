package com.biopet.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {}
