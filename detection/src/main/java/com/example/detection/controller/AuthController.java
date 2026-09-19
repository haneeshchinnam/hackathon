package com.example.detection.controller;

import com.example.detection.dto.AuthResponse;
import com.example.detection.dto.LoginRequest;
import com.example.detection.dto.RegisterRequest;
import com.example.detection.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping({"/api/v1/auth/register", "/api/auth/register"})
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping({"/api/v1/auth/login", "/api/auth/login"})
    public ResponseEntity<AuthResponse> authenticate(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.authenticate(request));
    }

    @PostMapping({"/api/v1/auth/refresh", "/api/auth/refresh"})
    public ResponseEntity<AuthResponse> refreshToken(@RequestHeader("refresh_token") String refreshToken) {
        if (refreshToken != null && refreshToken.startsWith("Bearer ")) {
            refreshToken = refreshToken.substring(7);
            AuthResponse newRes = authService.refreshToken(refreshToken);
            return ResponseEntity.ok(newRes);
        } else {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "refresh_token must contain a Bearer refresh token");
        }
    }

}
