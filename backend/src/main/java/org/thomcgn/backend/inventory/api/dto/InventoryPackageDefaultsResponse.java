package org.thomcgn.backend.inventory.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.thomcgn.backend.inventory.domain.PackageType;

import java.math.BigDecimal;

@Schema(description = "Inventory Package Defaults Response contract")
public record InventoryPackageDefaultsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PackageType packageType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal reorderThresholdPackages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal minimumStockPackages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal recommendedReorderPackages
) {
}

