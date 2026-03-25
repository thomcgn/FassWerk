package org.thomcgn.backend.inventory.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DrinkSalesDailyResponse(
    Long id,
    Long drinkId,
    String drinkName,
    Long drinkVariantId,
    String drinkVariantName,
    LocalDate saleDate,
    BigDecimal quantitySold,
    BigDecimal volumeSoldMl
) {}

