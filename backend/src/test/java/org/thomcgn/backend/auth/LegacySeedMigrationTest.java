package org.thomcgn.backend.auth;

import org.junit.jupiter.api.Test;
import org.thomcgn.backend.support.MigratedPostgresTest;

import static org.assertj.core.api.Assertions.assertThat;

class LegacySeedMigrationTest extends MigratedPostgresTest {
    @Override
    protected String migrationTarget() { return "20"; }

    @Test
    void disablesOnlyUnchangedSeedPasswordsAndRevokesTheirRefreshTokens() throws Exception {
        try (var connection = databaseConnection(); var sql = connection.createStatement()) {
            sql.execute("""
                    insert into app_users (name, email, password_hash, role, active)
                    values ('Changed', 'changed@example.test', 'already-rotated-password-hash', 'ADMIN', true)
                    """);
            sql.execute("""
                    insert into refresh_tokens (token_id, user_id, expires_at)
                    select 'session-' || id, id, now() + interval '1 day' from app_users
                    """);
            assertThat(migration("latest").migrate().migrationsExecuted).isEqualTo(5);
            try (var rows = sql.executeQuery("select email, active, password_hash from app_users order by email")) {
                int count = 0;
                while (rows.next()) {
                    boolean changed = rows.getString("email").equals("changed@example.test");
                    assertThat(rows.getBoolean("active")).isEqualTo(changed);
                    assertThat(rows.getString("password_hash")).isEqualTo(changed
                            ? "already-rotated-password-hash" : "DISABLED_LEGACY_SEED");
                    count++;
                }
                assertThat(count).isEqualTo(3);
            }
            try (var rows = sql.executeQuery("select u.email from refresh_tokens r join app_users u on u.id = r.user_id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("changed@example.test");
                assertThat(rows.next()).isFalse();
            }
        }
    }
}
