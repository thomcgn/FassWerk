package org.thomcgn.backend.reservation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.thomcgn.backend.reservation.domain.ReservationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Schema(description = "Reservation Response contract")
public record ReservationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String guestName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String contactEmail,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String contactPhone,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate reservationDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalTime reservationTime,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer guestCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReservationStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalDateTime expiresAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalDateTime checkedInAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String qrCodeToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String qrScanUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) java.util.List<Long> assignedTableIds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Integer durationMinutes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) java.time.Instant startsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) java.time.Instant endsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate businessDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String timezone
) {
}

