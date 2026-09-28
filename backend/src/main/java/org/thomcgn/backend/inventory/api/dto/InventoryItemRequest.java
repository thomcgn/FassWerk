package org.thomcgn.backend.inventory.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thomcgn.backend.inventory.domain.ContentUnit;
import org.thomcgn.backend.inventory.domain.PackageType;

import java.math.BigDecimal;

@Schema(description = "Inventory Item Request contract")
public record InventoryItemRequest(
        @NotBlank String name,
        @Schema(nullable = true) Long linkedDrinkId,
        @Schema(nullable = true) Long linkedDrinkVariantId,
        @NotNull PackageType packageType,
        @Schema(description = "Initial stock for creation; updates must retain the displayed package count. Use the adjustment endpoint for stock corrections.")
        @NotNull @DecimalMin("0.00") BigDecimal packagesInStock,
        @NotNull @DecimalMin("0.01") BigDecimal contentPerPackage,
        @NotNull ContentUnit contentUnit,
        @NotNull @DecimalMin("0.00") BigDecimal reorderThreshold,
        @NotNull @DecimalMin("0.00") BigDecimal minimumStock,
        @Schema(nullable = true)
        @DecimalMin("0.00") BigDecimal recommendedReorderAmount,
        @Schema(nullable = true)
        @DecimalMin("0.00") BigDecimal reorderThresholdPackages,
        @Schema(nullable = true)
        @DecimalMin("0.00") BigDecimal minimumStockPackages,
        @Schema(nullable = true)
        @DecimalMin("0.00") BigDecimal recommendedReorderPackages,
        @Schema(nullable = true) String supplier,
        @NotNull Boolean active,
        @Schema(description = "Revision read before editing; required for updates, omitted for creation", nullable = true)
        @jakarta.validation.constraints.Min(0) Long expectedRevision
) {
}

