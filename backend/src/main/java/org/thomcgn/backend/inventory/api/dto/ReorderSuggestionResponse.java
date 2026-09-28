package org.thomcgn.backend.inventory.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.thomcgn.backend.inventory.domain.ContentUnit;
import org.thomcgn.backend.inventory.domain.PackageType;

import java.math.BigDecimal;

@Schema(description = "Reorder Suggestion Response contract")
public record ReorderSuggestionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long inventoryItemId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String inventoryItemName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal currentStock,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal threshold,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal minimumStock,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal recommendedOrderAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal recommendedOrderPackages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal packageSize,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PackageType packageType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ContentUnit contentUnit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String supplier
) {
}

