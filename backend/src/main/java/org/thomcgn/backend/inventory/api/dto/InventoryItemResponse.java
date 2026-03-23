package org.thomcgn.backend.inventory.api.dto;

import org.thomcgn.backend.inventory.domain.ContentUnit;
import org.thomcgn.backend.inventory.domain.PackageType;

import java.math.BigDecimal;

public record InventoryItemResponse(
        Long id,
        String name,
        Long linkedDrinkId,
        Long linkedDrinkVariantId,
        PackageType packageType,
        BigDecimal packagesInStock,
        BigDecimal contentPerPackage,
        ContentUnit contentUnit,
        BigDecimal totalStockAmount,
        BigDecimal reorderThreshold,
        BigDecimal minimumStock,
        BigDecimal recommendedReorderAmount,
        String supplier,
        boolean active
) {
}

