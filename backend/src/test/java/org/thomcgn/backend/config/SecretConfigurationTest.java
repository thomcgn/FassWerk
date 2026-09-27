package org.thomcgn.backend.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.thomcgn.backend.auth.JwtProperties;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecretConfigurationTest {
    private final String secret = UUID.randomUUID().toString() + UUID.randomUUID();
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> {
                context.getEnvironment().getPropertySources().remove("systemEnvironment");
                context.getEnvironment().getPropertySources().remove("systemProperties");
            })
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfiguration.class, StartupSecurityConfiguration.class)
            .withPropertyValues("DB_URL=jdbc:postgresql://localhost/example", "DB_USER=example",
                    "DB_PASSWORD=" + UUID.randomUUID(), "JWT_SECRET=" + secret);

    @Test
    void productionWithoutJwtSecretFailsAtStartup() {
        runner.withPropertyValues("spring.profiles.active=prod", "JWT_SECRET=")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("JWT_SECRET is required"));
    }

    @Test
    void noProfileIsAlsoSecureByDefault() {
        runner.withPropertyValues("JWT_SECRET=")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "change-me-super-long-jwt-secret-at-least-32-characters",
            "docker-dev-secret-change-me-please-at-least-32-chars",
            "dev-only-fasswerk-signing-key-never-use-in-production",
            "test-secret-for-jwt-signing-that-is-long-enough", "replace_me"})
    void productionRejectsWeakAndKnownKeysWithoutLeakingThem(String invalidSecret) {
        runner.withPropertyValues("spring.profiles.active=prod", "JWT_SECRET=" + invalidSecret)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("JWT_SECRET")
                            .hasMessageNotContaining(invalidSecret);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_URL", "DB_USER", "DB_PASSWORD"})
    void productionRequiresDatabaseConfiguration(String variable) {
        runner.withPropertyValues("spring.profiles.active=prod", variable + "=")
                .run(context -> assertThat(context).hasFailed().getFailure().hasMessageContaining(variable));
    }

    @Test
    void productionRejectsDevelopmentDatabasePassword() {
        runner.withPropertyValues("spring.profiles.active=prod", "DB_PASSWORD=fasswerk")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod,dev", "dev,prod", "prod,test"})
    void productionCannotLoadDevelopmentOrTestOverrides(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("prod cannot be combined"));
    }

    @Test
    void productionAcceptsExplicitPrivateConfiguration() {
        runner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JwtProperties.class).secret()).isEqualTo(secret);
            assertThat(context.getBean(JwtProperties.class).toString()).doesNotContain(secret);
        });
    }

    @Test
    void developmentConfigurationLoadsWithoutExternalSecrets() {
        new ApplicationContextRunner()
                .withInitializer(context -> {
                    context.getEnvironment().getPropertySources().remove("systemEnvironment");
                    context.getEnvironment().getPropertySources().remove("systemProperties");
                })
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(PropertiesConfiguration.class, StartupSecurityConfiguration.class)
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(JwtProperties.class).secret()).startsWith("dev-only-");
                    assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                            .isEqualTo("jdbc:postgresql://localhost:5432/fasswerk");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"JWT_ACCESS_TOKEN_MINUTES=0", "JWT_REFRESH_TOKEN_DAYS=-1", "JWT_ISSUER="})
    void invalidTokenSettingsFailAtBinding(String property) {
        runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void bootstrapRequiresBothCredentials() {
        runner.withPropertyValues("BOOTSTRAP_ADMIN_EMAIL=operator@example.test")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("Bootstrap requires"));
    }

    @Test
    void bootstrapAcceptsExplicitCredentials() {
        runner.withPropertyValues("BOOTSTRAP_ADMIN_EMAIL=operator@example.test",
                        "BOOTSTRAP_ADMIN_PASSWORD=" + UUID.randomUUID())
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class PropertiesConfiguration {
    }
}
