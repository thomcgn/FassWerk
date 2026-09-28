package org.thomcgn.backend.inventory.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.thomcgn.backend.inventory.domain.ContentUnit;
import org.thomcgn.backend.inventory.domain.InventoryMovementType;
import org.thomcgn.backend.inventory.domain.InventoryReferenceType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Inventory Movement Response contract")
public record InventoryMovementResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long inventoryItemId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String inventoryItemName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) InventoryMovementType movementType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ContentUnit unit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) InventoryReferenceType referenceType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String referenceId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String createdBy
) {
}

