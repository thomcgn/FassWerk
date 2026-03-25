package org.thomcgn.backend.report.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.billing.domain.TableOrder;
import org.thomcgn.backend.billing.domain.TableOrderStatus;
import org.thomcgn.backend.billing.repository.TableOrderItemRepository;
import org.thomcgn.backend.billing.repository.TableOrderRepository;
import org.thomcgn.backend.report.api.dto.RevenueDayPointResponse;
import org.thomcgn.backend.report.api.dto.RevenueOverviewResponse;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RevenueReportService {

    private final TableOrderRepository tableOrderRepository;
    private final TableOrderItemRepository tableOrderItemRepository;

    @Transactional(readOnly = true)
    public RevenueOverviewResponse getOverview() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = today.withDayOfMonth(1);

        BigDecimal dayRevenue = sumForRange(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        BigDecimal weekRevenue = sumForRange(weekStart.atStartOfDay(), today.plusDays(1).atStartOfDay());
        BigDecimal monthRevenue = sumForRange(monthStart.atStartOfDay(), today.plusDays(1).atStartOfDay());
        BigDecimal dayConsumedMl = consumedMlForRange(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        BigDecimal weekConsumedMl = consumedMlForRange(weekStart.atStartOfDay(), today.plusDays(1).atStartOfDay());
        BigDecimal monthConsumedMl = consumedMlForRange(monthStart.atStartOfDay(), today.plusDays(1).atStartOfDay());

        List<TableOrder> weekOrders = tableOrderRepository.findAllByStatusAndClosedAtBetween(
                TableOrderStatus.CLOSED,
                weekStart.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        );

        List<TableOrder> monthOrders = tableOrderRepository.findAllByStatusAndClosedAtBetween(
                TableOrderStatus.CLOSED,
                monthStart.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        );

        Map<DayOfWeek, BigDecimal> weekMap = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            weekMap.put(day, BigDecimal.ZERO);
        }

        for (TableOrder order : weekOrders) {
            DayOfWeek day = order.getClosedAt().getDayOfWeek();
            BigDecimal orderTotal = tableOrderItemRepository.getTotalByTableOrderId(order.getId());
            weekMap.put(day, weekMap.get(day).add(orderTotal));
        }

        String strongestWeekday = weekMap.entrySet().stream()
                .max(Comparator.comparing(Map.Entry::getValue))
                .map(entry -> toGermanDay(entry.getKey()))
                .orElse("-");

        List<RevenueDayPointResponse> weekPoints = buildWeekPoints(weekMap);
        List<RevenueDayPointResponse> monthPoints = buildMonthPoints(monthOrders, monthStart, today);

        return new RevenueOverviewResponse(
                dayRevenue,
                weekRevenue,
                monthRevenue,
                dayConsumedMl,
                weekConsumedMl,
                monthConsumedMl,
                strongestWeekday,
                weekPoints,
                monthPoints
        );
    }

    private BigDecimal sumForRange(LocalDateTime start, LocalDateTime end) {
        List<TableOrder> orders = tableOrderRepository.findAllByStatusAndClosedAtBetween(TableOrderStatus.CLOSED, start, end);
        return orders.stream()
                .map(order -> tableOrderItemRepository.getTotalByTableOrderId(order.getId()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal consumedMlForRange(LocalDateTime start, LocalDateTime end) {
        return tableOrderItemRepository.getConsumedVolumeMlByClosedRange(TableOrderStatus.CLOSED, start, end);
    }

    private List<RevenueDayPointResponse> buildWeekPoints(Map<DayOfWeek, BigDecimal> weekMap) {
        List<RevenueDayPointResponse> points = new ArrayList<>();
        DayOfWeek[] ordered = {
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY
        };
        for (DayOfWeek day : ordered) {
            points.add(new RevenueDayPointResponse(shortDay(day), weekMap.getOrDefault(day, BigDecimal.ZERO)));
        }
        return points;
    }

    private List<RevenueDayPointResponse> buildMonthPoints(List<TableOrder> monthOrders, LocalDate start, LocalDate end) {
        Map<LocalDate, BigDecimal> dayMap = new java.util.LinkedHashMap<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            dayMap.put(cursor, BigDecimal.ZERO);
            cursor = cursor.plusDays(1);
        }

        for (TableOrder order : monthOrders) {
            LocalDate day = order.getClosedAt().toLocalDate();
            BigDecimal orderTotal = tableOrderItemRepository.getTotalByTableOrderId(order.getId());
            dayMap.put(day, dayMap.getOrDefault(day, BigDecimal.ZERO).add(orderTotal));
        }

        return dayMap.entrySet().stream()
                .map(entry -> new RevenueDayPointResponse(String.valueOf(entry.getKey().getDayOfMonth()), entry.getValue()))
                .toList();
    }

    private String toGermanDay(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Montag";
            case TUESDAY -> "Dienstag";
            case WEDNESDAY -> "Mittwoch";
            case THURSDAY -> "Donnerstag";
            case FRIDAY -> "Freitag";
            case SATURDAY -> "Samstag";
            case SUNDAY -> "Sonntag";
        };
    }

    private String shortDay(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Mo";
            case TUESDAY -> "Di";
            case WEDNESDAY -> "Mi";
            case THURSDAY -> "Do";
            case FRIDAY -> "Fr";
            case SATURDAY -> "Sa";
            case SUNDAY -> "So";
        };
    }
}

