package org.thomcgn.backend.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.common.exception.BadRequestException;
import org.thomcgn.backend.common.exception.ConflictException;
import org.thomcgn.backend.common.exception.NotFoundException;
import org.thomcgn.backend.qr.QrCodeService;
import org.thomcgn.backend.qr.QrProperties;
import org.thomcgn.backend.reservation.api.dto.CreateReservationRequest;
import org.thomcgn.backend.reservation.api.dto.ReservationScanResponse;
import org.thomcgn.backend.reservation.domain.BookingSlotConfig;
import org.thomcgn.backend.reservation.domain.OpeningHour;
import org.thomcgn.backend.reservation.domain.Reservation;
import org.thomcgn.backend.reservation.domain.ReservationStatus;
import org.thomcgn.backend.reservation.repository.BookingSlotConfigRepository;
import org.thomcgn.backend.reservation.repository.OpeningHourRepository;
import org.thomcgn.backend.reservation.repository.ReservationRepository;
import org.thomcgn.backend.table.repository.TableRepository;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final EnumSet<ReservationStatus> ACTIVE_SLOT_STATUSES = EnumSet.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN
    );

    private final ReservationRepository reservationRepository;
    private final OpeningHourRepository openingHourRepository;
    private final BookingSlotConfigRepository bookingSlotConfigRepository;
    private final TableRepository tableRepository;
    private final QrCodeService qrCodeService;
    private final QrProperties qrProperties;
    private final ReservationMailService reservationMailService;

    @Transactional
    public Reservation createReservation(CreateReservationRequest request) {
        BookingSlotConfig slotConfig = getActiveSlotConfig();
        validateOpeningHours(request.reservationDate(), request.reservationTime(), slotConfig.getSlotDurationMinutes());
        validateCapacity(request.reservationDate(), request.reservationTime(), request.guestCount());

        Reservation reservation = new Reservation();
        reservation.setGuestName(request.guestName().trim());
        reservation.setContactEmail(emptyToNull(request.contactEmail()));
        reservation.setContactPhone(emptyToNull(request.contactPhone()));
        reservation.setReservationDate(request.reservationDate());
        reservation.setReservationTime(request.reservationTime());
        reservation.setGuestCount(request.guestCount());
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setQrCodeToken(generateToken());
        reservation.setExpiresAt(LocalDateTime.of(request.reservationDate(), request.reservationTime())
                .plusMinutes(slotConfig.getNoShowGracePeriodMinutes()));

        return reservationRepository.save(reservation);
    }

    @Transactional(readOnly = true)
    public List<Reservation> listByDate(LocalDate date) {
        return reservationRepository.findByReservationDateOrderByReservationTimeAsc(date);
    }

    @Transactional(readOnly = true)
    public Reservation getById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + id));
    }

    @Transactional(readOnly = true)
    public ReservationScanResponse scanToken(String token) {
        Reservation reservation = findByToken(token);
        boolean checkInAllowed = isCheckInAllowed(reservation);
        return new ReservationScanResponse(
                reservation.getId(),
                reservation.getGuestName(),
                reservation.getReservationDate(),
                reservation.getReservationTime(),
                reservation.getStatus(),
                checkInAllowed
        );
    }

    @Transactional
    public Reservation checkIn(Long reservationId) {
        Reservation reservation = getById(reservationId);
        if (!isCheckInAllowed(reservation)) {
            throw new ConflictException("Check-in is not allowed for reservation " + reservationId);
        }

        reservation.setStatus(ReservationStatus.CHECKED_IN);
        reservation.setCheckedInAt(LocalDateTime.now());
        return reservationRepository.save(reservation);
    }

    @Transactional
    public Reservation confirm(Long reservationId) {
        Reservation reservation = getById(reservationId);
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new ConflictException("Only pending reservations can be confirmed");
        }

        reservation.setStatus(ReservationStatus.CONFIRMED);
        Reservation saved = reservationRepository.save(reservation);
        reservationMailService.sendConfirmedMail(saved);
        return saved;
    }

    @Transactional
    public Reservation cancel(Long reservationId, String reason) {
        Reservation reservation = getById(reservationId);
        if (reservation.getStatus() == ReservationStatus.CHECKED_IN || reservation.getStatus() == ReservationStatus.COMPLETED) {
            throw new ConflictException("Checked-in or completed reservations cannot be rejected");
        }
        reservation.setStatus(ReservationStatus.REJECTED);
        Reservation saved = reservationRepository.save(reservation);
        reservationMailService.sendRejectedMail(saved, reason);
        return saved;
    }

    @Transactional(readOnly = true)
    public byte[] generateReservationQrCode(Long reservationId) {
        Reservation reservation = getById(reservationId);
        String url = qrProperties.scanBaseUrl() + "/api/reservations/scan/" + reservation.getQrCodeToken();
        return qrCodeService.generateQrPng(url);
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void markNoShows() {
        BookingSlotConfig slotConfig;
        try {
            slotConfig = getActiveSlotConfig();
        } catch (NotFoundException ignored) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        List<Reservation> unattendedReservations = reservationRepository.findUnattendedByDateAndStatuses(today, ACTIVE_SLOT_STATUSES);
        unattendedReservations.stream()
                .filter(reservation -> now.isAfter(LocalDateTime.of(reservation.getReservationDate(), reservation.getReservationTime())
                        .plusMinutes(slotConfig.getNoShowGracePeriodMinutes())))
                .forEach(reservation -> reservation.setStatus(ReservationStatus.NO_SHOW));
    }

    private Reservation findByToken(String token) {
        return reservationRepository.findByQrCodeToken(token)
                .orElseThrow(() -> new NotFoundException("Reservation token not found"));
    }

    private boolean isCheckInAllowed(Reservation reservation) {
        return reservation.getReservationDate().isEqual(LocalDate.now())
                && reservation.getStatus() == ReservationStatus.CONFIRMED
                && reservation.getCheckedInAt() == null;
    }

    private void validateCapacity(LocalDate date, LocalTime time, int guestCount) {
        long totalActiveTables = tableRepository.getTotalActiveTables();
        if (totalActiveTables <= 0) {
            throw new ConflictException("No active tables configured");
        }

        long occupiedTables = reservationRepository.getReservationCountForSlot(date, time, ACTIVE_SLOT_STATUSES);
        if (occupiedTables >= totalActiveTables) {
            throw new ConflictException("No free tables available for this slot");
        }
    }

    private void validateOpeningHours(LocalDate date, LocalTime time, int slotDurationMinutes) {
        DayOfWeek weekday = date.getDayOfWeek();
        OpeningHour openingHour = openingHourRepository.findByWeekday(weekday)
                .orElseThrow(() -> new ConflictException("Opening hour config missing for " + weekday));

        if (!openingHour.isOpen()) {
            throw new ConflictException("Day is closed for reservations");
        }

        boolean inPrimaryWindow = isWithinWindow(time, openingHour.getOpenTime(), openingHour.getCloseTime());
        boolean inSecondaryWindow = isWithinWindow(time, openingHour.getSecondOpenTime(), openingHour.getSecondCloseTime());
        if (!inPrimaryWindow && !inSecondaryWindow) {
            throw new ConflictException("Reservation time is outside opening hours");
        }

        if (time.getMinute() % slotDurationMinutes != 0) {
            throw new BadRequestException("Reservation time does not match slot duration");
        }
    }

    private BookingSlotConfig getActiveSlotConfig() {
        return bookingSlotConfigRepository.findFirstByActiveTrueOrderByIdDesc()
                .orElseThrow(() -> new NotFoundException("No active booking slot configuration found"));
    }

    private boolean isWithinWindow(LocalTime requestedTime, LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return false;
        }
        return !requestedTime.isBefore(start) && requestedTime.isBefore(end);
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

