package com.example.detection.controller;

import com.example.detection.model.User;
import com.example.detection.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    public record UserSummary(Long id, String username, java.util.Set<com.example.detection.model.Role> roles) {}
    private final UserRepository userRepository;

    @GetMapping("/profile")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<String> getUserProfile(Authentication authentication) {
        return ResponseEntity.ok("Welcome " + authentication.getName() + "!");
    }

    @GetMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserSummary>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll().stream().map(u -> new UserSummary(u.getId(), u.getUsername(), u.getRoles())).toList());
    }
}
