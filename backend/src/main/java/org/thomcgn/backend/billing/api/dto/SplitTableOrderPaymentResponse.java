package org.thomcgn.backend.billing.api.dto;

public record SplitTableOrderPaymentResponse(
        TableOrderResponse openOrder,
        TableOrderResponse paidOrder
) {
}

