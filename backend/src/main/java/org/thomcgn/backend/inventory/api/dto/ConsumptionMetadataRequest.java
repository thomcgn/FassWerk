package org.thomcgn.backend.inventory.api.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMin;

public record ConsumptionMetadataRequest(
    @Min(0) Integer leadTimeDays,
    @DecimalMin("0.00") BigDecimal safetyStockFactor,
    @Min(1) Integer weeksLookback
) {}

