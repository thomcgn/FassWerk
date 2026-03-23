package org.thomcgn.backend.inventory.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InventoryAdjustmentRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank String reason,
        @NotNull Boolean increase
) {
}

