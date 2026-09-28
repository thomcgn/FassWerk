package org.thomcgn.backend.menu.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Drink Variant Request contract")
public record DrinkVariantRequest(
        @NotNull Long drinkId,
        @NotBlank String displayVolumeName,
        @NotNull @Min(1) Integer volumeMl,
        @Schema(nullable = true)
        @DecimalMin("0.00") BigDecimal price,
        @Schema(nullable = true) Boolean useStandardPrice,
        @Schema(nullable = true) String sku,
        @NotNull Boolean active
) {
}

