package org.thomcgn.backend.menu.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Drink Category Request contract")
public record DrinkCategoryRequest(
        @NotBlank String name,
        @NotNull @Min(0) Integer sortOrder,
        @NotNull Boolean active
) {
}

