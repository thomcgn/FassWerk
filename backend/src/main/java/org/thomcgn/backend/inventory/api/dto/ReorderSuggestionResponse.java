package org.thomcgn.backend.inventory.api.dto;

import org.thomcgn.backend.inventory.domain.ContentUnit;
import org.thomcgn.backend.inventory.domain.PackageType;

import java.math.BigDecimal;

public record ReorderSuggestionResponse(
        Long inventoryItemId,
        String inventoryItemName,
        BigDecimal currentStock,
        BigDecimal threshold,
        BigDecimal minimumStock,
        BigDecimal recommendedOrderAmount,
        BigDecimal recommendedOrderPackages,
        BigDecimal packageSize,
        PackageType packageType,
        ContentUnit contentUnit,
        String supplier
) {
}

