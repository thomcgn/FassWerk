package org.thomcgn.backend.report.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thomcgn.backend.billing.application.BillingRevenueQueries;
import org.thomcgn.backend.billing.application.PaidOrderRevenue;
import org.thomcgn.backend.report.api.dto.RevenueOverviewResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueReportServicePaidOnlyTest {

    @Mock
    private BillingRevenueQueries billingRevenue;

    @InjectMocks
    private RevenueReportService revenueReportService;

    @Test
    void getOverview_aggregatesRevenueFromPaidOrdersOnly() {
        PaidOrderRevenue paidWeekOrder = new PaidOrderRevenue(
                LocalDateTime.of(2026, 3, 23, 20, 0), new BigDecimal("40.00"));
        PaidOrderRevenue paidMonthOrder = new PaidOrderRevenue(
                LocalDateTime.of(2026, 3, 24, 21, 0), new BigDecimal("50.00"));

        when(billingRevenue.revenue(any(), any()))
                .thenReturn(new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("30.00"));
        when(billingRevenue.consumedVolumeMl(any(), any()))
                .thenReturn(new BigDecimal("500"), new BigDecimal("700"), new BigDecimal("900"));
        when(billingRevenue.paidOrdersClosedBetweenInclusive(any(), any()))
                .thenReturn(List.of(paidWeekOrder), List.of(paidMonthOrder));

        RevenueOverviewResponse overview = revenueReportService.getOverview();

        assertEquals(new BigDecimal("10.00"), overview.dayRevenue());
        assertEquals(new BigDecimal("20.00"), overview.weekRevenue());
        assertEquals(new BigDecimal("30.00"), overview.monthRevenue());
        assertEquals("Montag", overview.strongestWeekday());

        verify(billingRevenue, times(2)).paidOrdersClosedBetweenInclusive(any(), any());
    }
}

