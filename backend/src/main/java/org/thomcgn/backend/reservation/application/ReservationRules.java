package org.thomcgn.backend.reservation.application;

import org.thomcgn.backend.common.exception.BadRequestException;
import org.thomcgn.backend.common.exception.ConflictException;
import org.thomcgn.backend.reservation.domain.BookingIntervalMode;
import java.time.*;
import java.util.*;

/** The authoritative reservation policy; controllers and UI only submit/display data. */
public final class ReservationRules {
    private ReservationRules() {}
    public static final int NO_SHOW_MINUTES = 30;
    public record Settings(int intervalMinutes, BookingIntervalMode mode) {}
    public record Hours(DayOfWeek weekday, boolean open, LocalTime from, LocalTime to, LocalTime secondFrom, LocalTime secondTo) {}
    public record Schedule(Instant start, Instant deadline, LocalDate businessDate, String zone) {}
    public record Seats(long id, String area, int count) {}

    public static Schedule schedule(LocalDate date, LocalTime time, Settings settings, List<Hours> hours, Clock clock) {
        if (date == null || time == null || time.getSecond() != 0 || time.getNano() != 0)
            throw new BadRequestException("A date and minute-precise time are required");
        if (settings.intervalMinutes() < 1 || settings.mode() == null)
            throw new ConflictException("Booking configuration is invalid");
        LocalDateTime local = date.atTime(time);
        ZoneId zone = clock.getZone();
        if (zone.getRules().getValidOffsets(local).size() != 1)
            throw new BadRequestException("Reservation time is ambiguous or does not exist in the venue timezone");
        Instant start = local.atZone(zone).toInstant();
        if (start.isBefore(clock.instant())) throw new BadRequestException("Reservation must not be in the past");
        for (LocalDate openingDate : List.of(date.minusDays(1), date)) {
            for (Hours h : hours) {
                if (!h.open() || h.weekday() != openingDate.getDayOfWeek()) continue;
                for (LocalTime[] window : List.of(new LocalTime[]{h.from(), h.to()}, new LocalTime[]{h.secondFrom(), h.secondTo()})) {
                    if (window[0] == null || window[1] == null || window[0].equals(window[1])) continue;
                    LocalDateTime from = openingDate.atTime(window[0]);
                    LocalDateTime to = openingDate.plusDays(window[1].isAfter(window[0]) ? 0 : 1).atTime(window[1]);
                    Instant begins = from.atZone(zone).toInstant();
                    Instant closes = to.atZone(zone).toInstant();
                    if (start.isBefore(begins) || !start.isBefore(closes)) continue;
                    if (settings.mode() == BookingIntervalMode.FIXED
                            && Duration.between(from, local).toMinutes() % settings.intervalMinutes() != 0)
                        throw new BadRequestException("Reservation time does not match booking interval");
                    return new Schedule(start, checkInDeadline(start), openingDate, zone.getId());
                }
            }
        }
        throw new ConflictException("Reservation arrival must be within an opening window");
    }

    public static List<Long> allocate(List<Seats> available, int guests) {
        if (guests < 1) throw new BadRequestException("guestCount must be positive");
        var usable = available.stream().filter(t -> t.count() > 0).toList();
        var single = usable.stream().filter(t -> t.count() >= guests)
                .min(Comparator.comparingInt(Seats::count).thenComparingLong(Seats::id));
        if (single.isPresent()) return List.of(single.get().id());
        Map<String, List<Seats>> areas = new TreeMap<>();
        usable.forEach(t -> areas.computeIfAbsent(t.area() == null ? "" : t.area(), ignored -> new ArrayList<>()).add(t));
        List<Long> best = null;
        for (List<Seats> area : areas.values()) {
            area.sort(Comparator.comparingInt(Seats::count).reversed().thenComparingLong(Seats::id));
            long capacity = 0;
            List<Long> selected = new ArrayList<>();
            for (Seats table : area) {
                selected.add(table.id());
                capacity += table.count();
                if (capacity >= guests) {
                    if (best == null || selected.size() < best.size()) best = List.copyOf(selected);
                    break;
                }
            }
        }
        if (best == null) throw new ConflictException("Not enough configured seats in one area");
        return best;
    }

    public static Instant checkInDeadline(Instant start) {
        return start.plusSeconds(NO_SHOW_MINUTES * 60L);
    }

    public static LocalDate businessDate(LocalDate date, LocalTime time, List<Hours> hours) {
        for (Hours h : hours) {
            if (!h.open() || h.weekday() != date.minusDays(1).getDayOfWeek()) continue;
            for (LocalTime[] window : List.of(new LocalTime[]{h.from(), h.to()}, new LocalTime[]{h.secondFrom(), h.secondTo()})) {
                if (window[0] != null && window[1] != null && window[1].isBefore(window[0]) && time.isBefore(window[1]))
                    return date.minusDays(1);
            }
        }
        return date;
    }
}
