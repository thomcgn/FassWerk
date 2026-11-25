package org.thomcgn.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.auth.domain.AppUser;
import org.thomcgn.backend.auth.domain.UserRole;
import org.thomcgn.backend.auth.repository.AppUserRepository;

/**
 * Optionaler Lokal-Login für die Desktop-Anwendung. Nur aktiv mit app.desktop.staff-login=true.
 * Der normale Admin-Bootstrap bleibt davon unberührt; die Migration, die die alten
 * Seed-Zugangsdaten deaktiviert, wird nicht rückgängig gemacht.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DesktopStaffBootstrap implements ApplicationRunner {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Environment environment;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!Boolean.parseBoolean(environment.getProperty("app.desktop.staff-login", "false"))) {
            return;
        }

        String email = environment.getProperty("app.desktop.staff-email", "staff@fasswerk.local");
        String password = environment.getProperty("app.desktop.staff-password", "");
        if (email.isBlank() || password.isBlank()) {
            return;
        }

        AppUser user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user != null
                && user.isActive()
                && user.getRole() == UserRole.ADMIN
                && encoder.matches(password, user.getPasswordHash())) {
            // Nichts ändern: bestehende Sitzungen bleiben gültig, keine neuen Tokens nötig.
            return;
        }

        boolean existing = user != null;
        if (!existing) {
            user = new AppUser();
            user.setEmail(email);
        }
        user.setName("staff");
        user.setPasswordHash(encoder.encode(password));
        user.setRole(UserRole.ADMIN);
        user.setActive(true);
        if (existing) {
            // Passwort-/Rechteänderung ungültig macht alte Sitzungen – aber nur dann.
            user.setAccessVersion(user.getAccessVersion() + 1);
        }
        users.save(user);

        log.info("Desktop-Staff-Zugang wurde {} (Rolle ADMIN): {}", existing ? "aktualisiert" : "angelegt", email);
    }
}
