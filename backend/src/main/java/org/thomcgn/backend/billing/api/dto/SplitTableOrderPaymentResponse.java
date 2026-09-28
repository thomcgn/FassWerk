package org.thomcgn.backend.billing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "Split Table Order Payment Response contract")
public record SplitTableOrderPaymentResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TableOrderResponse openOrder,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TableOrderResponse paidOrder
) {
}

