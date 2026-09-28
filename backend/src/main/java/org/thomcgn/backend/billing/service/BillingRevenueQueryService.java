package org.thomcgn.backend.billing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.billing.application.BillingRevenueQueries;
import org.thomcgn.backend.billing.application.PaidOrderRevenue;
import org.thomcgn.backend.billing.domain.TableOrderStatus;
import org.thomcgn.backend.billing.repository.TableOrderItemRepository;
import org.thomcgn.backend.billing.repository.TableOrderRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingRevenueQueryService implements BillingRevenueQueries {
    private final TableOrderRepository orders;
    private final TableOrderItemRepository items;

    @Override
    public List<org.thomcgn.backend.billing.application.BusinessDayRevenue> businessDays(
            java.time.LocalDate start, java.time.LocalDate end) {
        var days = new java.util.TreeMap<java.time.LocalDate, org.thomcgn.backend.billing.application.BusinessDayRevenue>();
        for (var row : items.businessRevenue(start, end, start.atStartOfDay(), end.atStartOfDay())) {
            var date = row.effectiveDate();
            var previous = days.getOrDefault(date, new org.thomcgn.backend.billing.application.BusinessDayRevenue(
                    date, BigDecimal.ZERO, BigDecimal.ZERO));
            days.put(date, new org.thomcgn.backend.billing.application.BusinessDayRevenue(date,
                    previous.revenue().add(row.revenue()),
                    previous.consumedMl().add(row.consumedMl() == null ? BigDecimal.ZERO : row.consumedMl())));
        }
        return List.copyOf(days.values());
    }

    @Override
    public BigDecimal revenue(LocalDateTime start, LocalDateTime end) {
        return items.getRevenueByClosedRange(TableOrderStatus.CLOSED, start, end);
    }

    @Override
    public BigDecimal consumedVolumeMl(LocalDateTime start, LocalDateTime end) {
        return items.getConsumedVolumeMlByClosedRange(TableOrderStatus.CLOSED, start, end);
    }

    @Override
    public List<PaidOrderRevenue> paidOrdersClosedBetweenInclusive(LocalDateTime start, LocalDateTime end) {
        return orders.findAllByStatusAndPaidTrueAndClosedAtBetween(TableOrderStatus.CLOSED, start, end)
                .stream()
                .map(order -> new PaidOrderRevenue(order.getClosedAt(), items.getTotalByTableOrderId(order.getId())))
                .toList();
    }
}
