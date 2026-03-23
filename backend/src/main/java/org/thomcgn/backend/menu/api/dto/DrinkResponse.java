package org.thomcgn.backend.menu.api.dto;

public record DrinkResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        String description,
        String imageUrl,
        boolean active
) {
}

