package org.thomcgn.backend.reservation;

import org.junit.jupiter.api.Test;
import org.thomcgn.backend.reservation.application.ReservationRules;
import org.thomcgn.backend.reservation.domain.BookingIntervalMode;
import org.thomcgn.backend.common.exception.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ReservationRulesTest {
    private final Clock clock = Clock.fixed(Instant.parse("2035-01-01T00:00:00Z"), ZoneId.of("Europe/Berlin"));
    private final ReservationRules.Settings fixed = new ReservationRules.Settings(30,BookingIntervalMode.FIXED);
    private List<ReservationRules.Hours> hours(LocalDate date,String start,String end) {
        return List.of(new ReservationRules.Hours(date.getDayOfWeek(),true,LocalTime.parse(start),LocalTime.parse(end),null,null));
    }

    @Test void midnightBelongsToPreviousOpeningBusinessDate() {
        LocalDate day=LocalDate.of(2035,6,1);
        var schedule=ReservationRules.schedule(day.plusDays(1),LocalTime.MIDNIGHT,fixed,hours(day,"18:00","03:00"),clock);
        assertThat(schedule.businessDate()).isEqualTo(day);
        assertThat(Duration.between(schedule.start(),schedule.deadline())).isEqualTo(Duration.ofMinutes(30));
    }

    @Test void arrivalMustFitButStayHasNoThreeHourLimit() {
        LocalDate day=LocalDate.of(2035,6,1);
        assertThat(ReservationRules.schedule(day,LocalTime.of(22,30),fixed,hours(day,"18:00","23:00"),clock)).isNotNull();
        assertThatThrownBy(() -> ReservationRules.schedule(day,LocalTime.of(23,0),fixed,hours(day,"18:00","23:00"),clock))
                .isInstanceOf(ConflictException.class);
    }

    @Test void fixedIntervalsAreRelativeToOpeningAndFlexibleDoesNotEnforceGrid() {
        LocalDate day=LocalDate.of(2035,6,1);
        assertThat(ReservationRules.schedule(day,LocalTime.of(18,45),fixed,hours(day,"18:15","23:30"),clock)).isNotNull();
        assertThatThrownBy(() -> ReservationRules.schedule(day,LocalTime.of(18,30),fixed,hours(day,"18:15","23:30"),clock)).isInstanceOf(BadRequestException.class);
        assertThat(ReservationRules.schedule(day,LocalTime.of(18,23),
                new ReservationRules.Settings(30,BookingIntervalMode.FLEXIBLE),hours(day,"18:15","23:30"),clock)).isNotNull();
    }

    @Test void rejectsDstGapAndAmbiguousClockHour() {
        Clock early=Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneId.of("Europe/Berlin"));
        for (LocalDate date: List.of(LocalDate.of(2026,3,29),LocalDate.of(2026,10,25))) {
            assertThatThrownBy(() -> ReservationRules.schedule(date,LocalTime.of(2,30),fixed,hours(date,"00:00","08:00"),early))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Test void noShowGraceAcrossDstIsThirtyElapsedMinutes() {
        LocalDate day=LocalDate.of(2035,3,25);
        // Select the actual DST transition rather than assuming a year-specific calendar date.
        while(day.getDayOfWeek()!=DayOfWeek.SUNDAY) day=day.plusDays(1);
        var schedule=ReservationRules.schedule(day,LocalTime.of(1,30),fixed,hours(day,"00:00","08:00"),clock);
        assertThat(Duration.between(schedule.start(),schedule.deadline())).isEqualTo(Duration.ofMinutes(30));
    }

    @Test void deadlineDisplayUsesElapsedMinutesAcrossDstAndIgnoresLegacyDuration() {
        var r=new org.thomcgn.backend.reservation.domain.Reservation();
        r.setReservationDate(LocalDate.of(2026,3,29));
        r.setReservationTime(LocalTime.of(1,30));
        r.setStartsAt(Instant.parse("2026-03-29T00:30:00Z"));
        r.setReservationZone("Europe/Berlin");
        r.setDurationMinutes(180);
        r.setQrCodeToken("test");
        var mapper=new org.thomcgn.backend.reservation.service.ReservationMapper(
                new org.thomcgn.backend.qr.QrProperties("http://localhost:3000"),clock);
        var response=mapper.toResponse(r);
        assertThat(response.expiresAt()).isEqualTo(LocalDateTime.of(2026,3,29,3,0));
        assertThat(response.durationMinutes()).isNull();
        assertThat(response.endsAt()).isNull();
    }

    @Test void combinesTablesButNotDifferentAreas() {
        var tables=List.of(new ReservationRules.Seats(1,"BAR",4),new ReservationRules.Seats(2,"BAR",4),
                new ReservationRules.Seats(3,"GARDEN",2));
        assertThat(ReservationRules.allocate(tables,6)).containsExactly(1L,2L);
        assertThatThrownBy(() -> ReservationRules.allocate(tables,9)).isInstanceOf(ConflictException.class);
    }

    @Test void prefersOneFittingTableAndRejectsUnknownCapacity() {
        assertThat(ReservationRules.allocate(List.of(new ReservationRules.Seats(1,"",8),
                new ReservationRules.Seats(2,"",4)),3)).containsExactly(2L);
        assertThatThrownBy(() -> ReservationRules.allocate(List.of(),1)).isInstanceOf(ConflictException.class);
    }

    @Test void arrivalsDuringClosedBreakAreRejected() {
        LocalDate day=LocalDate.of(2035,6,1);
        var windows=List.of(new ReservationRules.Hours(day.getDayOfWeek(),true,LocalTime.of(10,0),LocalTime.of(14,0),LocalTime.of(17,0),LocalTime.of(23,0)));
        assertThatThrownBy(() -> ReservationRules.schedule(day,LocalTime.of(15,0),fixed,windows,clock)).isInstanceOf(ConflictException.class);
        assertThat(ReservationRules.schedule(day,LocalTime.of(18,0),fixed,windows,clock)).isNotNull();
    }
}
