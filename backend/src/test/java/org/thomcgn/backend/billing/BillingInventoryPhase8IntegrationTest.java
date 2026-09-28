package org.thomcgn.backend.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.thomcgn.backend.billing.api.dto.*;
import org.thomcgn.backend.billing.application.Money;
import org.thomcgn.backend.billing.service.TableOrderService;
import org.thomcgn.backend.common.exception.ConflictException;
import org.thomcgn.backend.inventory.api.dto.InventoryAdjustmentRequest;
import org.thomcgn.backend.inventory.repository.DrinkSalesDailyRepository;
import org.thomcgn.backend.inventory.service.DrinkSalesTrackingService;
import org.thomcgn.backend.inventory.service.InventoryInsightsApplicationService;
import org.thomcgn.backend.inventory.service.InventoryService;
import org.thomcgn.backend.inventory.service.ReorderCalculationService;
import org.thomcgn.backend.menu.service.MenuService;
import org.thomcgn.backend.support.PostgresIntegrationTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class BillingInventoryPhase8IntegrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired TableOrderService orders;
    @Autowired org.thomcgn.backend.report.service.RevenueReportService reports;
    @Autowired org.thomcgn.backend.shift.service.ShiftSettlementService shifts;
    @Autowired InventoryService inventory;
    @Autowired org.thomcgn.backend.inventory.service.ReorderOrderService deliveries;
    @Autowired DrinkSalesTrackingService salesTracking;
    @Autowired DrinkSalesDailyRepository dailySales;
    @Autowired InventoryInsightsApplicationService inventoryInsights;
    @Autowired MenuService menu;
    @MockitoSpyBean ReorderCalculationService reorderCalculations;
    @Autowired org.thomcgn.backend.inventory.service.SalesConfigurationService businessSettings;

    @BeforeEach
    void fixtures() {
        jdbc.execute("truncate table tables, drink_categories, inventory_items, suppliers restart identity cascade");
        jdbc.update("insert into tables(name,status,active) values('T1','FREE',true),('T2','FREE',true)");
    }

    @Test
    void delayedStateRetriesCannotUndoLaterArchiveOrReopen() {
        var order = orders.open(new OpenTableOrderRequest(1L, null));
        orders.markUnpaid(order.id(), "phase1-archive-a");
        orders.reopenUnpaid(order.id(), "phase1-reopen-a");
        assertThat(orders.markUnpaid(order.id(), "phase1-archive-a").status()).isEqualTo(org.thomcgn.backend.billing.domain.TableOrderStatus.OPEN);
        orders.markUnpaid(order.id(), "phase1-archive-b");
        assertThat(orders.reopenUnpaid(order.id(), "phase1-reopen-a").status()).isEqualTo(org.thomcgn.backend.billing.domain.TableOrderStatus.CLOSED);
        assertThatThrownBy(() -> orders.reopenUnpaid(order.id(), "phase1-archive-a")).isInstanceOf(ConflictException.class);
        orders.reopenUnpaid(order.id(), "phase1-reopen-b");
        orders.close(order.id(), "phase1-close");
        assertThat(orders.close(order.id(), "phase1-close").paid()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from billing_operations where operation_key='phase1-close'", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentDayCloseAndOldReplayCannotAdvanceANewerDay() throws Exception {
        var previous = businessSettings.getConfiguration();
        var date = java.time.LocalDate.of(2036, 6, 1);
        var key = java.util.UUID.randomUUID().toString();
        try {
            jdbc.update("update inventory_business_settings set manual_business_date=?", date);
            var first = java.util.concurrent.CompletableFuture.runAsync(() -> businessSettings.closeBusinessDayManually(date, key));
            var second = java.util.concurrent.CompletableFuture.runAsync(() -> businessSettings.closeBusinessDayManually(date, key));
            first.get(); second.get();
            assertThat(businessSettings.getCurrentBusinessDate()).isEqualTo(date.plusDays(1));
            assertThatThrownBy(() -> businessSettings.closeBusinessDayManually(date, "phase1-stale-other-device")).isInstanceOf(ConflictException.class);
            assertThatThrownBy(() -> businessSettings.closeBusinessDayManually(date.plusDays(1), key)).isInstanceOf(ConflictException.class);
            businessSettings.closeBusinessDayManually(date.plusDays(1), java.util.UUID.randomUUID().toString());
            businessSettings.closeBusinessDayManually(date, key);
            assertThat(businessSettings.getCurrentBusinessDate()).isEqualTo(date.plusDays(2));
            jdbc.update("update inventory_business_settings set manual_business_date=?", date);
            businessSettings.closeBusinessDayManually(date, key);
            assertThat(businessSettings.getCurrentBusinessDate()).isEqualTo(date);
        } finally { businessSettings.updateConfiguration(previous); }
    }

    @Test
    void dayCloseOperationFailureRollsBackDate() {
        var previous = businessSettings.getConfiguration();
        var date = java.time.LocalDate.of(2036, 7, 1);
        jdbc.execute("alter table business_day_close_operations add constraint phase1_failure check(operation_key <> 'phase1-failing-close')");
        try {
            jdbc.update("update inventory_business_settings set manual_business_date=?", date);
            assertThatThrownBy(() -> businessSettings.closeBusinessDayManually(date, "phase1-failing-close"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(businessSettings.getCurrentBusinessDate()).isEqualTo(date);
            assertThat(jdbc.queryForObject("select count(*) from business_day_close_operations where operation_key='phase1-failing-close'", Integer.class)).isZero();
        } finally {
            jdbc.execute("alter table business_day_close_operations drop constraint phase1_failure");
            businessSettings.updateConfiguration(previous);
        }
    }

    @Test
    void closureUsesSameManualBusinessDateInReportShiftAndArchive() {
        var previous = businessSettings.getConfiguration();
        try {
            var date = java.time.LocalDate.of(2035, 6, 1);
            jdbc.update("update inventory_business_settings set manual_business_date=?", date);
            long variant = stockedVariant("Night report", 250, "3.00", "10");
            var order = orders.open(new OpenTableOrderRequest(1L, null));
            orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 2), "night-report-add");
            orders.close(order.id());
            assertThat(reports.getOverview().dayRevenue()).isEqualByComparingTo("6");
            assertThat(shifts.getByDate(date).dailyRevenue()).isEqualByComparingTo("6");
            assertThat(shifts.getByDate(date.plusDays(1)).dailyRevenue()).isEqualByComparingTo("0");
            assertThat(orders.searchArchive(date, null, "PAID")).extracting(TableOrderResponse::id).contains(order.id());
            assertThat(orders.searchArchive(date.plusDays(1), null, "PAID")).isEmpty();
            jdbc.update("update inventory_business_settings set manual_business_date=?", date.plusDays(1));
            assertThat(reports.getOverview().dayRevenue()).isEqualByComparingTo("0");
            assertThat(shifts.getByDate(date).dailyRevenue()).isEqualByComparingTo("6");
        } finally { businessSettings.updateConfiguration(previous); }
    }

    @Test
    void concurrentDeliveryReceiptsIncreaseStockExactlyOnceAndCannotBeReverted() throws Exception {
        long variant = stockedVariant("Delivery", 250, "3.00", "10");
        long delivery = delivery(variant, "MILLILITER", "2000");
        var first = java.util.concurrent.CompletableFuture.runAsync(() -> deliveries.updateReorderStatus(delivery, "RECEIVED"));
        var second = java.util.concurrent.CompletableFuture.runAsync(() -> deliveries.updateReorderStatus(delivery, "RECEIVED"));
        first.get(); second.get();
        assertThat(stock(variant)).isEqualByComparingTo("12");
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where operation_key=?", Integer.class,
                "reorder-receive:" + delivery)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select received_quantity from reorder_orders where id=?", BigDecimal.class, delivery)).isEqualByComparingTo("2000");
        assertThatThrownBy(() -> deliveries.updateReorderStatus(delivery, "PENDING")).isInstanceOf(ConflictException.class);
    }

    @Test
    void invalidDeliveryUnitLeavesStockAndStatusUntouched() {
        long variant = stockedVariant("Invalid delivery", 250, "3.00", "10");
        long delivery = delivery(variant, "PIECE", "2");
        assertThatThrownBy(() -> deliveries.updateReorderStatus(delivery, "RECEIVED"))
                .isInstanceOf(org.thomcgn.backend.common.exception.BadRequestException.class);
        assertThat(stock(variant)).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("select status from reorder_orders where id=?", String.class, delivery)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where operation_key=?", Integer.class,
                "reorder-receive:" + delivery)).isZero();
    }

    private long delivery(long variant, String unit, String amount) {
        long item = jdbc.queryForObject("select id from inventory_items where linked_drink_variant_id=?", Long.class, variant);
        long supplier = jdbc.queryForObject("insert into suppliers(name,active) values('Audit supplier',true) returning id", Long.class);
        return deliveries.createReorderOrder(new org.thomcgn.backend.inventory.api.dto.ReorderOrderRequest(
                item, supplier, new BigDecimal(amount), unit, java.time.LocalDate.of(2035, 1, 1), null, null)).id();
    }

    @Test
    void schedulerCommitsHealthyItemAfterAnotherItemDatabaseFailure() {
        long broken = stockedVariant("Broken scheduler", 250, "3.00", "10");
        long healthy = stockedVariant("Healthy scheduler", 250, "3.00", "10");
        org.mockito.Mockito.doAnswer(call -> {
            org.thomcgn.backend.inventory.domain.InventoryItem item = call.getArgument(0);
            if (item.getLinkedDrinkVariant().getId().equals(broken)) jdbc.queryForObject("select 1 / 0", Integer.class);
            return call.callRealMethod();
        }).when(reorderCalculations).calculateReorderAmount(any());
        assertThatCode(() -> reorderCalculations.recalculateAllInventoryItems()).doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("select count(*) from reorder_calculations c join inventory_items i on c.inventory_item_id=i.id where i.linked_drink_variant_id=?", Integer.class, healthy)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from reorder_calculations c join inventory_items i on c.inventory_item_id=i.id where i.linked_drink_variant_id=?", Integer.class, broken)).isZero();
    }

    @Test
    void concurrentWeeklyJobsCatchUpOldWeeksAndPreserveUnattributableLegacyRows() throws Exception {
        long variant = stockedVariant("Catch up", 250, "3.00", "10");
        Long drink = jdbc.queryForObject("select drink_id from drink_variants where id=?",Long.class,variant);
        jdbc.update("insert into drink_sales_daily(drink_id,drink_variant_id,sale_date,quantity_sold,volume_sold_ml) values(?,?,'2020-01-06',2,500)",drink,variant);
        jdbc.update("insert into drink_sales_daily(drink_id,drink_variant_id,sale_date,quantity_sold,volume_sold_ml) values(?,null,'2020-01-06',1,250)",drink);
        var first=java.util.concurrent.CompletableFuture.runAsync(() -> salesTracking.aggregateDailyToWeekly());
        var second=java.util.concurrent.CompletableFuture.runAsync(() -> salesTracking.aggregateDailyToWeekly());
        first.get(); second.get();
        assertThat(jdbc.queryForObject("select quantity_sold from drink_sales_weekly where drink_variant_id=?", BigDecimal.class,variant)).isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("select count(*) from drink_sales_daily where drink_variant_id is null", Integer.class)).isEqualTo(1);
    }

    @Test
    void cancellationAfterBusinessDayChangeReversesOriginalDay() {
        var previous = businessSettings.getConfiguration();
        try {
            long variant = stockedVariant("Cross day", 250, "3.00", "10");
            jdbc.update("update inventory_business_settings set manual_business_date='2035-06-01'");
            var order = orders.open(new OpenTableOrderRequest(1L, null));
            order = orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 2), "cross-day-add");
            long originalItem = order.items().getFirst().id();
            jdbc.update("update inventory_business_settings set manual_business_date='2035-06-02'");
            order = orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 1), "next-day-add");
            assertThat(order.items()).hasSize(2);
            orders.removeItem(order.id(), originalItem, "cross-day-cancel");
            orders.removeItem(order.id(), originalItem, "cross-day-cancel");
            assertThat(jdbc.queryForObject("select quantity_sold from drink_sales_daily where sale_date='2035-06-01'", BigDecimal.class)).isEqualByComparingTo("1");
            assertThat(jdbc.queryForObject("select quantity_sold from drink_sales_daily where sale_date='2035-06-02'", BigDecimal.class)).isEqualByComparingTo("1");
            assertThat(stock(variant)).isEqualByComparingTo("9.5000");
        } finally { businessSettings.updateConfiguration(previous); }
    }

    @Test
    void multipleItemsUseCanonicalCentRounding() {
        long first = stockedVariant("Rounded", 250, "2.345", "10");
        long second = stockedVariant("Small", 100, "0.10", "10");
        var order = orders.open(new OpenTableOrderRequest(1L, null));
        order = orders.addItem(order.id(), new AddTableOrderItemRequest(first, 3), "rounding-add-0001");
        order = orders.addItem(order.id(), new AddTableOrderItemRequest(second, 3), "rounding-add-0002");

        assertThat(order.items()).extracting(TableOrderItemResponse::totalPrice)
                .containsExactlyInAnyOrder(new BigDecimal("7.05"), new BigDecimal("0.30"));
        assertThat(order.total()).isEqualByComparingTo("7.35");
        assertThat(Money.multiply(new BigDecimal("2.345"), 3)).isEqualByComparingTo("7.05");
    }

    @Test
    void repeatedAddWithSameKeyDeductsStockExactlyOnceAndRejectsChangedPayload() {
        long variant = stockedVariant("Idempotent", 250, "3.00", "1.00");
        var order = orders.open(new OpenTableOrderRequest(1L, null));
        var request = new AddTableOrderItemRequest(variant, 2);
        var first = orders.addItem(order.id(), request, "same-add-request");
        var replay = orders.addItem(order.id(), request, "same-add-request");

        assertThat(first.items().getFirst().quantity()).isEqualTo(2);
        assertThat(replay.items().getFirst().quantity()).isEqualTo(2);
        assertThat(stock(variant)).isEqualByComparingTo("0.5000");
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 1), "same-add-request"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void exactZeroIsAllowedAndInsufficientFollowUpRollsBackItemAndMovement() {
        long variant = stockedVariant("Exact", 250, "1.00", "0.5000");
        var order = orders.open(new OpenTableOrderRequest(1L, null));
        order = orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 2), "exact-zero-add");

        assertThat(stock(variant)).isEqualByComparingTo("0.0000");
        long itemId = order.items().getFirst().id();
        long orderId = order.id();
        assertThatThrownBy(() -> orders.addItem(orderId, new AddTableOrderItemRequest(variant, 1), "insufficient-add"))
                .isInstanceOf(ConflictException.class);
        assertThat(orders.getById(orderId).items().getFirst().quantity()).isEqualTo(2);
        assertThat(stock(variant)).isEqualByComparingTo("0.0000");
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select quantity from table_order_items where id=?", Integer.class, itemId)).isEqualTo(2);
    }

    @Test
    void litreStockPreservesOneMillilitrePrecision() {
        long variant = stockedVariant("One ml", 1, "0.01", "0.0020");
        var order = orders.open(new OpenTableOrderRequest(1L, null));
        orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 1), "one-ml-add");

        assertThat(stock(variant)).isEqualByComparingTo("0.0010");
        assertThat(jdbc.queryForObject("select amount from inventory_movements", BigDecimal.class))
                .isEqualByComparingTo("-0.0010");
    }

    @Test
    void multipleQuantityAndCancellationUpdateSalesByTheRealAmount() {
        long variant = stockedVariant("Sales", 250, "2.00", "2.0000");
        var order = orders.open(new OpenTableOrderRequest(1L, null));
        order = orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 3), "sales-quantity-add");

        assertThat(jdbc.queryForObject("select quantity_sold from drink_sales_daily", BigDecimal.class))
                .isEqualByComparingTo("3.00");
        assertThat(jdbc.queryForObject("select volume_sold_ml from drink_sales_daily", BigDecimal.class))
                .isEqualByComparingTo("750.00");

        orders.removeItem(order.id(), order.items().getFirst().id());

        assertThat(jdbc.queryForObject("select quantity_sold from drink_sales_daily", BigDecimal.class))
                .isEqualByComparingTo("2.00");
        assertThat(jdbc.queryForObject("select volume_sold_ml from drink_sales_daily", BigDecimal.class))
                .isEqualByComparingTo("500.00");
        assertThat(stock(variant)).isEqualByComparingTo("1.5000");
    }

    @Test
    void repeatedWeeklyAggregationRecomputesInsteadOfDoubleCounting() {
        long variant = stockedVariant("Weekly", 250, "2.00", "2.0000");
        var order = orders.open(new OpenTableOrderRequest(1L, null));
        orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 3), "weekly-source-add");
        var daily = dailySales.findAll().getFirst();

        salesTracking.aggregateToWeekly(daily);
        salesTracking.aggregateToWeekly(daily);

        assertThat(jdbc.queryForObject("select quantity_sold from drink_sales_weekly", BigDecimal.class))
                .isEqualByComparingTo("3.00");
        assertThat(jdbc.queryForObject("select volume_sold_ml from drink_sales_weekly", BigDecimal.class))
                .isEqualByComparingTo("750.00");
    }

    @Test
    void reorderCalculationConvertsDailyMillilitresToWeeklyInventoryUnits() {
        long variant = stockedVariant("Reorder units", 250, "2.00", "2.0000");
        long inventoryId = jdbc.queryForObject(
                "select id from inventory_items where linked_drink_variant_id=?", Long.class, variant);
        long drinkId = jdbc.queryForObject("select drink_id from drink_variants where id=?", Long.class, variant);
        jdbc.update("update inventory_items set minimum_stock=5.0000 where id=?", inventoryId);
        jdbc.update("""
                insert into drink_sales_weekly(drink_id,drink_variant_id,week_start_date,quantity_sold,
                  volume_sold_ml,average_daily_quantity,average_daily_volume_ml)
                values(?,?,current_date,28,7000,4,1000)
                """, drinkId, variant);
        jdbc.update("""
                insert into consumption_metadata(inventory_item_id,lead_time_days,safety_stock_factor,weeks_lookback)
                values(?,7,1,4)
                """, inventoryId);

        var calculation = inventoryInsights.calculateReorder(inventoryId);

        assertThat(calculation.weeklyAverageConsumption()).isEqualByComparingTo("7.0000");
        assertThat(calculation.recommendedReorderAmount()).isEqualByComparingTo("12.0000");
        assertThat(calculation.weeksUntilStockout()).isEqualByComparingTo("0.29");
    }

    @Test
    void repeatedSplitReturnsSamePaidReceiptWithoutDuplicatingRevenue() {
        long variant = stockedVariant("Split", 250, "2.50", "5");
        var order = orders.open(new OpenTableOrderRequest(1L, null));
        order = orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 4), "split-source-add");
        long item = order.items().getFirst().id();
        long orderId = order.id();
        var request = new SplitTableOrderPaymentRequest(List.of(new SplitTableOrderItemRequest(item, 2)));

        var first = orders.splitPayment(orderId, request, "same-split-request");
        var replay = orders.splitPayment(orderId, request, "same-split-request");

        assertThat(replay.paidOrder().id()).isEqualTo(first.paidOrder().id());
        assertThat(replay.paidOrder().total()).isEqualByComparingTo("5.00");
        assertThat(replay.openOrder().items().getFirst().quantity()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from table_orders where paid", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> orders.splitPayment(orderId,
                new SplitTableOrderPaymentRequest(List.of(new SplitTableOrderItemRequest(item, 1))),
                "same-split-request")).isInstanceOf(ConflictException.class);
    }

    @Test
    void deletingCatalogDrinkPreservesPaidBillAndRevenue() {
        long variant = stockedVariant("Historical", 250, "4.00", "2.0000");
        long drink = jdbc.queryForObject("select drink_id from drink_variants where id=?", Long.class, variant);
        var order = orders.open(new OpenTableOrderRequest(1L, null));
        order = orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 2), "historical-add");
        long orderId = order.id();
        orders.close(orderId);

        menu.deleteDrink(drink);

        assertThat(jdbc.queryForObject("select active from drinks where id=?", Boolean.class, drink)).isFalse();
        assertThat(jdbc.queryForObject("select active from drink_variants where id=?", Boolean.class, variant)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from table_order_items where table_order_id=?", Integer.class, orderId))
                .isEqualTo(1);
        assertThat(orders.getById(orderId).total()).isEqualByComparingTo("8.00");
    }

    @Test
    void invalidSplitAndRepeatedCloseCannotLeavePartialFinancialState() {
        long variant = stockedVariant("Rollback", 250, "4.00", "5");
        var order = orders.open(new OpenTableOrderRequest(1L, null));
        order = orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 2), "rollback-source-add");
        long item = order.items().getFirst().id();
        long orderId = order.id();

        assertThatThrownBy(() -> orders.splitPayment(orderId,
                new SplitTableOrderPaymentRequest(List.of(new SplitTableOrderItemRequest(item, 3))),
                "invalid-split-request")).isInstanceOf(org.thomcgn.backend.common.exception.BadRequestException.class);
        assertThat(jdbc.queryForObject("select count(*) from table_orders", Integer.class)).isEqualTo(1);
        assertThat(orders.getById(orderId).items().getFirst().quantity()).isEqualTo(2);

        var closed = orders.close(orderId);
        var replay = orders.close(orderId);
        assertThat(replay.id()).isEqualTo(closed.id());
        assertThat(jdbc.queryForObject("select count(*) from table_orders where paid", Integer.class)).isEqualTo(1);
    }

    @Test
    void reorderFailureRollsBackOrderItemStockMovementAndSalesAggregate() {
        long variant = stockedVariant("Atomic", 250, "3.00", "1.0000");
        var order = orders.open(new OpenTableOrderRequest(1L, null));
        doThrow(new IllegalStateException("reorder unavailable"))
                .doCallRealMethod()
                .when(reorderCalculations).calculateReorderAmount(any());

        assertThatThrownBy(() -> orders.addItem(order.id(),
                new AddTableOrderItemRequest(variant, 1), "atomic-failure-add"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(orders.getById(order.id()).items()).isEmpty();
        assertThat(stock(variant)).isEqualByComparingTo("1.0000");
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from drink_sales_daily", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from billing_operations", Integer.class)).isZero();
    }

    @Test
    void manualAdjustmentIdempotencyPreventsDuplicateMovement() {
        long variant = stockedVariant("Manual", 250, "1.00", "10");
        long inventoryId = jdbc.queryForObject(
                "select id from inventory_items where linked_drink_variant_id=?", Long.class, variant);
        var request = new InventoryAdjustmentRequest(new BigDecimal("2.1250"), "Count correction", false);
        inventory.adjust(inventoryId, request, "tester", "same-adjustment");
        inventory.adjust(inventoryId, request, "tester", "same-adjustment");

        assertThat(stock(variant)).isEqualByComparingTo("7.8750");
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> inventory.adjust(inventoryId,
                new InventoryAdjustmentRequest(BigDecimal.ONE, "Count correction", false),
                "tester", "same-adjustment")).isInstanceOf(ConflictException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADD", "REMOVE", "CLOSE", "SPLIT_PART", "SPLIT_ALL", "ARCHIVE", "REOPEN"})
    void commitFailureRollsBackWholeCommandAndSameKeyCanRetry(String command) {
        long variant = stockedVariant("Commit failure", 250, "3.00", "10");
        var opened = orders.open(new OpenTableOrderRequest(1L, null));
        var populated = orders.addItem(opened.id(), new AddTableOrderItemRequest(variant, 2), "phase3-setup");
        long orderId = populated.id();
        long itemId = populated.items().getFirst().id();
        if (command.equals("REOPEN")) orders.markUnpaid(orderId, "phase3-archive-setup");
        String key = "phase3-commit-failure";
        Runnable action = switch (command) {
            case "ADD" -> () -> orders.addItem(orderId, new AddTableOrderItemRequest(variant, 1), key);
            case "REMOVE" -> () -> orders.removeItem(orderId, itemId, key);
            case "CLOSE" -> () -> orders.close(orderId, key);
            case "ARCHIVE" -> () -> orders.markUnpaid(orderId, key);
            case "REOPEN" -> () -> orders.reopenUnpaid(orderId, key);
            case "SPLIT_PART", "SPLIT_ALL" -> () -> orders.splitPayment(orderId,
                    new SplitTableOrderPaymentRequest(List.of(new SplitTableOrderItemRequest(
                            itemId, command.equals("SPLIT_ALL") ? 2 : 1))), key);
            default -> throw new AssertionError(command);
        };
        var date = businessSettings.getCurrentBusinessDate();
        var before = businessSnapshot();
        var revenueBefore = shifts.getByDate(date).dailyRevenue();
        // Deferred constraint: fail at COMMIT, after all statements and response mapping.
        jdbc.execute("""
                create function phase3_reject_commit() returns trigger language plpgsql as $$
                begin
                  if new.operation_key = 'phase3-commit-failure' then
                    raise exception 'phase3 injected commit failure' using errcode = '23514';
                  end if;
                  return new;
                end $$
                """);
        try {
            jdbc.execute("""
                    create constraint trigger phase3_commit_failure after insert on billing_operations
                    deferrable initially deferred for each row execute function phase3_reject_commit()
                    """);
            assertThatThrownBy(action::run).hasStackTraceContaining("phase3 injected commit failure");
            assertThat(businessSnapshot()).isEqualTo(before);
            assertThat(shifts.getByDate(date).dailyRevenue()).isEqualByComparingTo(revenueBefore);
        } finally {
            jdbc.execute("drop trigger if exists phase3_commit_failure on billing_operations");
            jdbc.execute("drop function phase3_reject_commit()");
        }
        action.run();
        var committed = businessSnapshot();
        assertThat(committed).isNotEqualTo(before);
        action.run();
        assertThat(businessSnapshot()).isEqualTo(committed);
        assertThat(jdbc.queryForObject("select count(*) from billing_operations where operation_key=?",
                Integer.class, key)).isEqualTo(1);
        String expectedStock = command.equals("ADD") ? "9.25" : command.equals("REMOVE") ? "9.75" : "9.50";
        assertThat(stock(variant)).isEqualByComparingTo(expectedStock);
        String revenue = switch (command) {
            case "CLOSE", "SPLIT_ALL" -> "6";
            case "SPLIT_PART" -> "3";
            default -> "0";
        };
        assertThat(shifts.getByDate(date).dailyRevenue()).isEqualByComparingTo(revenue);
        assertThat(reports.getOverview().dayRevenue()).isEqualByComparingTo(revenue);
    }

    @Test
    void deliveryStatusWriteFailureRollsBackAlreadyFlushedStockAndMovement() {
        long variant = stockedVariant("Atomic delivery", 250, "3.00", "10");
        long deliveryId = delivery(variant, "LITER", "2");
        var before = businessSnapshot();
        jdbc.execute("alter table reorder_orders add constraint phase3_delivery_failure check(status <> 'RECEIVED')");
        try {
            assertThatThrownBy(() -> deliveries.updateReorderStatus(deliveryId, "RECEIVED"))
                    .hasStackTraceContaining("phase3_delivery_failure");
            assertThat(businessSnapshot()).isEqualTo(before);
        } finally {
            jdbc.execute("alter table reorder_orders drop constraint phase3_delivery_failure");
        }
        deliveries.updateReorderStatus(deliveryId, "RECEIVED");
        var committed = businessSnapshot();
        deliveries.updateReorderStatus(deliveryId, "RECEIVED");
        assertThat(businessSnapshot()).isEqualTo(committed);
        assertThat(stock(variant)).isEqualByComparingTo("12");
    }

    @Test
    void databaseReorderFailureRollsBackExistingItemIncrementAndCanRetry() {
        long variant = stockedVariant("Atomic reorder", 250, "3.00", "10");
        var order = orders.open(new OpenTableOrderRequest(1L, null));
        orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 1), "phase3-reorder-setup");
        var before = businessSnapshot();
        jdbc.execute("alter table reorder_calculations add constraint phase3_reorder_failure check(current_stock_amount >= 9.75)");
        try {
            assertThatThrownBy(() -> orders.addItem(order.id(),
                    new AddTableOrderItemRequest(variant, 1), "phase3-reorder-retry"))
                    .hasStackTraceContaining("phase3_reorder_failure");
            assertThat(businessSnapshot()).isEqualTo(before);
        } finally {
            jdbc.execute("alter table reorder_calculations drop constraint phase3_reorder_failure");
        }
        orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 1), "phase3-reorder-retry");
        var committed = businessSnapshot();
        orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 1), "phase3-reorder-retry");
        assertThat(businessSnapshot()).isEqualTo(committed);
        assertThat(stock(variant)).isEqualByComparingTo("9.50");
        assertThat(orders.getById(order.id()).items().getFirst().quantity()).isEqualTo(2);
    }

    // Read after the service transaction ends, through JDBC rather than an ORM identity cache.
    // Compare all columns, including inventory revisions, timestamps and recommendation values.
    // Sequence gaps are deliberately excluded: PostgreSQL sequences do not roll back.
    private java.util.Map<String, String> businessSnapshot() {
        var snapshot = new java.util.LinkedHashMap<String, String>();
        for (String table : List.of("tables", "table_orders", "table_order_items", "billing_operations",
                "inventory_items", "inventory_movements", "drink_sales_daily", "drink_sales_weekly",
                "business_audit_events", "reorder_calculations", "reorder_orders", "reservations", "shift_settlements", "shift_worker_entries")) {
            snapshot.put(table, jdbc.queryForObject(
                    "select coalesce(jsonb_agg(to_jsonb(t) order by t.id), '[]'::jsonb)::text from " + table + " t",
                    String.class));
        }
        return snapshot;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void priceChangesPreserveBookedPricesAndNewSalesUseSeparatePositions(boolean standardPrice) {
        long variant = stockedVariant("Price binding", 250, "3.00", "10");
        long drink = jdbc.queryForObject("select drink_id from drink_variants where id=?", Long.class, variant);
        if (standardPrice) {
            menu.createOrUpdateVolumePrice(new org.thomcgn.backend.menu.api.dto.VolumePriceRequest(250, new BigDecimal("3")));
            jdbc.update("update drink_variants set use_volume_standard_price=true where id=?", variant);
        }
        var order = orders.open(new OpenTableOrderRequest(1L, null));
        var original = orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 2), "phase6-original");
        if (standardPrice) menu.updateVolumePrice(250, new org.thomcgn.backend.menu.api.dto.VolumePriceUpdateRequest(new BigDecimal("4")));
        else menu.updateVariant(variant, new org.thomcgn.backend.menu.api.dto.DrinkVariantRequest(
                drink, "Serving", 250, new BigDecimal("4"), false, null, true));
        assertThat(orders.getById(order.id()).total()).isEqualByComparingTo("6");
        var updated = orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 1), "phase6-new-price");
        assertThat(updated.items()).hasSize(2);
        assertThat(updated.total()).isEqualByComparingTo("10");
        var split = orders.splitPayment(order.id(), new SplitTableOrderPaymentRequest(List.of(
                new SplitTableOrderItemRequest(original.items().getFirst().id(), 1))), "phase6-split");
        assertThat(split.paidOrder().total()).isEqualByComparingTo("3");
        assertThat(split.openOrder().total()).isEqualByComparingTo("7");
    }

    @Test
    void auditCapturesActorAndPaymentExactlyOnceWithoutCredentials() {
        long variant = stockedVariant("Audited", 250, "3", "10");
        String email = "audit-" + java.util.UUID.randomUUID() + "@example.test";
        long userId = jdbc.queryForObject("insert into app_users(name,email,password_hash,role,active) values('Audit',?,'unused','ADMIN',true) returning id", Long.class, email);
        long marker = auditMarker();
        var auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                email, "must-not-be-audited", List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        org.slf4j.MDC.put("requestId", "phase6-audit-request");
        try {
            var order = orders.open(new OpenTableOrderRequest(1L, null));
            var populated = orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 2), "audit-add");
            orders.removeItem(order.id(), populated.items().getFirst().id(), "audit-cancel");
            orders.close(order.id(), "audit-close");
            long committed = auditMarker();
            orders.close(order.id(), "audit-close");
            assertThat(auditMarker()).isEqualTo(committed);
            assertThat(jdbc.queryForList("select distinct actor from business_audit_events where id>?", String.class, marker))
                    .containsExactly("user:" + userId);
            assertThat(jdbc.queryForList("select distinct request_id from business_audit_events where id>?", String.class, marker))
                    .containsExactly("phase6-audit-request");
            assertThat(jdbc.queryForList("select distinct action from business_audit_events where id>?", String.class, marker))
                    .contains("PAYMENT_CLOSE", "ITEM_REDUCTION", "INVENTORY_MOVEMENT");
            assertThat(jdbc.queryForObject("select string_agg(metadata::text, '') from business_audit_events where id>?", String.class, marker))
                    .doesNotContain(email, "must-not-be-audited", "password_hash", "accessToken", "refreshToken");
            assertThatThrownBy(() -> orders.removeItem(order.id(), populated.items().getFirst().id(), "paid-cancel"))
                    .isInstanceOf(ConflictException.class);
            assertThat(auditMarker()).isEqualTo(committed);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            org.slf4j.MDC.remove("requestId");
        }
        long markerAfterUser = auditMarker();
        orders.open(new OpenTableOrderRequest(2L, null));
        assertThat(jdbc.queryForList("select distinct actor from business_audit_events where id>?", String.class, markerAfterUser))
                .containsExactly("system:internal");
    }

    @Test
    void auditFailureRollsBackBookingAndAllowsSameKeyRetry() {
        long variant = stockedVariant("Audit failure", 250, "3", "10");
        var order = orders.open(new OpenTableOrderRequest(1L, null));
        var before = businessSnapshot();
        jdbc.execute("alter table business_audit_events add constraint phase6_audit_failure check(action <> 'INVENTORY_MOVEMENT') not valid");
        try {
            assertThatThrownBy(() -> orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 1), "audit-retry"))
                    .hasStackTraceContaining("phase6_audit_failure");
            assertThat(businessSnapshot()).isEqualTo(before);
        } finally {
            jdbc.execute("alter table business_audit_events drop constraint phase6_audit_failure");
        }
        orders.addItem(order.id(), new AddTableOrderItemRequest(variant, 1), "audit-retry");
        assertThat(stock(variant)).isEqualByComparingTo("9.75");
    }

    @Test
    void auditHistoryCannotBeUpdatedDeletedOrTruncated() {
        orders.open(new OpenTableOrderRequest(1L, null));
        long marker = auditMarker();
        assertThatThrownBy(() -> jdbc.update("update business_audit_events set actor='changed' where id=?", marker))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from business_audit_events where id=?", marker))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("truncate business_audit_events"))
                .hasStackTraceContaining("append-only");
        assertThat(auditMarker()).isEqualTo(marker);
    }

    @Test
    void priceAndShiftCorrectionsKeepBeforeAndAfterValues() {
        menu.createOrUpdateVolumePrice(new org.thomcgn.backend.menu.api.dto.VolumePriceRequest(777, new BigDecimal("3")));
        long marker = auditMarker();
        menu.updateVolumePrice(777, new org.thomcgn.backend.menu.api.dto.VolumePriceUpdateRequest(new BigDecimal("4")));
        assertThat(jdbc.queryForObject("select metadata->'before'->>'price' from business_audit_events where id>? and entity_type='volume_prices'", String.class, marker)).isEqualTo("3.00");
        assertThat(jdbc.queryForObject("select metadata->'after'->>'price' from business_audit_events where id>? and entity_type='volume_prices'", String.class, marker)).isEqualTo("4.00");
        var date = java.time.LocalDate.of(2040, 4, 6);
        jdbc.update("delete from shift_settlements where settlement_date=?", date);
        var original = shifts.saveByDate(date, new org.thomcgn.backend.shift.api.dto.ShiftSettlementRequest(
                new BigDecimal("100"), BigDecimal.ZERO, List.of(), 0L));
        marker = auditMarker();
        shifts.saveByDate(date, new org.thomcgn.backend.shift.api.dto.ShiftSettlementRequest(
                new BigDecimal("120"), BigDecimal.ZERO, List.of(), original.revision()));
        assertThat(jdbc.queryForObject("select metadata->'before'->>'opening_cash' from business_audit_events where id>? and entity_type='shift_settlements'", String.class, marker)).isEqualTo("100.00");
        assertThat(jdbc.queryForObject("select metadata->'after'->>'opening_cash' from business_audit_events where id>? and entity_type='shift_settlements'", String.class, marker)).isEqualTo("120.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CASH", "CARD"})
    void directSaleUsesSharedStockRevenueShiftArchiveAndAuditWithoutATable(String method) {
        long variant = stockedVariant("Direct", 250, "3", "10");
        var date = businessSettings.getCurrentBusinessDate();
        jdbc.update("delete from shift_settlements where settlement_date=?", date);
        var request = new DirectSaleRequest(org.thomcgn.backend.billing.domain.PaymentMethod.valueOf(method),
                List.of(new DirectSaleRequest.Item(variant, 2, new BigDecimal("3.00"))));
        String key = java.util.UUID.randomUUID().toString();
        long marker = auditMarker();
        var receipt = orders.directSale(request, key);
        assertThat(receipt.saleType()).isEqualTo(org.thomcgn.backend.billing.domain.SaleType.DIRECT);
        assertThat(receipt.paymentMethod().name()).isEqualTo(method);
        assertThat(receipt.status()).isEqualTo(org.thomcgn.backend.billing.domain.TableOrderStatus.CLOSED);
        assertThat(receipt.paid()).isTrue();
        assertThat(receipt.tableId()).isNull();
        assertThat(receipt.reservationId()).isNull();
        assertThat(receipt.total()).isEqualByComparingTo("6");
        assertThat(orders.directSale(request, key).id()).isEqualTo(receipt.id());
        assertThat(stock(variant)).isEqualByComparingTo("9.5");
        assertThat(jdbc.queryForObject("select count(*) from tables where status <> 'FREE'", Integer.class)).isZero();
        assertThat(reports.getOverview().dayRevenue()).isEqualByComparingTo("6");
        assertThat(shifts.getByDate(date).dailyRevenue()).isEqualByComparingTo("6");
        assertThat(shifts.getByDate(date).expectedClosingCash()).isEqualByComparingTo(method.equals("CASH") ? "6" : "0");
        var savedShift = shifts.saveByDate(date, new org.thomcgn.backend.shift.api.dto.ShiftSettlementRequest(
                new BigDecimal("100"), new BigDecimal("5"), List.of(), shifts.getByDate(date).revision()));
        assertThat(savedShift.expectedClosingCash()).isEqualByComparingTo(method.equals("CASH") ? "101" : "95");
        assertThat(jdbc.queryForObject("select quantity_sold from drink_sales_daily where drink_variant_id=?", BigDecimal.class, variant)).isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("select volume_sold_ml from drink_sales_daily where drink_variant_id=?", BigDecimal.class, variant)).isEqualByComparingTo("500");
        assertThat(orders.searchArchive(date, null, "PAID")).extracting(TableOrderResponse::id).contains(receipt.id());
        assertThat(orders.searchArchive(date, "Barverkauf", "PAID")).extracting(TableOrderResponse::id).contains(receipt.id());
        assertThat(jdbc.queryForObject("select count(*) from business_audit_events where id>? and action='PAYMENT_CLOSE' and metadata->'after'->>'sale_type'='DIRECT'", Integer.class, marker)).isEqualTo(1);
        assertThatThrownBy(() -> orders.markUnpaid(receipt.id())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> orders.reopenUnpaid(receipt.id())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> orders.addItem(receipt.id(), new AddTableOrderItemRequest(variant, 1))).isInstanceOf(ConflictException.class);
        var changed = new DirectSaleRequest(request.paymentMethod(), List.of(new DirectSaleRequest.Item(variant, 1, new BigDecimal("3"))));
        assertThatThrownBy(() -> orders.directSale(changed, key)).isInstanceOf(ConflictException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void failedDirectSaleRollsBackWholeCartIncludingInventoryAndAudit(boolean stalePrice) {
        long first = stockedVariant("First direct", 250, "3", "10");
        long second = stockedVariant("Second direct", 250, "4", stalePrice ? "10" : "0.1");
        long marker = auditMarker();
        var request = new DirectSaleRequest(org.thomcgn.backend.billing.domain.PaymentMethod.CASH, List.of(
                new DirectSaleRequest.Item(first, 1, new BigDecimal("3")),
                new DirectSaleRequest.Item(second, 1, new BigDecimal(stalePrice ? "3" : "4"))));
        assertThatThrownBy(() -> orders.directSale(request, "direct-rollback")).isInstanceOf(ConflictException.class);
        assertThat(stock(first)).isEqualByComparingTo("10");
        assertThat(stock(second)).isEqualByComparingTo(stalePrice ? "10" : "0.1");
        assertThat(jdbc.queryForObject("select count(*) from table_orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from billing_operations", Integer.class)).isZero();
        assertThat(auditMarker()).isEqualTo(marker);
    }

    @Test
    void simultaneousDirectRetriesCommitOnlyOneSale() throws Exception {
        long variant = stockedVariant("Concurrent direct", 250, "3", "10");
        var request = new DirectSaleRequest(org.thomcgn.backend.billing.domain.PaymentMethod.CARD,
                List.of(new DirectSaleRequest.Item(variant, 2, new BigDecimal("3"))));
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.Callable<TableOrderResponse> sale = () -> {
            barrier.await();
            return orders.directSale(request, "direct-concurrent");
        };
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = pool.submit(sale); var b = pool.submit(sale);
            assertThat(a.get().id()).isEqualTo(b.get().id());
        }
        assertThat(stock(variant)).isEqualByComparingTo("9.5");
        assertThat(jdbc.queryForObject("select count(*) from table_orders", Integer.class)).isEqualTo(1);
    }

    @Test
    void directSaleOperationCommitFailureRollsBackAllEffects() {
        long variant = stockedVariant("Direct late failure", 250, "3", "10");
        long marker = auditMarker();
        var request = new DirectSaleRequest(org.thomcgn.backend.billing.domain.PaymentMethod.CASH,
                List.of(new DirectSaleRequest.Item(variant, 1, new BigDecimal("3"))));
        jdbc.execute("alter table billing_operations add constraint phase7_failure check(operation_key <> 'direct-late-failure')");
        try {
            assertThatThrownBy(() -> orders.directSale(request, "direct-late-failure"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(stock(variant)).isEqualByComparingTo("10");
            assertThat(jdbc.queryForObject("select count(*) from table_orders", Integer.class)).isZero();
            assertThat(auditMarker()).isEqualTo(marker);
        } finally { jdbc.execute("alter table billing_operations drop constraint phase7_failure"); }
    }

    @Test
    void databaseRejectsUnfinishedDirectSales() {
        assertThatThrownBy(() -> jdbc.update("insert into table_orders(sale_type,payment_method,status,paid,opened_at) values('DIRECT','CASH','OPEN',false,now())"))
                .hasStackTraceContaining("Direct sale must be paid and closed");
        assertThatThrownBy(() -> jdbc.update("insert into table_orders(sale_type,payment_method,status,paid,opened_at) values('TABLE','CASH','OPEN',false,now())"))
                .hasStackTraceContaining("ck_order_sale_shape");
    }

    private long auditMarker() {
        return jdbc.queryForObject("select coalesce(max(id),0) from business_audit_events", Long.class);
    }

    private long stockedVariant(String name, int volumeMl, String price, String stock) {
        long category = jdbc.queryForObject(
                "insert into drink_categories(name,sort_order,active) values(?,0,true) returning id",
                Long.class, name + "-category");
        long drink = jdbc.queryForObject(
                "insert into drinks(category_id,name,active) values(?,?,true) returning id",
                Long.class, category, name);
        long variant = jdbc.queryForObject("""
                insert into drink_variants(drink_id,display_volume_name,volume_ml,price,active,use_volume_standard_price)
                values(?,'Serving',?,?,true,false) returning id
                """, Long.class, drink, volumeMl, new BigDecimal(price));
        jdbc.update("""
                insert into inventory_items(name,linked_drink_variant_id,package_type,packages_in_stock,content_per_package,
                  content_unit,total_stock_amount,reorder_threshold,minimum_stock,recommended_reorder_amount,active)
                values(?,?,'BARREL',1,?,'LITER',?,0,0,0,true)
                """, name + " stock", variant, new BigDecimal(stock), new BigDecimal(stock));
        return variant;
    }

    private BigDecimal stock(long variant) {
        return jdbc.queryForObject(
                "select total_stock_amount from inventory_items where linked_drink_variant_id=?",
                BigDecimal.class, variant);
    }
}
