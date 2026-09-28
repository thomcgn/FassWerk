package org.thomcgn.backend.billing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Split Table Order Item Request contract")
public record SplitTableOrderItemRequest(
        @NotNull Long itemId,
        @NotNull @Min(1) Integer quantity
) {
}

