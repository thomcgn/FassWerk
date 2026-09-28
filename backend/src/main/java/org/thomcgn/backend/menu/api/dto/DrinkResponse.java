package org.thomcgn.backend.menu.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "Drink Response contract")
public record DrinkResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long categoryId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String categoryName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String imageUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active
) {
}

