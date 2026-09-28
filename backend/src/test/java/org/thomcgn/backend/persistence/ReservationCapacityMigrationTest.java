package org.thomcgn.backend.persistence;

import org.junit.jupiter.api.Test;
import org.thomcgn.backend.support.MigratedPostgresTest;
import static org.assertj.core.api.Assertions.*;

class ReservationCapacityMigrationTest extends MigratedPostgresTest {
    @Override protected String migrationTarget() { return "22"; }

    @Test void upgradePreservesLegacyAssignmentWithoutInventingCapacityOrTimeZone() throws Exception {
        long tableId;
        long reservationId;
        try (var connection=databaseConnection(); var sql=connection.createStatement()) {
            try(var rows=sql.executeQuery("insert into tables(name,area,status,active) values('Migration table','ROOM','FREE',true) returning id")) {
                rows.next(); tableId=rows.getLong(1);
            }
            try(var rows=sql.executeQuery("insert into reservations(guest_name,reservation_date,reservation_time,guest_count,status,qr_code_token,assigned_table_id) values('Legacy','2035-06-01','18:00',6,'CONFIRMED','migration-qr',"+tableId+") returning id")) {
                rows.next(); reservationId=rows.getLong(1);
            }
        }
        assertThat(migration("latest").migrate().migrationsExecuted).isEqualTo(2);
        try (var connection=databaseConnection(); var sql=connection.createStatement()) {
            try(var rows=sql.executeQuery("select t.seats,r.starts_at,r.duration_minutes,r.reservation_zone,rt.table_id,r.guest_count from reservations r join reservation_tables rt on rt.reservation_id=r.id join tables t on t.id=rt.table_id where r.id="+reservationId)) {
                assertThat(rows.next()).isTrue();
                for(int i=1;i<=4;i++) assertThat(rows.getObject(i)).isNull();
                assertThat(rows.getLong(5)).isEqualTo(tableId);
                assertThat(rows.getInt(6)).isEqualTo(6);
            }
            assertThatThrownBy(() -> sql.execute("update tables set seats=0 where id="+tableId))
                .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
