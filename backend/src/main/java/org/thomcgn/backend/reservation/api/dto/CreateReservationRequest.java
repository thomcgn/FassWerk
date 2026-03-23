package org.thomcgn.backend.reservation.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateReservationRequest(
        @NotBlank String guestName,
        @Email String contactEmail,
        String contactPhone,
        @NotNull LocalDate reservationDate,
        @NotNull LocalTime reservationTime,
        @NotNull @Min(1) Integer guestCount
) {
}

