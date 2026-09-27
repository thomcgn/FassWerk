package org.thomcgn.backend.support;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

/** Isolates SQL fixtures from Spring contexts and from every other test method. */
public abstract class MigratedPostgresTest extends PostgresIntegrationTest {
    private String database;
    protected Flyway flyway;

    protected String migrationTarget() { return "latest"; }

    @BeforeEach
    void createMigratedDatabase() throws SQLException {
        database = "test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = connection(); var sql = connection.createStatement()) {
            sql.execute("create database " + database);
        }
        flyway = migration(migrationTarget());
        flyway.migrate();
    }

    protected Flyway migration(String target) {
        return Flyway.configure().dataSource(databaseUrl(), username(), password())
                .locations("classpath:db/migration").target(target).load();
    }

    protected Connection databaseConnection() throws SQLException {
        return DriverManager.getConnection(databaseUrl(), username(), password());
    }

    private String databaseUrl() {
        return jdbcUrl().replace("/fasswerk_test", "/" + database);
    }

    @AfterEach
    void dropDatabase() throws SQLException {
        if (database != null) {
            try (var connection = connection(); var sql = connection.createStatement()) {
                sql.execute("drop database " + database + " with (force)");
            }
        }
    }
}
