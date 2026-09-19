package com.example.detection.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken
) {
}
