package org.thomcgn.backend.inventory.api.dto;

import java.math.BigDecimal;

public record ConsumptionMetadataResponse(
    Long id,
    Long inventoryItemId,
    Integer leadTimeDays,
    BigDecimal safetyStockFactor,
    Integer weeksLookback
) {}

