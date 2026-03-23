package org.thomcgn.backend.billing.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddTableOrderItemRequest(
        @NotNull Long drinkVariantId,
        @NotNull @Min(1) Integer quantity
) {
}

