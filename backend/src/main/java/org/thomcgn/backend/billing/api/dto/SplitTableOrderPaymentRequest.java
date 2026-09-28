package org.thomcgn.backend.billing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Split Table Order Payment Request contract")
public record SplitTableOrderPaymentRequest(
        @NotEmpty List<@Valid SplitTableOrderItemRequest> items
) {
}

