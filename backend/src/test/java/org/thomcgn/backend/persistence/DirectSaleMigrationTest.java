package org.thomcgn.backend.persistence;

import org.junit.jupiter.api.Test;
import org.thomcgn.backend.support.MigratedPostgresTest;
import static org.assertj.core.api.Assertions.*;

class DirectSaleMigrationTest extends MigratedPostgresTest {
    @Override protected String migrationTarget() { return "31"; }

    @Test void upgradeKeepsExistingOpenAndPaidTableReceipts() throws Exception {
        try (var connection = databaseConnection(); var sql = connection.createStatement()) {
            sql.execute("insert into tables(name,status,active) values('Existing','OCCUPIED',true)");
            sql.execute("insert into table_orders(table_id,status,paid,opened_at) select id,'OPEN',false,now() from tables");
            sql.execute("insert into table_orders(table_id,status,paid,opened_at,closed_at,closed_business_date) select id,'CLOSED',true,now(),now(),current_date from tables");
        }
        assertThat(migration("latest").migrate().migrationsExecuted).isEqualTo(1);
        try (var connection = databaseConnection(); var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("select count(*) from table_orders where sale_type='TABLE' and payment_method is null and table_id is not null")) {
                rows.next(); assertThat(rows.getInt(1)).isEqualTo(2);
            }
            sql.execute("insert into table_orders(sale_type,payment_method,status,paid,opened_at,closed_at,closed_business_date) values('DIRECT','CASH','CLOSED',true,now(),now(),current_date)");
            assertThatThrownBy(() -> sql.execute("update table_orders set status='OPEN',paid=false where sale_type='DIRECT'"))
                    .hasMessageContaining("Direct sale must be paid and closed");
        }
        assertThat(migration("latest").migrate().migrationsExecuted).isZero();
    }
}
