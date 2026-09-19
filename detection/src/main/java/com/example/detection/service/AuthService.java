package com.example.detection.service;

import com.example.detection.dto.AuthResponse;
import com.example.detection.dto.LoginRequest;
import com.example.detection.dto.RegisterRequest;
import com.example.detection.model.User;
import com.example.detection.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse register(RegisterRequest authRequest) {
        if(userRepository.existsByEmail(authRequest.email()) || userRepository.existsByUsername(authRequest.username())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Username or email already exists");
        }

        User user = new User(authRequest.username(), authRequest.email(), passwordEncoder.encode(authRequest.password()));

        userRepository.saveAndFlush(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());

        String accessToken = tokenService.generateAccessToken(userDetails);
        String refreshToken = tokenService.generateRefreshToken(userDetails);

        user.setRefreshToken(refreshToken);

        userRepository.saveAndFlush(user);

        return new AuthResponse(accessToken, refreshToken);
    }

    public AuthResponse authenticate(LoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());

        String accessToken = tokenService.generateAccessToken(userDetails);
        String refreshToken = tokenService.generateRefreshToken(userDetails);

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Invalid credentials"));
        user.setRefreshToken(refreshToken);
        userRepository.saveAndFlush(user);

        return new AuthResponse(accessToken, refreshToken);
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!tokenService.isValid(refreshToken) || !tokenService.hasType(refreshToken, "refresh")) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid refresh token");
        }

        String username = tokenService.extractUsername(refreshToken);

        UserDetails storedUserDetails = userDetailsService.loadUserByUsername(username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Invalid refresh token"));

        if (!refreshToken.equals(user.getRefreshToken())) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid refresh token");
        }

        String accessToken = tokenService.generateAccessToken(storedUserDetails);
        return new AuthResponse(accessToken, refreshToken);
    }
}
