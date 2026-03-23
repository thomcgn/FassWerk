package org.thomcgn.backend.inventory.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thomcgn.backend.inventory.domain.ContentUnit;
import org.thomcgn.backend.inventory.domain.PackageType;

import java.math.BigDecimal;

public record InventoryItemRequest(
        @NotBlank String name,
        Long linkedDrinkId,
        Long linkedDrinkVariantId,
        @NotNull PackageType packageType,
        @NotNull @DecimalMin("0.00") BigDecimal packagesInStock,
        @NotNull @DecimalMin("0.01") BigDecimal contentPerPackage,
        @NotNull ContentUnit contentUnit,
        @NotNull @DecimalMin("0.00") BigDecimal reorderThreshold,
        @NotNull @DecimalMin("0.00") BigDecimal minimumStock,
        @NotNull @DecimalMin("0.00") BigDecimal recommendedReorderAmount,
        String supplier,
        @NotNull Boolean active
) {
}

