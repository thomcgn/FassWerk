package org.thomcgn.backend.auth;

import org.junit.jupiter.api.Test;
import org.thomcgn.backend.support.MigratedPostgresTest;

import static org.assertj.core.api.Assertions.assertThat;

class AuditSessionMigrationTest extends MigratedPostgresTest {
    @Override
    protected String migrationTarget() { return "25"; }

    @Test
    void upgradeRevokesLegacySessionsOnceAndPreservesAccountsAndPriorRevocations() throws Exception {
        try (var connection = databaseConnection(); var sql = connection.createStatement()) {
            sql.execute("""
                    insert into app_users(id,name,email,password_hash,role,active)
                    values(999,'Upgrade user','upgrade@example.test','unchanged-password-hash','ADMIN',true)
                    """);
            sql.execute("""
                    insert into refresh_tokens(token_id,user_id,expires_at,revoked_at,revoked_reason) values
                    ('legacy-active',999,'2070-01-01',null,null),
                    ('legacy-expired',999,'2000-01-01',null,null),
                    ('legacy-revoked',999,'2070-01-01','2020-01-01T00:00:00Z','MANUAL')
                    """);
            assertThat(migration("26").migrate().migrationsExecuted).isEqualTo(1);
            try (var rows = sql.executeQuery("select count(*) from refresh_tokens where user_id=999 and revoked_at is not null and revoked_reason='SECURITY_UPGRADE'")) {
                rows.next(); assertThat(rows.getInt(1)).isEqualTo(2);
            }
            try (var rows = sql.executeQuery("select revoked_at,revoked_reason from refresh_tokens where token_id='legacy-revoked'")) {
                rows.next();
                assertThat(rows.getObject(1, java.time.OffsetDateTime.class).toInstant())
                        .isEqualTo(java.time.Instant.parse("2020-01-01T00:00:00Z"));
                assertThat(rows.getString(2)).isEqualTo("MANUAL");
            }
            try (var rows = sql.executeQuery("select name,email,password_hash,role,active from app_users where id=999")) {
                rows.next();
                assertThat(rows.getString(1)).isEqualTo("Upgrade user");
                assertThat(rows.getString(2)).isEqualTo("upgrade@example.test");
                assertThat(rows.getString(3)).isEqualTo("unchanged-password-hash");
                assertThat(rows.getString(4)).isEqualTo("ADMIN");
                assertThat(rows.getBoolean(5)).isTrue();
            }
            sql.execute("""
                    insert into refresh_tokens(token_id,user_id,expires_at,family_id,browser_session)
                    values('new-session',999,'2070-01-01','new-family',true)
                    """);
            // A later application start must not log out newly created sessions.
            assertThat(migration("26").migrate().migrationsExecuted).isZero();
            try (var rows = sql.executeQuery("select revoked_at from refresh_tokens where token_id='new-session'")) {
                rows.next(); assertThat(rows.getObject(1)).isNull();
            }
            assertThat(migration("26").validateWithResult().validationSuccessful).isTrue();
        }
    }
}
