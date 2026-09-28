package org.thomcgn.backend.menu.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "Drink Category Response contract")
public record DrinkCategoryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer sortOrder,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active
) {
}

