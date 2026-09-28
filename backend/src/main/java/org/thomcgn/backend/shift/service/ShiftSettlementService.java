package org.thomcgn.backend.shift.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.billing.application.BillingRevenueQueries;
import org.thomcgn.backend.shift.api.dto.ShiftSettlementRequest;
import org.thomcgn.backend.shift.api.dto.ShiftSettlementResponse;
import org.thomcgn.backend.shift.api.dto.ShiftWorkerEntryRequest;
import org.thomcgn.backend.shift.api.dto.ShiftWorkerEntryResponse;
import org.thomcgn.backend.shift.domain.ShiftSettlement;
import org.thomcgn.backend.shift.domain.ShiftWorkerEntry;
import org.thomcgn.backend.shift.repository.ShiftSettlementRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShiftSettlementService {

    private final ShiftSettlementRepository settlementRepository;
    private final BillingRevenueQueries billingRevenue;
    private final org.thomcgn.backend.common.persistence.BookingMutationLock bookingLock;

    @Transactional(readOnly = true)
    public ShiftSettlementResponse getByDate(LocalDate date) {
        return settlementRepository.findBySettlementDate(date)
                .map(this::toResponse)
                .orElseGet(() -> emptyResponse(date));
    }

    @Transactional(readOnly = true)
    public List<ShiftSettlementResponse> listByRange(LocalDate from, LocalDate to) {
        return settlementRepository.findAllBySettlementDateBetweenOrderBySettlementDateDesc(from, to).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ShiftSettlementResponse saveByDate(LocalDate date, ShiftSettlementRequest request) {
        bookingLock.acquire();
        ShiftSettlement settlement = settlementRepository.findBySettlementDate(date).orElseGet(ShiftSettlement::new);
        if (request.expectedRevision() == null || request.expectedRevision() != settlement.getRevision()) {
            throw new org.thomcgn.backend.common.exception.ConflictException("Die Schichtabrechnung wurde geändert. Aktuellen Stand laden und den Entwurf vergleichen.");
        }
        settlement.setRevision(settlement.getRevision() + 1);
        settlement.setSettlementDate(date);
        settlement.setOpeningCash(request.openingCash());
        settlement.setOtherExpenses(request.otherExpenses());

        settlement.getEntries().clear();
        List<ShiftWorkerEntryRequest> requests = request.entries() != null ? request.entries() : List.of();
        for (ShiftWorkerEntryRequest entryRequest : requests) {
            ShiftWorkerEntry entry = new ShiftWorkerEntry();
            entry.setSettlement(settlement);
            entry.setEmployeeName(entryRequest.employeeName().trim());
            entry.setShiftStart(entryRequest.shiftStart());
            entry.setShiftEnd(entryRequest.shiftEnd());
            entry.setHourlyWage(entryRequest.hourlyWage());
            settlement.getEntries().add(entry);
        }

        return toResponse(settlementRepository.saveAndFlush(settlement));
    }

    private ShiftSettlementResponse emptyResponse(LocalDate date) {
        BigDecimal revenue = resolveDailyRevenue(date);
        return new ShiftSettlementResponse(
                null,
                date,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                revenue,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                billingRevenue.cashRevenue(date).setScale(2, RoundingMode.HALF_UP),
                List.of(),
                0L
        );
    }

    private ShiftSettlementResponse toResponse(ShiftSettlement settlement) {
        List<ShiftWorkerEntryResponse> entryResponses = new ArrayList<>();
        BigDecimal totalWages = BigDecimal.ZERO;

        List<ShiftWorkerEntry> sortedEntries = settlement.getEntries().stream()
                .sorted(Comparator.comparing(ShiftWorkerEntry::getEmployeeName))
                .toList();

        for (ShiftWorkerEntry entry : sortedEntries) {
            BigDecimal workedHours = resolveWorkedHours(entry.getShiftStart(), entry.getShiftEnd());
            BigDecimal wageCost = workedHours.multiply(entry.getHourlyWage()).setScale(2, RoundingMode.HALF_UP);
            totalWages = totalWages.add(wageCost);
            entryResponses.add(new ShiftWorkerEntryResponse(
                    entry.getId(),
                    entry.getEmployeeName(),
                    entry.getShiftStart(),
                    entry.getShiftEnd(),
                    entry.getHourlyWage(),
                    workedHours,
                    wageCost
            ));
        }

        totalWages = totalWages.setScale(2, RoundingMode.HALF_UP);
        BigDecimal revenue = resolveDailyRevenue(settlement.getSettlementDate());
        BigDecimal expectedClosingCash = settlement.getOpeningCash()
                .add(billingRevenue.cashRevenue(settlement.getSettlementDate()))
                .subtract(totalWages)
                .subtract(settlement.getOtherExpenses())
                .setScale(2, RoundingMode.HALF_UP);

        return new ShiftSettlementResponse(
                settlement.getId(),
                settlement.getSettlementDate(),
                settlement.getOpeningCash(),
                settlement.getOtherExpenses(),
                revenue,
                totalWages,
                expectedClosingCash,
                entryResponses,
                settlement.getRevision()
        );
    }

    private BigDecimal resolveWorkedHours(java.time.LocalTime start, java.time.LocalTime end) {
        LocalDate anchorDate = LocalDate.of(2000, 1, 1);
        LocalDateTime startDateTime = LocalDateTime.of(anchorDate, start);
        LocalDateTime endDateTime = LocalDateTime.of(anchorDate, end);
        if (!endDateTime.isAfter(startDateTime)) {
            endDateTime = endDateTime.plusDays(1);
        }
        Duration duration = Duration.between(startDateTime, endDateTime);
        BigDecimal minutes = BigDecimal.valueOf(duration.toMinutes());
        return minutes.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveDailyRevenue(LocalDate date) {
        return billingRevenue.businessDays(date, date.plusDays(1)).stream()
                .map(org.thomcgn.backend.billing.application.BusinessDayRevenue::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

}

