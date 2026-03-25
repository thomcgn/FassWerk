package org.thomcgn.backend.inventory.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ReorderCalculationResponse(
    Long id,
    Long inventoryItemId,
    String inventoryItemName,
    OffsetDateTime calculationDate,
    BigDecimal currentStockAmount,
    BigDecimal weeklyAverageConsumption,
    BigDecimal recommendedReorderAmount,
    boolean isBelowThreshold,
    BigDecimal weeksUntilStockout
) {}

