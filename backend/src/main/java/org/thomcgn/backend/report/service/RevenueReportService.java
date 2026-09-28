package org.thomcgn.backend.report.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.billing.application.BillingRevenueQueries;
import org.thomcgn.backend.billing.application.BusinessDayRevenue;
import org.thomcgn.backend.report.api.dto.RevenueDayPointResponse;
import org.thomcgn.backend.report.api.dto.RevenueOverviewResponse;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RevenueReportService {

    private final BillingRevenueQueries billingRevenue;
    private final org.thomcgn.backend.inventory.service.SalesConfigurationService businessSettings;

    @Transactional(readOnly = true)
    public RevenueOverviewResponse getOverview() {
        LocalDate today = businessSettings.getCurrentBusinessDate();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = today.withDayOfMonth(1);

        var allDays = billingRevenue.businessDays(weekStart.isBefore(monthStart) ? weekStart : monthStart, today.plusDays(1));
        var weekOrders = allDays.stream().filter(day -> !day.businessDate().isBefore(weekStart)).toList();
        var monthOrders = allDays.stream().filter(day -> !day.businessDate().isBefore(monthStart)).toList();
        BigDecimal dayRevenue = allDays.stream().filter(day -> day.businessDate().equals(today))
                .map(BusinessDayRevenue::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal weekRevenue = weekOrders.stream().map(BusinessDayRevenue::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthRevenue = monthOrders.stream().map(BusinessDayRevenue::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dayConsumedMl = allDays.stream().filter(day -> day.businessDate().equals(today))
                .map(BusinessDayRevenue::consumedMl).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal weekConsumedMl = weekOrders.stream().map(BusinessDayRevenue::consumedMl).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthConsumedMl = monthOrders.stream().map(BusinessDayRevenue::consumedMl).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<DayOfWeek, BigDecimal> weekMap = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            weekMap.put(day, BigDecimal.ZERO);
        }

        for (BusinessDayRevenue order : weekOrders) {
            DayOfWeek day = order.businessDate().getDayOfWeek();
            BigDecimal orderTotal = order.revenue();
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

    private List<RevenueDayPointResponse> buildMonthPoints(List<BusinessDayRevenue> monthOrders, LocalDate start, LocalDate end) {
        Map<LocalDate, BigDecimal> dayMap = new java.util.LinkedHashMap<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            dayMap.put(cursor, BigDecimal.ZERO);
            cursor = cursor.plusDays(1);
        }

        for (BusinessDayRevenue order : monthOrders) {
            LocalDate day = order.businessDate();
            BigDecimal orderTotal = order.revenue();
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

