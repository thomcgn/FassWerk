package org.thomcgn.backend.menu.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DrinkVariantRequest(
        @NotNull Long drinkId,
        @NotBlank String displayVolumeName,
        @NotNull @Min(1) Integer volumeMl,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        String sku,
        @NotNull Boolean active
) {
}

