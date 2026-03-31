package org.thomcgn.backend.inventory.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReorderOrderResponse(
    @JsonProperty("id")
    Long id,

    @JsonProperty("inventoryItemId")
    Long inventoryItemId,

    @JsonProperty("inventoryItemName")
    String inventoryItemName,

    @JsonProperty("supplierId")
    Long supplierId,

    @JsonProperty("supplierName")
    String supplierName,

    @JsonProperty("orderedQuantity")
    BigDecimal orderedQuantity,

    @JsonProperty("orderedUnit")
    String orderedUnit,

    @JsonProperty("scheduledDeliveryDate")
    LocalDate scheduledDeliveryDate,

    @JsonProperty("scheduledDeliveryTime")
    LocalTime scheduledDeliveryTime,

    @JsonProperty("status")
    String status,

    @JsonProperty("notes")
    String notes,

    @JsonProperty("receivedQuantity")
    BigDecimal receivedQuantity,

    @JsonProperty("receivedAt")
    String receivedAt,

    @JsonProperty("createdBy")
    String createdBy,

    @JsonProperty("createdAt")
    String createdAt
) {}


