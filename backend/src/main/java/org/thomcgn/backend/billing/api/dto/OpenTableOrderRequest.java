package org.thomcgn.backend.billing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Open Table Order Request contract")
public record OpenTableOrderRequest(
        @NotNull Long tableId,
        @Schema(nullable = true) Long reservationId
) {
}

