package org.thomcgn.backend.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class StartupSecurityConfiguration {
    @Bean
    static BeanFactoryPostProcessor validateStartupSecrets(Environment environment) {
        // Validate before datasource/Flyway initialization; errors never include secret values.
        return beanFactory -> validate(environment);
    }

    private static void validate(Environment env) {
        Set<String> profiles = Set.copyOf(Arrays.asList(env.getActiveProfiles()));
        boolean dev = profiles.contains("dev");
        boolean test = profiles.contains("test");
        if (profiles.contains("prod") && (dev || test)) {
            throw new IllegalStateException("prod cannot be combined with dev or test");
        }
        String secret = required(env, "app.jwt.secret", "JWT_SECRET");
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32 || placeholder(secret)
                || (!(dev || test) && (secret.startsWith("dev-only-") || secret.startsWith("test-secret-")))) {
            throw new IllegalStateException("JWT_SECRET must be a non-placeholder signing key of at least 32 UTF-8 bytes");
        }
        String url = required(env, "spring.datasource.url", "DB_URL");
        required(env, "spring.datasource.username", "DB_USER");
        if (!(test && url.startsWith("jdbc:h2:mem:"))) {
            String password = required(env, "spring.datasource.password", "DB_PASSWORD");
            if (placeholder(password) || (!dev && password.equals("fasswerk"))) {
                throw new IllegalStateException("DB_PASSWORD must not be a placeholder or a development default");
            }
        }
        String email = env.getProperty("app.bootstrap.admin-email", "");
        String password = env.getProperty("app.bootstrap.admin-password", "");
        if (!email.isBlank() || !password.isBlank()) {
            if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                    || password.isBlank() || password.length() < 16 || password.getBytes(StandardCharsets.UTF_8).length > 72
                    || placeholder(password)) {
                throw new IllegalStateException("Bootstrap requires a valid BOOTSTRAP_ADMIN_EMAIL and a non-placeholder password (16 characters minimum, 72 UTF-8 bytes maximum)");
            }
        }
    }

    private static String required(Environment env, String property, String variable) {
        String value = env.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variable + " is required");
        }
        return value;
    }

    private static boolean placeholder(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("change-me") || lower.contains("changeme") || lower.contains("change_me")
                || lower.contains("replace_me") || lower.contains("replace-me") || lower.equals("dummy")
                || lower.equals("password") || lower.equals("staffpass123!");
    }
}
