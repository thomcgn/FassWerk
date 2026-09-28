package org.thomcgn.backend.persistence;

import org.junit.jupiter.api.Test;
import org.thomcgn.backend.support.MigratedPostgresTest;
import static org.assertj.core.api.Assertions.*;

class ReservationPaymentMigrationTest extends MigratedPostgresTest {
    @Override protected String migrationTarget() { return "23"; }

    @Test void schemaUpgradePreservesSnapshotsAndAllowsUnboundedNewReservations() throws Exception {
        long reservation;
        long table;
        try(var connection=databaseConnection(); var sql=connection.createStatement()) {
            try(var rows=sql.executeQuery("insert into tables(name,status,active,seats) values('Upgrade','FREE',true,4) returning id")) {
                rows.next(); table=rows.getLong(1);
            }
            try(var rows=sql.executeQuery("""
                    insert into reservations(guest_name,reservation_date,reservation_time,guest_count,status,qr_code_token,
                    starts_at,ends_at,duration_minutes,check_in_deadline)
                    values('Preserve','2035-06-01','18:00',2,'CONFIRMED','preserve',
                    '2035-06-01T16:00:00Z','2035-06-01T19:00:00Z',180,'2035-06-01T16:15:00Z') returning id
                    """)) {
                rows.next(); reservation=rows.getLong(1);
            }
        }
        assertThat(migration("latest").migrate().migrationsExecuted).isEqualTo(7);
        try(var connection=databaseConnection(); var sql=connection.createStatement()) {
            try(var rows=sql.executeQuery("select duration_minutes,ends_at is not null from reservations where id="+reservation)) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(180);
                assertThat(rows.getBoolean(2)).isTrue();
            }
            sql.execute("update reservations set duration_minutes=null,ends_at=null where id="+reservation);
            sql.execute("insert into table_orders(table_id,status,paid,opened_at) values("+table+",'OPEN',false,now())");
            assertThatThrownBy(() -> sql.execute("insert into table_orders(table_id,status,paid,opened_at) values("+table+",'OPEN',false,now())"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test void duplicateLegacyBillsBlockUpgradeWithoutDeletingFinancialHistory() throws Exception {
        long table;
        try(var connection=databaseConnection(); var sql=connection.createStatement()) {
            try(var rows=sql.executeQuery("insert into tables(name,status,active) values('Duplicate','OCCUPIED',true) returning id")) {
                rows.next(); table=rows.getLong(1);
            }
            sql.execute("insert into table_orders(table_id,status,paid,opened_at) values("+table+",'OPEN',false,now()),("+table+",'OPEN',false,now())");
        }
        assertThatThrownBy(() -> migration("latest").migrate()).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
        try(var connection=databaseConnection(); var sql=connection.createStatement();
            var rows=sql.executeQuery("select count(*) from table_orders where table_id="+table)) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(2);
        }
    }
}
