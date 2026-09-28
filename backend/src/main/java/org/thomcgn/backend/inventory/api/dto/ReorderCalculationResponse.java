package org.thomcgn.backend.inventory.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Reorder Calculation Response contract")
public record ReorderCalculationResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long inventoryItemId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String inventoryItemName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime calculationDate,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal currentStockAmount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal weeklyAverageConsumption,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal recommendedReorderAmount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean isBelowThreshold,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) BigDecimal weeksUntilStockout
) {}

