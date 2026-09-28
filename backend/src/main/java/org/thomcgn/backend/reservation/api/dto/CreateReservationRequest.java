package org.thomcgn.backend.reservation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Create Reservation Request contract")
public record CreateReservationRequest(
        @NotBlank @Size(max = 160) String guestName,
        @Schema(nullable = true)
        @Email @Size(max = 255) String contactEmail,
        @Schema(nullable = true)
        @Size(max = 80) String contactPhone,
        @NotNull LocalDate reservationDate,
        @NotNull LocalTime reservationTime,
        @NotNull @Min(1) Integer guestCount
) {
}

