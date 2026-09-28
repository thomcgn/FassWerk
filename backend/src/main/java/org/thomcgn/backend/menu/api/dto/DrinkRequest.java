package org.thomcgn.backend.menu.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Drink Request contract")
public record DrinkRequest(
        @NotNull Long categoryId,
        @NotBlank String name,
        @Schema(nullable = true) String description,
        @Schema(nullable = true) String imageUrl,
        @NotNull Boolean active
) {
}

