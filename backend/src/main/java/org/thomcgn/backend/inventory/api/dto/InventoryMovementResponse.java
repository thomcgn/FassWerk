package org.thomcgn.backend.inventory.api.dto;

import org.thomcgn.backend.inventory.domain.ContentUnit;
import org.thomcgn.backend.inventory.domain.InventoryMovementType;
import org.thomcgn.backend.inventory.domain.InventoryReferenceType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record InventoryMovementResponse(
        Long id,
        Long inventoryItemId,
        String inventoryItemName,
        InventoryMovementType movementType,
        BigDecimal amount,
        ContentUnit unit,
        String reason,
        InventoryReferenceType referenceType,
        String referenceId,
        OffsetDateTime createdAt,
        String createdBy
) {
}

