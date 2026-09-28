package org.thomcgn.backend.inventory.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Reorder Order Response contract")
public record ReorderOrderResponse(
    @JsonProperty("id")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,

    @JsonProperty("inventoryItemId")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long inventoryItemId,

    @JsonProperty("inventoryItemName")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String inventoryItemName,

    @JsonProperty("supplierId")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long supplierId,

    @JsonProperty("supplierName")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String supplierName,

    @JsonProperty("orderedQuantity")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal orderedQuantity,

    @JsonProperty("orderedUnit")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String orderedUnit,

    @JsonProperty("scheduledDeliveryDate")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate scheduledDeliveryDate,

    @JsonProperty("scheduledDeliveryTime")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalTime scheduledDeliveryTime,

    @JsonProperty("status")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,

    @JsonProperty("notes")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String notes,

    @JsonProperty("receivedQuantity")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) BigDecimal receivedQuantity,

    @JsonProperty("receivedAt")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String receivedAt,

    @JsonProperty("createdBy")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String createdBy,

    @JsonProperty("createdAt")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String createdAt
) {}


