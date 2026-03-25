package org.thomcgn.backend.menu.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record VolumePriceUpdateRequest(
        @NotNull @DecimalMin("0.00") BigDecimal price
) {
}

