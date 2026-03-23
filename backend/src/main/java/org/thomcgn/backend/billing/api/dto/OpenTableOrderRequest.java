package org.thomcgn.backend.billing.api.dto;

import jakarta.validation.constraints.NotNull;

public record OpenTableOrderRequest(
        @NotNull Long tableId,
        Long reservationId
) {
}

