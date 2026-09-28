package org.thomcgn.backend.persistence;

import org.junit.jupiter.api.Test;
import org.thomcgn.backend.support.MigratedPostgresTest;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;

class BarchefRoleMigrationTest extends MigratedPostgresTest {
    @Override
    protected String migrationTarget() { return "21"; }

    @Test
    void upgradePreservesAccountsAndAcceptsOnlySupportedRoles() throws Exception {
        assertThat(migration("latest").migrate().migrationsExecuted).isEqualTo(11);
        try (var connection = databaseConnection(); var sql = connection.createStatement()) {
            sql.execute("insert into app_users(name,email,password_hash,role,active) values('Barchef','bar@example.test','not-a-login-hash','BARCHEF',false)");
            try (var rows = sql.executeQuery("select count(*) from app_users where role in ('ADMIN','STAFF')")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
            assertThatThrownBy(() -> sql.execute("update app_users set role='UNKNOWN'"))
                    .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23514"));
        }
    }
}
