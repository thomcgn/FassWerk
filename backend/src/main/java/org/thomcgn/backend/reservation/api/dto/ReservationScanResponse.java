package org.thomcgn.backend.reservation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.thomcgn.backend.reservation.domain.ReservationStatus;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Reservation Scan Response contract")
public record ReservationScanResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long reservationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String guestName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate reservationDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalTime reservationTime,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReservationStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean checkInAllowed
) {
}

