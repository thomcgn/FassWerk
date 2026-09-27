package org.thomcgn.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class LegacySeedMigrationTest {
    @Test
    void disablesOnlyUnchangedSeedPasswordsAndRevokesTheirRefreshTokens() throws Exception {
        // A focused data-transition test, not a replacement for PostgreSQL/Flyway testing.
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("create table app_users (id bigint primary key, password_hash varchar(255), active boolean, updated_at timestamp)");
            statement.execute("create table refresh_tokens (id bigint primary key, user_id bigint references app_users(id))");
            String original = new ClassPathResource("db/migration/V2__auth_and_tokens_consolidated.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
            var hashes = Pattern.compile("'(\\$2b\\$[^']+)'").matcher(original).results()
                    .map(match -> match.group(1)).toList();
            assertThat(hashes).hasSize(2);
            try (var insert = connection.prepareStatement("insert into app_users values (?, ?, true, current_timestamp)")) {
                for (int i = 1; i <= 3; i++) {
                    insert.setLong(1, i);
                    insert.setString(2, i < 3 ? hashes.get(i - 1) : "already-rotated-password-hash");
                    insert.executeUpdate();
                    statement.execute("insert into refresh_tokens values (" + i + ", " + i + ")");
                }
            }
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V21__disable_known_seed_credentials.sql"));
            try (var rows = statement.executeQuery("select id, active, password_hash from app_users order by id")) {
                for (int i = 1; i <= 3; i++) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBoolean("active")).isEqualTo(i == 3);
                    assertThat(rows.getString("password_hash")).isEqualTo(i < 3 ? "DISABLED_LEGACY_SEED" : "already-rotated-password-hash");
                }
            }
            try (var rows = statement.executeQuery("select user_id from refresh_tokens")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(3);
                assertThat(rows.next()).isFalse();
            }
        }
    }
}
