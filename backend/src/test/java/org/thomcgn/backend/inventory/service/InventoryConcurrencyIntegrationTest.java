package org.thomcgn.backend.inventory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.thomcgn.backend.billing.api.dto.AddTableOrderItemRequest;
import org.thomcgn.backend.billing.api.dto.OpenTableOrderRequest;
import org.thomcgn.backend.billing.service.TableOrderService;
import org.thomcgn.backend.common.exception.ConflictException;
import org.thomcgn.backend.inventory.api.dto.InventoryAdjustmentRequest;
import org.thomcgn.backend.support.PostgresIntegrationTest;

import java.math.BigDecimal;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/** Separate service transactions and database connections; no enclosing test transaction. */
@SpringBootTest
@ActiveProfiles("test")
class InventoryConcurrencyIntegrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired TableOrderService orders;
    @Autowired InventoryService inventory;
    long firstOrder;
    long secondOrder;

    @BeforeEach
    void fixtures() {
        jdbc.execute("truncate table tables, drink_categories, inventory_items restart identity cascade");
        jdbc.update("insert into tables(name,status,active) values('A','FREE',true),('B','FREE',true)");
        jdbc.update("insert into drink_categories(name,sort_order,active) values('Concurrency',0,true)");
        jdbc.update("insert into drinks(category_id,name,active) values(1,'Shared drink',true)");
        jdbc.update("""
                insert into drink_variants(drink_id,display_volume_name,volume_ml,price,active,use_volume_standard_price)
                values(1,'One litre',1000,3,true,false),(1,'Half litre',500,2,true,false)
                """);
        jdbc.update("""
                insert into inventory_items(name,linked_drink_variant_id,package_type,packages_in_stock,
                  content_per_package,content_unit,total_stock_amount,reorder_threshold,minimum_stock,
                  recommended_reorder_amount,active)
                values('Shared stock',1,'BARREL',1,10,'LITER',10,0,0,0,true)
                """);
        firstOrder = orders.open(new OpenTableOrderRequest(1L, null)).id();
        secondOrder = orders.open(new OpenTableOrderRequest(2L, null)).id();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void parallelSalesPreserveBothDeductionsIncludingSharedDrinkStock(boolean sharedDrink) throws Exception {
        if (sharedDrink) jdbc.update("update inventory_items set linked_drink_variant_id=null,linked_drink_id=1 where id=1");
        var secondRequest = new AddTableOrderItemRequest(sharedDrink ? 2L : 1L, sharedDrink ? 6 : 3);
        race(() -> orders.addItem(firstOrder, new AddTableOrderItemRequest(1L, 2), "phase4-sale-a"),
                () -> orders.addItem(secondOrder, secondRequest, "phase4-sale-b"));
        assertStockAndMovements("5", "-5", 2);
        assertThat(jdbc.queryForObject("select sum(volume_sold_ml) from drink_sales_daily", BigDecimal.class))
                .isEqualByComparingTo("5000");
        assertThat(orders.getById(firstOrder).items().getFirst().quantity()).isEqualTo(2);
        assertThat(orders.getById(secondOrder).items().getFirst().quantity()).isEqualTo(secondRequest.quantity());
        assertThat(jdbc.queryForObject("select count(*) from billing_operations", Integer.class)).isEqualTo(2);
        // Paying both receipts must not consume the stock a second time.
        race(() -> orders.close(firstOrder, "phase4-pay-a"), () -> orders.close(secondOrder, "phase4-pay-b"));
        assertStockAndMovements("5", "-5", 2);
    }

    @Test
    void parallelRetryDeductsOnlyOnce() throws Exception {
        Runnable add = () -> orders.addItem(firstOrder, new AddTableOrderItemRequest(1L, 2), "phase4-same-add");
        race(add, add);
        add.run();
        assertStockAndMovements("8", "-2", 1);
        assertThat(orders.getById(firstOrder).items().getFirst().quantity()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from billing_operations", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select quantity_sold from drink_sales_daily", BigDecimal.class))
                .isEqualByComparingTo("2");
    }

    @Test
    void parallelOversellRejectsOneWholeCommandAndCanRetryAfterRestock() throws Exception {
        var successes = new AtomicInteger();
        var conflicts = new AtomicInteger();
        race(() -> attemptSixLitres(firstOrder, "phase4-six-a", successes, conflicts),
                () -> attemptSixLitres(secondOrder, "phase4-six-b", successes, conflicts));
        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        assertStockAndMovements("4", "-6", 1);
        assertThat(jdbc.queryForObject("select sum(quantity) from table_order_items", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from billing_operations", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select quantity_sold from drink_sales_daily", BigDecimal.class)).isEqualByComparingTo("6");
        boolean firstLost = orders.getById(firstOrder).items().isEmpty();
        long loser = firstLost ? firstOrder : secondOrder;
        String retryKey = firstLost ? "phase4-six-a" : "phase4-six-b";
        inventory.adjust(1L, adjustment("2", true), "test", "phase4-restock");
        orders.addItem(loser, new AddTableOrderItemRequest(1L, 6), retryKey);
        orders.addItem(loser, new AddTableOrderItemRequest(1L, 6), retryKey);
        assertStockAndMovements("0", "-10", 3);
        assertThat(jdbc.queryForObject("select quantity_sold from drink_sales_daily", BigDecimal.class)).isEqualByComparingTo("12");
    }

    @Test
    void saleAndManualCorrectionDoNotOverwriteEachOther() throws Exception {
        race(() -> orders.addItem(firstOrder, new AddTableOrderItemRequest(1L, 2), "phase4-correction-sale"),
                () -> inventory.adjust(1L, adjustment("3", false), "test", "phase4-correction"));
        assertStockAndMovements("5", "-5", 2);
        assertThat(jdbc.queryForObject("select quantity_sold from drink_sales_daily", BigDecimal.class)).isEqualByComparingTo("2");
    }

    @Test
    void parallelCorrectionRetryProducesOneMovementAndOneRevision() throws Exception {
        Runnable correction = () -> inventory.adjust(1L, adjustment("3", false), "test", "phase4-same-correction");
        race(correction, correction);
        assertStockAndMovements("7", "-3", 1);
        assertThat(jdbc.queryForObject("select revision from inventory_items where id=1", Long.class)).isEqualTo(1L);
    }

    @Test
    void cancellationAndAnotherSalePreserveNetStockAndSales() throws Exception {
        var existing = orders.addItem(firstOrder, new AddTableOrderItemRequest(1L, 2), "phase4-original");
        race(() -> orders.removeItem(firstOrder, existing.items().getFirst().id(), "phase4-cancel"),
                () -> orders.addItem(secondOrder, new AddTableOrderItemRequest(1L, 3), "phase4-new-sale"));
        assertStockAndMovements("6", "-4", 3);
        assertThat(jdbc.queryForObject("select quantity_sold from drink_sales_daily", BigDecimal.class)).isEqualByComparingTo("4");
        assertThat(orders.getById(firstOrder).items().getFirst().quantity()).isEqualTo(1);
        assertThat(orders.getById(secondOrder).items().getFirst().quantity()).isEqualTo(3);
    }

    @Test
    void rolledBackSaleDoesNotUndoConcurrentSuccessfulSale() throws Exception {
        jdbc.execute("alter table billing_operations add constraint phase4_failure check(operation_key <> 'phase4-failing-sale')");
        try {
            race(() -> assertThatThrownBy(() -> orders.addItem(firstOrder,
                            new AddTableOrderItemRequest(1L, 2), "phase4-failing-sale"))
                            .hasStackTraceContaining("phase4_failure"),
                    () -> orders.addItem(secondOrder, new AddTableOrderItemRequest(1L, 3), "phase4-survivor"));
            assertStockAndMovements("7", "-3", 1);
            assertThat(orders.getById(firstOrder).items()).isEmpty();
            assertThat(jdbc.queryForObject("select quantity_sold from drink_sales_daily", BigDecimal.class)).isEqualByComparingTo("3");
            assertThat(jdbc.queryForObject("select count(*) from billing_operations", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from reorder_calculations", Integer.class)).isEqualTo(1);
        } finally {
            jdbc.execute("alter table billing_operations drop constraint phase4_failure");
        }
        orders.addItem(firstOrder, new AddTableOrderItemRequest(1L, 2), "phase4-failing-sale");
        assertStockAndMovements("5", "-5", 2);
    }

    private void attemptSixLitres(long order, String key, AtomicInteger successes, AtomicInteger conflicts) {
        try {
            orders.addItem(order, new AddTableOrderItemRequest(1L, 6), key);
            successes.incrementAndGet();
        } catch (ConflictException expected) {
            conflicts.incrementAndGet();
        }
    }

    private InventoryAdjustmentRequest adjustment(String amount, boolean increase) {
        return new InventoryAdjustmentRequest(new BigDecimal(amount), "Concurrency correction", increase);
    }

    private void assertStockAndMovements(String stock, String delta, int movements) {
        assertThat(jdbc.queryForObject("select total_stock_amount from inventory_items where id=1", BigDecimal.class))
                .isEqualByComparingTo(stock);
        assertThat(jdbc.queryForObject("select sum(amount) from inventory_movements", BigDecimal.class))
                .isEqualByComparingTo(delta);
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements", Integer.class)).isEqualTo(movements);
    }

    private void race(Runnable first, Runnable second) throws Exception {
        var start = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); first.run(); return null; });
            var b = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); second.run(); return null; });
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
