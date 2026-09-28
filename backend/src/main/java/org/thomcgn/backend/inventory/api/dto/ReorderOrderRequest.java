package org.thomcgn.backend.inventory.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Reorder Order Request contract")
public record ReorderOrderRequest(
    @NotNull
    @Positive
    @JsonProperty("inventoryItemId")
    Long inventoryItemId,

    @NotNull
    @Positive
    @JsonProperty("supplierId")
    Long supplierId,

    @NotNull
    @Positive
    @JsonProperty("orderedQuantity")
    BigDecimal orderedQuantity,

    @NotBlank
    @JsonProperty("orderedUnit")
    String orderedUnit,

    @NotNull
    @JsonProperty("scheduledDeliveryDate")
    LocalDate scheduledDeliveryDate,

    @JsonProperty("scheduledDeliveryTime")
    @Schema(nullable = true) LocalTime scheduledDeliveryTime,

    @JsonProperty("notes")
    @Schema(nullable = true) String notes
) {}


