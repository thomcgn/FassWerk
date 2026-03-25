package org.thomcgn.backend.inventory.api.dto;

import org.thomcgn.backend.inventory.domain.PackageType;

import java.math.BigDecimal;

public record InventoryPackageDefaultsResponse(
        PackageType packageType,
        BigDecimal reorderThresholdPackages,
        BigDecimal minimumStockPackages,
        BigDecimal recommendedReorderPackages
) {
}

