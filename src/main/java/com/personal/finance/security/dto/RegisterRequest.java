package com.personal.finance.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Username is required")
        @Size(max = 64, message = "Username must not exceed 64 characters")
        String username,

        @NotBlank(message = "Password is required")
        String password,

        @Size(max = 128, message = "Display name must not exceed 128 characters")
        String displayName) {
}
