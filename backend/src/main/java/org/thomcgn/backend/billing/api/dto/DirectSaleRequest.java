package org.thomcgn.backend.billing.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.thomcgn.backend.billing.domain.PaymentMethod;
import java.math.BigDecimal;
import java.util.List;

@io.swagger.v3.oas.annotations.media.Schema(description = "Atomic direct sale with confirmed prices and received payment")
public record DirectSaleRequest(
        @NotNull PaymentMethod paymentMethod,
        @NotEmpty @Size(min = 1, max = 100) List<@NotNull @Valid Item> items
) {
    @io.swagger.v3.oas.annotations.media.Schema(description = "Direct sale cart item with confirmed unit price")
    public record Item(@NotNull @Positive Long drinkVariantId,
                       @NotNull @Min(1) @Max(1000) Integer quantity,
                       @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal expectedUnitPrice) {}
}
