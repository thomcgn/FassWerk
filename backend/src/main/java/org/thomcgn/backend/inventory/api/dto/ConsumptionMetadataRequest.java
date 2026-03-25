package org.thomcgn.backend.inventory.api.dto;

import java.math.BigDecimal;

public record ConsumptionMetadataRequest(
    Integer leadTimeDays,
    BigDecimal safetyStockFactor,
    Integer weeksLookback
) {}

