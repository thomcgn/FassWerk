package org.thomcgn.backend.menu.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Drink Variant Response contract")
public record DrinkVariantResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long drinkId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String drinkName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayVolumeName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer volumeMl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal price,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean useStandardPrice,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String sku,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active
) {
}

