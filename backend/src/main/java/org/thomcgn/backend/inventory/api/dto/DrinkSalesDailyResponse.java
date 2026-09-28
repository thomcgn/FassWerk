package org.thomcgn.backend.inventory.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Drink Sales Daily Response contract")
public record DrinkSalesDailyResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long drinkId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String drinkName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long drinkVariantId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String drinkVariantName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate saleDate,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal quantitySold,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal volumeSoldMl
) {}

