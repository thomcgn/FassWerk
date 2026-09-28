package org.thomcgn.backend.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.thomcgn.backend.support.MigratedPostgresTest;

import java.sql.SQLException;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostgresSchemaIntegrationTest extends MigratedPostgresTest {
    @Test
    void emptyDatabaseReachesCompleteSchemaOnlyThroughFlyway() throws Exception {
        assertThat(flyway.info().applied()).hasSize(18);
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try (var connection = databaseConnection(); var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("""
                    select tablename from pg_tables where schemaname = 'public'
                    and tablename <> 'flyway_schema_history'
                    """)) {
                var tables = new ArrayList<String>();
                while (rows.next()) tables.add(rows.getString(1));
                assertThat(tables).containsExactlyInAnyOrder(
                        "billing_operations", "reservation_tables", "app_users", "refresh_tokens", "revoked_access_tokens", "tables", "reservations",
                        "opening_hours", "booking_slot_config", "drink_categories", "drinks", "drink_variants",
                        "volume_prices", "inventory_items", "inventory_movements", "table_orders", "table_order_items",
                        "shift_settlements", "shift_worker_entries", "inventory_business_settings", "drink_sales_daily",
                        "drink_sales_weekly", "reorder_calculations", "consumption_metadata", "suppliers", "reorder_orders");
            }
            try (var rows = sql.executeQuery("select count(*) from app_users where active or password_hash <> 'DISABLED_LEGACY_SEED'")) {
                rows.next();
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "insert into tables(name,status,active) values('A','FREE',true),('A','FREE',true)|23505",
            "insert into drink_categories(name,sort_order,active) values('A',0,true),('A',1,true)|23505",
            "insert into volume_prices(volume_ml,price) values(9876,1),(9876,2)|23505",
            "insert into refresh_tokens(token_id,user_id,expires_at) select 'duplicate',id,now() from app_users|23505",
            "insert into revoked_access_tokens(token_id,expires_at,revoked_at) values('same',now(),now()),('same',now(),now())|23505",
            "insert into refresh_tokens(token_id,user_id,expires_at) values('orphan',-1,now())|23503",
            "insert into tables(name,status,active) values(null,'FREE',true)|23502"
    })
    void databaseEnforcesConstraints(String statement, String sqlState) throws Exception {
        try (var connection = databaseConnection(); var sql = connection.createStatement()) {
            assertThatThrownBy(() -> sql.execute(statement)).isInstanceOfSatisfying(SQLException.class,
                    error -> assertThat(error.getSQLState()).isEqualTo(sqlState));
        }
    }
    @Test
    void reservationQrTokensAreUnique() throws Exception {
        try (var connection = databaseConnection(); var sql = connection.createStatement()) {
            String insert = """
                    insert into reservations(guest_name,reservation_date,reservation_time,guest_count,status,qr_code_token)
                    values('Guest', current_date + 7, '18:00', 1, 'PENDING', 'same-qr')
                    """;
            sql.execute(insert);
            assertThatThrownBy(() -> sql.execute(insert)).isInstanceOfSatisfying(SQLException.class,
                    error -> assertThat(error.getSQLState()).isEqualTo("23505"));
        }
    }

    @ParameterizedTest
    @CsvSource({"drink_sales_daily,sale_date", "drink_sales_weekly,week_start_date"})
    void salesAggregateKeysAreUniqueForNonNullVariants(String table, String dateColumn) throws Exception {
        try (var connection = databaseConnection(); var sql = connection.createStatement()) {
            sql.execute("insert into drink_categories(id,name,sort_order,active) values(100,'Test',0,true)");
            sql.execute("insert into drinks(id,category_id,name,active) values(100,100,'Test drink',true)");
            sql.execute("insert into drink_variants(id,drink_id,display_volume_name,volume_ml,price,active) values(100,100,'Glass',250,3,true)");
            String insert = "insert into " + table + "(drink_id,drink_variant_id," + dateColumn + ") values(100,100,current_date)";
            sql.execute(insert);
            assertThatThrownBy(() -> sql.execute(insert)).isInstanceOfSatisfying(SQLException.class,
                    error -> assertThat(error.getSQLState()).isEqualTo("23505"));
            // PostgreSQL UNIQUE allows duplicate null-variant aggregates: an explicit known gap.
            sql.execute("insert into " + table + "(drink_id," + dateColumn + ") values(100,current_date),(100,current_date)");
            try (var rows = sql.executeQuery("select count(*) from " + table + " where drink_variant_id is null")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
        }
    }

}
