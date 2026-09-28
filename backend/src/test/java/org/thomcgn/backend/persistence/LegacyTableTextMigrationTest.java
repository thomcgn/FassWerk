package org.thomcgn.backend.persistence;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thomcgn.backend.support.MigratedPostgresTest;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyTableTextMigrationTest extends MigratedPostgresTest {
    @Override
    protected String migrationTarget() { return "17"; }

    @ParameterizedTest
    @ValueSource(strings = {"name", "area"})
    void convertsLegacyBinaryTextWithoutDataLoss(String column) throws Exception {
        try (var connection = databaseConnection(); var sql = connection.createStatement()) {
            sql.execute("insert into tables(name,area,status,active) values('Terrasse','Garten','FREE',true)");
            sql.execute("alter table tables alter column " + column + " type bytea using convert_to(" + column + ", 'UTF8')");
            assertThat(migration("latest").migrate().migrationsExecuted).isEqualTo(11);
            try (var rows = sql.executeQuery("select name,area,pg_typeof(" + column + ")::text from tables")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("Terrasse");
                assertThat(rows.getString(2)).isEqualTo("Garten");
                assertThat(rows.getString(3)).isEqualTo("character varying");
            }
        }
    }
}
