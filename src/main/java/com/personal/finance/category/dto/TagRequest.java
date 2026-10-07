package com.personal.finance.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagRequest(
        @NotBlank(message = "Tag name is required")
        @Size(max = 64, message = "Tag name must not exceed 64 characters")
        String name) {
}
