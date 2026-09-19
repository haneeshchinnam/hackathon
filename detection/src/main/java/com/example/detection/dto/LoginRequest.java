package com.example.detection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(min = 3, max = 20) String username,
        @NotBlank @Size(min = 6, max = 40) String password
) {
}
