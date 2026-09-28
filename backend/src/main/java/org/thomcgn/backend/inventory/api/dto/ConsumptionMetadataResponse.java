package org.thomcgn.backend.inventory.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Consumption Metadata Response contract")
public record ConsumptionMetadataResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long inventoryItemId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer leadTimeDays,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal safetyStockFactor,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer weeksLookback
) {}

