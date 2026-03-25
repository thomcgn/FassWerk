package org.thomcgn.backend.inventory.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DrinkSalesWeeklyResponse(
    Long id,
    Long drinkId,
    String drinkName,
    Long drinkVariantId,
    String drinkVariantName,
    LocalDate weekStartDate,
    BigDecimal quantitySold,
    BigDecimal volumeSoldMl,
    BigDecimal averageDailyQuantity,
    BigDecimal averageDailyVolumeMl
) {}

