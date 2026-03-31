package org.thomcgn.backend.report.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thomcgn.backend.billing.domain.TableOrder;
import org.thomcgn.backend.billing.domain.TableOrderStatus;
import org.thomcgn.backend.billing.repository.TableOrderItemRepository;
import org.thomcgn.backend.billing.repository.TableOrderRepository;
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
    private TableOrderRepository tableOrderRepository;

    @Mock
    private TableOrderItemRepository tableOrderItemRepository;

    @InjectMocks
    private RevenueReportService revenueReportService;

    @Test
    void getOverview_aggregatesRevenueFromPaidOrdersOnly() {
        TableOrder paidWeekOrder = new TableOrder();
        paidWeekOrder.setId(11L);
        paidWeekOrder.setStatus(TableOrderStatus.CLOSED);
        paidWeekOrder.setPaid(true);
        paidWeekOrder.setClosedAt(LocalDateTime.of(2026, 3, 23, 20, 0));

        TableOrder paidMonthOrder = new TableOrder();
        paidMonthOrder.setId(12L);
        paidMonthOrder.setStatus(TableOrderStatus.CLOSED);
        paidMonthOrder.setPaid(true);
        paidMonthOrder.setClosedAt(LocalDateTime.of(2026, 3, 24, 21, 0));

        when(tableOrderItemRepository.getRevenueByClosedRange(any(), any(), any()))
                .thenReturn(new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("30.00"));
        when(tableOrderItemRepository.getConsumedVolumeMlByClosedRange(any(), any(), any()))
                .thenReturn(new BigDecimal("500"), new BigDecimal("700"), new BigDecimal("900"));

        when(tableOrderRepository.findAllByStatusAndPaidTrueAndClosedAtBetween(any(), any(), any()))
                .thenReturn(List.of(paidWeekOrder), List.of(paidMonthOrder));

        when(tableOrderItemRepository.getTotalByTableOrderId(11L)).thenReturn(new BigDecimal("40.00"));
        when(tableOrderItemRepository.getTotalByTableOrderId(12L)).thenReturn(new BigDecimal("50.00"));

        RevenueOverviewResponse overview = revenueReportService.getOverview();

        assertEquals(new BigDecimal("10.00"), overview.dayRevenue());
        assertEquals(new BigDecimal("20.00"), overview.weekRevenue());
        assertEquals(new BigDecimal("30.00"), overview.monthRevenue());
        assertEquals("Montag", overview.strongestWeekday());

        verify(tableOrderRepository, times(2)).findAllByStatusAndPaidTrueAndClosedAtBetween(any(), any(), any());
    }
}

