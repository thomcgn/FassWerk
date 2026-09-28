package org.thomcgn.backend.persistence;

import org.junit.jupiter.api.Test;
import org.thomcgn.backend.support.MigratedPostgresTest;
import static org.assertj.core.api.Assertions.*;

class BusinessAuditMigrationTest extends MigratedPostgresTest {
    @Override protected String migrationTarget() { return "30"; }

    @Test void upgradeKeepsExistingPricesAndStartsHistoryWithoutInventedActors() throws Exception {
        try (var connection = databaseConnection(); var sql = connection.createStatement()) {
            sql.execute("insert into volume_prices(volume_ml,price) values(777,3)");
        }
        assertThat(migration("latest").migrate().migrationsExecuted).isEqualTo(2);
        try (var connection = databaseConnection(); var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("select count(*) from business_audit_events")) {
                rows.next(); assertThat(rows.getLong(1)).isZero();
            }
            sql.execute("update volume_prices set price=4 where volume_ml=777");
            try (var rows = sql.executeQuery("select actor, metadata->'before'->>'price',metadata->'after'->>'price' from business_audit_events")) {
                rows.next();
                assertThat(rows.getString(1)).isEqualTo("system:unattributed");
                assertThat(rows.getString(2)).isEqualTo("3.00");
                assertThat(rows.getString(3)).isEqualTo("4.00");
            }
        }
    }
}
