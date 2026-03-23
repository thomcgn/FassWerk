package org.thomcgn.backend.menu.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DrinkCategoryRequest(
        @NotBlank String name,
        @NotNull @Min(0) Integer sortOrder,
        @NotNull Boolean active
) {
}

