package org.thomcgn.backend.billing.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.report.service.RevenueReportService;
import org.thomcgn.backend.billing.domain.TableOrder;
import org.thomcgn.backend.billing.domain.TableOrderItem;
import org.thomcgn.backend.billing.domain.TableOrderStatus;
import org.thomcgn.backend.billing.repository.TableOrderRepository;
import org.thomcgn.backend.billing.repository.TableOrderItemRepository;
import org.thomcgn.backend.table.repository.TableRepository;
import org.thomcgn.backend.menu.repository.DrinkVariantRepository;
import org.thomcgn.backend.shift.service.ShiftSettlementService;
import org.thomcgn.backend.support.PostgresIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.thomcgn.backend.shift.api.dto.ShiftSettlementRequest;
import org.thomcgn.backend.shift.api.dto.ShiftWorkerEntryRequest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RevenueBoundaryIntegrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired RevenueReportService reports;
    @Autowired ShiftSettlementService shifts;
    @Autowired BillingRevenueQueries billingRevenue;
    @Autowired TableOrderRepository orders;
    @Autowired TableOrderItemRepository items;
    @Autowired TableRepository tables;
    @Autowired DrinkVariantRepository variants;

    @BeforeEach
    void fixtures() {
        jdbc.execute("truncate table tables, drink_categories, shift_settlements restart identity cascade");
        jdbc.update("insert into tables(name,status,active) values('Revenue test','FREE',true)");
        jdbc.update("insert into drink_categories(name,sort_order,active) values('Revenue test',0,true)");
        jdbc.update("insert into drinks(category_id,name,active) values(1,'Test drink',true)");
        jdbc.update("insert into drink_variants(drink_id,display_volume_name,volume_ml,price,active) values(1,'Glass',250,6,true)");
    }

    @Test
    void shiftUsesOnlyPaidClosedRevenueAndExcludesNextMidnight() {
        LocalDate date = LocalDate.of(2035, 1, 15);
        order("CLOSED", true, date.atTime(12, 0), "12.00");
        order("CLOSED", false, date.atTime(12, 0), "30.00");
        order("OPEN", true, date.atTime(12, 0), "50.00");
        order("CLOSED", true, date.plusDays(1).atStartOfDay(), "100.00");
        var result = shifts.getByDate(date);
        assertThat(result.dailyRevenue()).isEqualByComparingTo("12.00");
        assertThat(result.totalWages()).isEqualByComparingTo("0");
        assertThat(result.expectedClosingCash()).isEqualByComparingTo("12.00");
        assertThat(result.entries()).isEmpty();
    }

    @Test
    void reportingPreservesPaidTotalsVolumesAndCharts() {
        LocalDate today = LocalDate.now();
        order("CLOSED", true, today.atTime(12, 0), "12.00");
        order("CLOSED", false, today.atTime(12, 0), "30.00");
        order("OPEN", true, today.atTime(12, 0), "50.00");
        var result = reports.getOverview();
        assertThat(result.dayRevenue()).isEqualByComparingTo("12.00");
        assertThat(result.weekRevenue()).isEqualByComparingTo("12.00");
        assertThat(result.monthRevenue()).isEqualByComparingTo("12.00");
        assertThat(result.dayConsumedMl()).isEqualByComparingTo("500");
        assertThat(result.weekConsumedMl()).isEqualByComparingTo("500");
        assertThat(result.monthConsumedMl()).isEqualByComparingTo("500");
        assertThat(result.weekPoints().stream().map(point -> point.revenue()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("12.00");
        assertThat(result.monthPoints().stream().map(point -> point.revenue()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("12.00");
    }

    @Test
    void savedShiftKeepsWagesAndCashCalculationInShiftContext() {
        LocalDate date = LocalDate.of(2035, 1, 15);
        order("CLOSED", true, date.atTime(12, 0), "12.00");
        var request = new ShiftSettlementRequest(new BigDecimal("100.00"), new BigDecimal("5.00"),
                List.of(new ShiftWorkerEntryRequest("Worker", LocalTime.of(18, 0), LocalTime.of(20, 0),
                        new BigDecimal("10.00"))));
        var result = shifts.saveByDate(date, request);
        assertThat(result.id()).isNotNull();
        assertThat(result.dailyRevenue()).isEqualByComparingTo("12.00");
        assertThat(result.totalWages()).isEqualByComparingTo("20.00");
        assertThat(result.expectedClosingCash()).isEqualByComparingTo("87.00");
        assertThat(result.entries()).hasSize(1);
        assertThat(shifts.getByDate(date).expectedClosingCash()).isEqualByComparingTo("87.00");
        assertThat(shifts.listByRange(date, date)).hasSize(1);
    }

    @Test
    void billingReadModelPreservesLegacyInclusiveChartBoundary() {
        LocalDate date = LocalDate.of(2035, 1, 15);
        order("CLOSED", true, date.atStartOfDay(), "12.00");
        order("CLOSED", true, date.plusDays(1).atStartOfDay(), "100.00");
        order("CLOSED", false, date.atTime(12, 0), "30.00");
        order("OPEN", true, date.atTime(12, 0), "50.00");
        var start = date.atStartOfDay();
        var end = date.plusDays(1).atStartOfDay();
        assertThat(billingRevenue.revenue(start, end)).isEqualByComparingTo("12.00");
        assertThat(billingRevenue.consumedVolumeMl(start, end)).isEqualByComparingTo("500");
        assertThat(billingRevenue.paidOrdersClosedBetweenInclusive(start, end))
                .containsExactlyInAnyOrder(new PaidOrderRevenue(start, new BigDecimal("12.00")),
                        new PaidOrderRevenue(end, new BigDecimal("100.00")));
        assertThat(jdbc.queryForObject("select count(*) from table_orders", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select sum(total_price) from table_order_items", BigDecimal.class))
                .isEqualByComparingTo("192.00");
    }

    private void order(String status, boolean paid, LocalDateTime closedAt, String total) {
        // Use the production Hibernate timestamp binding (UTC), not JDBC's default calendar.
        TableOrder order = new TableOrder();
        order.setTable(tables.getReferenceById(1L));
        order.setStatus(TableOrderStatus.valueOf(status));
        order.setPaid(paid);
        order.setOpenedAt(closedAt.minusHours(1));
        order.setClosedAt(closedAt);
        orders.saveAndFlush(order);
        TableOrderItem item = new TableOrderItem();
        item.setTableOrder(order);
        item.setDrinkVariant(variants.getReferenceById(1L));
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal(total).divide(BigDecimal.valueOf(2)));
        item.setTotalPrice(new BigDecimal(total));
        item.setDeductedVolumeMl(new BigDecimal("500"));
        items.saveAndFlush(item);
    }
}
