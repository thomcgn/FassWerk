package org.thomcgn.backend.persistence;

import org.junit.jupiter.api.Test;
import org.thomcgn.backend.support.MigratedPostgresTest;

import java.math.BigDecimal;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingInventoryMigrationTest extends MigratedPostgresTest {
    @Override
    protected String migrationTarget() {
        return "24";
    }

    @Test
    void upgradeAddsPrecisionIdempotencyAndNonnegativeStockGuard() throws Exception {
        long inventoryItem;
        try (var connection = databaseConnection(); var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("""
                    insert into inventory_items(name,package_type,packages_in_stock,content_per_package,content_unit,
                      total_stock_amount,reorder_threshold,minimum_stock,recommended_reorder_amount,active)
                    values('Precision','BARREL',1,0.01,'LITER',0.01,0,0,0,true) returning id
                    """)) {
                rows.next();
                inventoryItem = rows.getLong(1);
            }
        }

        assertThat(migration("latest").migrate().migrationsExecuted).isEqualTo(1);

        try (var connection = databaseConnection(); var sql = connection.createStatement()) {
            sql.execute("update inventory_items set content_per_package=0.0020,total_stock_amount=0.0010 where id=" + inventoryItem);
            try (var rows = sql.executeQuery("select content_per_package,total_stock_amount from inventory_items where id=" + inventoryItem)) {
                rows.next();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo(new BigDecimal("0.0020"));
                assertThat(rows.getBigDecimal(2)).isEqualByComparingTo(new BigDecimal("0.0010"));
            }
            assertThatThrownBy(() -> sql.execute(
                    "update inventory_items set total_stock_amount=-0.0001 where id=" + inventoryItem))
                    .isInstanceOfSatisfying(SQLException.class,
                            error -> assertThat(error.getSQLState()).isEqualTo("23514"));
            try (var rows = sql.executeQuery("select count(*) from billing_operations")) {
                rows.next();
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }
}
