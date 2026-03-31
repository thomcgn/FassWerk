package org.thomcgn.backend.inventory.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReorderOrderRequest(
    @JsonProperty("inventoryItemId")
    Long inventoryItemId,

    @JsonProperty("supplierId")
    Long supplierId,

    @JsonProperty("orderedQuantity")
    BigDecimal orderedQuantity,

    @JsonProperty("orderedUnit")
    String orderedUnit,

    @JsonProperty("scheduledDeliveryDate")
    LocalDate scheduledDeliveryDate,

    @JsonProperty("scheduledDeliveryTime")
    LocalTime scheduledDeliveryTime,

    @JsonProperty("notes")
    String notes
) {}


