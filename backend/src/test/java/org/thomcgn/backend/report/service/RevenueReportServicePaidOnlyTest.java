package org.thomcgn.backend.report.service;

import org.junit.jupiter.api.Test;
import org.thomcgn.backend.billing.application.BillingRevenueQueries;
import org.thomcgn.backend.billing.application.BusinessDayRevenue;
import org.thomcgn.backend.inventory.service.SalesConfigurationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RevenueReportServicePaidOnlyTest {
    @Test void totalsAndChartsUseTheSameBusinessDayProjection() {
        var billing=mock(BillingRevenueQueries.class);
        var settings=mock(SalesConfigurationService.class);
        var today=LocalDate.of(2035,6,5);
        when(settings.getCurrentBusinessDate()).thenReturn(today);
        when(billing.businessDays(any(), eq(today.plusDays(1)))).thenReturn(List.of(
                new BusinessDayRevenue(today,new BigDecimal("40.00"),new BigDecimal("500"))));
        var result=new RevenueReportService(billing,settings).getOverview();
        assertThat(result.dayRevenue()).isEqualByComparingTo("40.00");
        assertThat(result.monthPoints().stream().map(p -> p.revenue()).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(result.monthRevenue());
        assertThat(result.weekPoints().stream().map(p -> p.revenue()).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(result.weekRevenue());
        verify(billing).businessDays(any(),eq(today.plusDays(1)));
        verifyNoMoreInteractions(billing);
    }
}
