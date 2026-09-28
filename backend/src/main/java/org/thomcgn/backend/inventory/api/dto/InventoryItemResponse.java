package org.thomcgn.backend.inventory.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.thomcgn.backend.inventory.domain.ContentUnit;
import org.thomcgn.backend.inventory.domain.PackageType;

import java.math.BigDecimal;

@Schema(description = "Inventory Item Response contract")
public record InventoryItemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long linkedDrinkId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long linkedDrinkVariantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PackageType packageType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal packagesInStock,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal contentPerPackage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ContentUnit contentUnit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal totalStockAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal reorderThreshold,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal minimumStock,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal recommendedReorderAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String supplier,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active
) {
}

