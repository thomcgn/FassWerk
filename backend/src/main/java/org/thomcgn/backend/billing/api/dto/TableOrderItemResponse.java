package org.thomcgn.backend.billing.api.dto;

import java.math.BigDecimal;

public record TableOrderItemResponse(
        Long id,
        Long drinkVariantId,
        String drinkLabel,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        BigDecimal deductedVolumeMl
) {
}

