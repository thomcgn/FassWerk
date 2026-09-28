package org.thomcgn.backend.billing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Table Order Item Response contract")
public record TableOrderItemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long drinkVariantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String drinkLabel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer quantity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal unitPrice,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal totalPrice,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal deductedVolumeMl
) {
}

