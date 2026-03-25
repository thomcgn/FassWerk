package org.thomcgn.backend.billing.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SplitTableOrderPaymentRequest(
        @NotEmpty List<@Valid SplitTableOrderItemRequest> items
) {
}

