package org.thomcgn.backend.reservation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.thomcgn.backend.reservation.domain.BookingIntervalMode;
import org.thomcgn.backend.reservation.service.ReservationService;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Public reservation clock, no-show grace, interval mode and opening windows")
public record ReservationSettingsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate today,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Europe/Berlin") String timezone,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Reserved for bounded stays; null because checked-in tables remain occupied until checkout or archive") Integer durationMinutes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Minutes after arrival before an unattended reservation becomes a no-show") int graceMinutes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int intervalMinutes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BookingIntervalMode mode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OpeningHoursResponse> openingHours
) {
    public static ReservationSettingsResponse from(ReservationService.SettingsResponse settings) {
        return new ReservationSettingsResponse(settings.today(), settings.timezone(), settings.durationMinutes(),
                settings.graceMinutes(), settings.intervalMinutes(), settings.mode(),
                settings.openingHours().stream().map(OpeningHoursResponse::from).toList());
    }
}
