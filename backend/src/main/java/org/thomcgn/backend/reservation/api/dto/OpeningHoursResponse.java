package org.thomcgn.backend.reservation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.thomcgn.backend.reservation.application.ReservationRules;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Schema(description = "Opening window for one weekday; secondFrom/secondTo describe an optional split window")
public record OpeningHoursResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DayOfWeek weekday,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean open,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalTime from,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalTime to,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalTime secondFrom,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalTime secondTo
) {
    public static OpeningHoursResponse from(ReservationRules.Hours hours) {
        return new OpeningHoursResponse(hours.weekday(), hours.open(), hours.from(), hours.to(),
                hours.secondFrom(), hours.secondTo());
    }
}
