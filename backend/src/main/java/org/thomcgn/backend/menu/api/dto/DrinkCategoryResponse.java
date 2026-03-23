package org.thomcgn.backend.menu.api.dto;

public record DrinkCategoryResponse(
        Long id,
        String name,
        Integer sortOrder,
        boolean active
) {
}

