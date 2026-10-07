package com.personal.finance.category.dto;

import com.personal.finance.category.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(max = 128, message = "Category name must not exceed 128 characters")
        String name,

        @NotNull(message = "Category type is required")
        CategoryType type,

        Long parentId,

        @Size(max = 64, message = "Icon must not exceed 64 characters")
        String icon,

        @Size(max = 16, message = "Color must not exceed 16 characters")
        String color,

        Boolean archived) {
}
