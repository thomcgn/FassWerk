package org.thomcgn.backend.menu.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DrinkRequest(
        @NotNull Long categoryId,
        @NotBlank String name,
        String description,
        String imageUrl,
        @NotNull Boolean active
) {
}

