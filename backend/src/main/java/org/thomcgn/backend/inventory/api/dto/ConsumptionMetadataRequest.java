package org.thomcgn.backend.inventory.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMin;

@Schema(description = "Consumption Metadata Request contract")
public record ConsumptionMetadataRequest(
    @Schema(nullable = true)
    @Min(0) Integer leadTimeDays,
    @Schema(nullable = true)
    @DecimalMin("0.00") BigDecimal safetyStockFactor,
    @Schema(nullable = true)
    @Min(1) Integer weeksLookback
) {}

