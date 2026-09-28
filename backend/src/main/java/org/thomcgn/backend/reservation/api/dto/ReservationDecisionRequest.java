package org.thomcgn.backend.reservation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "Reservation Decision Request contract")
public record ReservationDecisionRequest(
        @Schema(nullable = true) String reason
) {
}

