package org.thomcgn.backend.menu.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Volume Price Update Request contract")
public record VolumePriceUpdateRequest(
        @NotNull @DecimalMin("0.00") BigDecimal price
) {
}

