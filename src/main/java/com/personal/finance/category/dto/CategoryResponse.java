package com.personal.finance.category.dto;

import com.personal.finance.category.CategoryType;

public record CategoryResponse(
        Long id,
        String name,
        CategoryType type,
        Long parentId,
        String icon,
        String color,
        boolean archived) {
}
