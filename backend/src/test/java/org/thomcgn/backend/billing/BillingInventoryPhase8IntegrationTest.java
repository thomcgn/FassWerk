package org.thomcgn.backend.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    @Autowired InventoryService inventory;
    @Autowired DrinkSalesTrackingService salesTracking;
    @Autowired DrinkSalesDailyRepository dailySales;
    @Autowired InventoryInsightsApplicationService inventoryInsights;
    @Autowired MenuService menu;
    @MockitoSpyBean ReorderCalculationService reorderCalculations;

    @BeforeEach
    void fixtures() {
        jdbc.execute("truncate table tables, drink_categories, inventory_items restart identity cascade");
        jdbc.update("insert into tables(name,status,active) values('T1','FREE',true),('T2','FREE',true)");
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
