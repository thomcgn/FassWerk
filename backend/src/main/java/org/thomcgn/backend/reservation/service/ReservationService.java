package org.thomcgn.backend.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.common.exception.*;
import org.thomcgn.backend.common.persistence.BookingMutationLock;
import org.thomcgn.backend.qr.QrCodeService;
import org.thomcgn.backend.qr.QrProperties;
import org.thomcgn.backend.reservation.api.dto.CreateReservationRequest;
import org.thomcgn.backend.reservation.api.dto.ReservationScanResponse;
import org.thomcgn.backend.reservation.application.ReservationRules;
import org.thomcgn.backend.reservation.domain.*;
import org.thomcgn.backend.reservation.repository.*;
import org.thomcgn.backend.table.domain.TableEntity;
import org.thomcgn.backend.table.repository.TableRepository;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReservationService implements org.thomcgn.backend.reservation.application.ReservationTableUsage {
    private static final Set<ReservationStatus> ACTIVE = EnumSet.of(
            ReservationStatus.PENDING, ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN);
    private final ReservationRepository reservationRepository;
    private final OpeningHourRepository openingHourRepository;
    private final BookingSlotConfigRepository bookingSlotConfigRepository;
    private final TableRepository tableRepository;
    private final QrCodeService qrCodeService;
    private final QrProperties qrProperties;
    private final BookingMutationLock bookingLock;
    private final Clock reservationClock;
    private final ApplicationEventPublisher events;
    private final org.thomcgn.backend.reservation.application.ReservationBilling billing;

    @Transactional
    public Reservation createReservation(CreateReservationRequest request) {
        bookingLock.acquire();
        Reservation reservation = new Reservation();
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setQrCodeToken(generateToken());
        applyRequest(reservation, request);
        return reservationRepository.save(reservation);
    }

    @Transactional
    public Reservation update(Long id, CreateReservationRequest request) {
        bookingLock.acquire();
        Reservation reservation = getById(id);
        requireEditable(reservation);
        applyRequest(reservation, request);
        Reservation saved = reservationRepository.save(reservation);
        if (saved.getStatus() == ReservationStatus.CONFIRMED) notifyDecision(saved, null);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Reservation> listByDate(LocalDate date) {
        return reservationRepository.findByReservationDateOrderByReservationTimeAsc(
                date == null ? LocalDate.now(reservationClock) : date);
    }

    @Transactional(readOnly = true)
    public Reservation getById(Long id) {
        return reservationRepository.findById(id).orElseThrow(() -> new NotFoundException("Reservation not found: " + id));
    }

    @Transactional(readOnly = true)
    public ReservationScanResponse scanToken(String token) {
        Reservation reservation = reservationRepository.findByQrCodeToken(token)
                .orElseThrow(() -> new NotFoundException("Reservation token not found"));
        return new ReservationScanResponse(reservation.getId(), reservation.getGuestName(), reservation.getReservationDate(),
                reservation.getReservationTime(), reservation.getStatus(), isCheckInAllowed(reservation));
    }

    @Transactional
    public Reservation checkIn(Long id) {
        bookingLock.acquire();
        Reservation reservation = getById(id);
        if (reservation.getStatus() == ReservationStatus.CHECKED_IN) {
            billing.openForCheckIn(id, tableIds(reservation));
            return reservation;
        }
        if (!isCheckInAllowed(reservation)) throw new ConflictException("Check-in is not allowed");
        billing.openForCheckIn(id, tableIds(reservation));
        reservation.setStatus(ReservationStatus.CHECKED_IN);
        reservation.setCheckedInAt(LocalDateTime.ofInstant(reservationClock.instant(), zone(reservation)));
        return reservationRepository.save(reservation);
    }

    @Transactional
    public Reservation confirm(Long id) {
        bookingLock.acquire();
        Reservation reservation = getById(id);
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) return reservation;
        if (reservation.getStatus() != ReservationStatus.PENDING) throw new ConflictException("Only pending reservations can be confirmed");
        if (reservation.getStartsAt() == null || reservation.getAssignedTables().isEmpty())
            throw new ConflictException("Legacy reservation must be updated and assigned before confirmation");
        if (!reservationClock.instant().isBefore(deadline(reservation))) throw new ConflictException("Reservation has expired");
        reservation.setStatus(ReservationStatus.CONFIRMED);
        Reservation saved = reservationRepository.save(reservation);
        notifyDecision(saved, null);
        return saved;
    }

    @Transactional
    public Reservation cancel(Long id, String reason) {
        bookingLock.acquire();
        Reservation reservation = getById(id);
        if (reservation.getStatus() == ReservationStatus.REJECTED || reservation.getStatus() == ReservationStatus.CANCELLED) return reservation;
        requireEditable(reservation);
        reservation.setStatus(reservation.getStatus() == ReservationStatus.PENDING ? ReservationStatus.REJECTED : ReservationStatus.CANCELLED);
        Reservation saved = reservationRepository.save(reservation);
        notifyDecision(saved, reason);
        return saved;
    }

    @Transactional
    public Reservation complete(Long id) {
        bookingLock.acquire();
        Reservation reservation = getById(id);
        if (reservation.getStatus() == ReservationStatus.COMPLETED) return reservation;
        if (reservation.getStatus() != ReservationStatus.CHECKED_IN) throw new ConflictException("Only checked-in reservations can be completed");
        if (reservation.getAssignedTables().isEmpty() || !billing.settledTableIds(id).containsAll(tableIds(reservation)))
            throw new ConflictException("Reservation remains occupied until every table bill is fully paid");
        reservation.setStatus(ReservationStatus.COMPLETED);
        return reservationRepository.save(reservation);
    }

    @org.springframework.context.event.EventListener
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void onTableOrderPaid(org.thomcgn.backend.billing.application.TableOrderPaid event) {
        if (event.reservationId() == null) return;
        var reservation = getById(event.reservationId());
        if (reservation.getStatus() == ReservationStatus.CHECKED_IN
                && !reservation.getAssignedTables().isEmpty()
                && billing.settledTableIds(reservation.getId()).containsAll(tableIds(reservation))) {
            reservation.setStatus(ReservationStatus.COMPLETED);
        }
    }

    @Transactional(readOnly = true)
    public byte[] generateReservationQrCode(Long id) {
        return qrCodeService.generateQrPng(qrProperties.scanBaseUrl().replaceAll("/+$", "")
                + "/bookings/scan/" + getById(id).getQrCodeToken());
    }

    @Scheduled(cron = "${app.reservation.no-show-cron:0 * * * * *}")
    @Transactional
    public void markNoShows() {
        bookingLock.acquire();
        for (Reservation r : reservationRepository.findByStatusIn(EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED))) {
            if (!reservationClock.instant().isBefore(deadline(r))) r.setStatus(ReservationStatus.NO_SHOW);
        }
    }

    @Transactional(readOnly = true)
    public SettingsResponse settings() {
        var config = config();
        return new SettingsResponse(LocalDate.now(reservationClock), reservationClock.getZone().getId(),
                null, ReservationRules.NO_SHOW_MINUTES,
                config.getSlotDurationMinutes(), config.getBookingIntervalMode(), hours());
    }

    public record SettingsResponse(LocalDate today, String timezone, Integer durationMinutes, int graceMinutes,
                                   int intervalMinutes, BookingIntervalMode mode, List<ReservationRules.Hours> openingHours) {}

    @Override
    @Transactional(readOnly = true)
    public boolean hasCurrentOrFutureHold(Long tableId) {
        if (billing.occupiedTableIds().contains(tableId)) return true;
        return reservationRepository.findByStatusIn(ACTIVE).stream()
                .filter(this::stillHoldsTables)
                .anyMatch(r -> heldTableIds(r).contains(tableId));
    }

    @Override
    @Transactional(readOnly = true)
    public void assertWalkInAvailable(Long tableId) {
        var now = LocalDateTime.now(reservationClock);
        var businessDate = ReservationRules.businessDate(now.toLocalDate(), now.toLocalTime(), hours());
        for (var reservation : reservationRepository.findByStatusIn(ACTIVE)) {
            if (stillHoldsTables(reservation)
                    && (reservation.getStatus() == ReservationStatus.CHECKED_IN || businessDate(reservation).equals(businessDate))
                    && heldTableIds(reservation).contains(tableId))
                throw new ConflictException("Table is held by a reservation; use its check-in");
        }
    }

    private boolean stillHoldsTables(Reservation r) {
        return r.getStatus() == ReservationStatus.CHECKED_IN || reservationClock.instant().isBefore(deadline(r));
    }

    private List<Long> tableIds(Reservation r) {
        return r.getAssignedTables().stream().map(TableEntity::getId).toList();
    }

    private List<Long> heldTableIds(Reservation r) {
        if (r.getStatus() != ReservationStatus.CHECKED_IN) return tableIds(r);
        var paid = billing.settledTableIds(r.getId());
        return tableIds(r).stream().filter(id -> !paid.contains(id)).toList();
    }

    private LocalDate businessDate(Reservation r) {
        return r.getBusinessDate() != null ? r.getBusinessDate()
                : ReservationRules.businessDate(r.getReservationDate(), r.getReservationTime(), hours());
    }

    private void applyRequest(Reservation r, CreateReservationRequest request) {
        if (request.guestName() == null || request.guestName().isBlank() || request.guestCount() == null || request.guestCount() < 1)
            throw new BadRequestException("Guest name and positive guestCount are required");
        var cfg = config();
        var schedule = ReservationRules.schedule(request.reservationDate(), request.reservationTime(),
                new ReservationRules.Settings(cfg.getSlotDurationMinutes(), cfg.getBookingIntervalMode()), hours(), reservationClock);
        Set<Long> occupied = new HashSet<>(billing.occupiedTableIds());
        for (Reservation other : reservationRepository.findByStatusIn(ACTIVE)) {
            if (Objects.equals(other.getId(), r.getId()) || !stillHoldsTables(other)) continue;
            if (other.getStatus() != ReservationStatus.CHECKED_IN && !businessDate(other).equals(schedule.businessDate())) continue;
            if (other.getAssignedTables().isEmpty())
                throw new ConflictException("An active legacy reservation requires table assignment");
            occupied.addAll(heldTableIds(other));
        }
        List<TableEntity> candidates = tableRepository.findAll().stream()
                .filter(t -> t.isActive() && t.getSeats() != null && t.getStatus() == org.thomcgn.backend.table.domain.TableStatus.FREE && !occupied.contains(t.getId())).toList();
        List<Long> ids = ReservationRules.allocate(candidates.stream()
                .map(t -> new ReservationRules.Seats(t.getId(), t.getArea(), t.getSeats())).toList(), request.guestCount());
        r.setGuestName(request.guestName().trim());
        r.setContactEmail(emptyToNull(request.contactEmail()));
        r.setContactPhone(emptyToNull(request.contactPhone()));
        r.setGuestCount(request.guestCount());
        r.setReservationDate(request.reservationDate());
        r.setReservationTime(request.reservationTime());
        r.setDurationMinutes(null);
        r.setStartsAt(schedule.start());
        r.setEndsAt(null);
        r.setCheckInDeadline(schedule.deadline());
        r.setBusinessDate(schedule.businessDate());
        r.setReservationZone(schedule.zone());
        r.setExpiresAt(LocalDateTime.ofInstant(schedule.deadline(), ZoneId.of(schedule.zone())));
        r.getAssignedTables().clear();
        r.getAssignedTables().addAll(candidates.stream().filter(t -> ids.contains(t.getId())).toList());
        r.setAssignedTable(r.getAssignedTables().getFirst());
    }

    private boolean isCheckInAllowed(Reservation r) {
        if (r.getStatus() != ReservationStatus.CONFIRMED || r.getCheckedInAt() != null
                || r.getStartsAt() == null || r.getAssignedTables().isEmpty()) return false;
        // Arrival grace is independent of stay length; payment alone ends a checked-in hold.
        Duration grace = Duration.between(r.getStartsAt(), deadline(r));
        return !reservationClock.instant().isBefore(r.getStartsAt().minus(grace))
                && reservationClock.instant().isBefore(deadline(r));
    }

    private Instant deadline(Reservation r) {
        Instant start = r.getStartsAt() != null ? r.getStartsAt()
                : r.getReservationDate().atTime(r.getReservationTime()).atZone(zone(r)).toInstant();
        return ReservationRules.checkInDeadline(start);
    }

    private ZoneId zone(Reservation r) {
        return r.getReservationZone() == null ? reservationClock.getZone() : ZoneId.of(r.getReservationZone());
    }

    private void requireEditable(Reservation r) {
        if (r.getStatus() != ReservationStatus.PENDING && r.getStatus() != ReservationStatus.CONFIRMED)
            throw new ConflictException("Only pending or confirmed reservations can be changed");
    }

    private BookingSlotConfig config() {
        return bookingSlotConfigRepository.findFirstByActiveTrueOrderByIdDesc()
                .orElseThrow(() -> new ConflictException("No active booking configuration"));
    }

    private List<ReservationRules.Hours> hours() {
        return openingHourRepository.findAll().stream().map(h -> new ReservationRules.Hours(h.getWeekday(), h.isOpen(),
                h.getOpenTime(), h.getCloseTime(), h.getSecondOpenTime(), h.getSecondCloseTime())).toList();
    }

    private void notifyDecision(Reservation r, String reason) {
        events.publishEvent(new ReservationMailEvent(r.getId(), r.getContactEmail(), r.getGuestName(),
                r.getReservationDate().atTime(r.getReservationTime()), LocalDateTime.ofInstant(deadline(r), zone(r)), r.getGuestCount(), r.getStatus(), reason));
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String emptyToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
