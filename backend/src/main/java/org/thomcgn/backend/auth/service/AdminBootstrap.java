package org.thomcgn.backend.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.auth.domain.AppUser;
import org.thomcgn.backend.auth.domain.UserRole;
import org.thomcgn.backend.auth.repository.AppUserRepository;

@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Environment environment;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        String email = environment.getProperty("app.bootstrap.admin-email", "").trim();
        if (email.isEmpty()) {
            return;
        }
        var existing = users.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            if (existing.get().isActive() && existing.get().getRole() == UserRole.ADMIN) {
                return;
            }
            throw new IllegalStateException("Bootstrap cannot overwrite an existing non-admin or disabled account");
        }
        if (users.existsByRoleAndActiveTrue(UserRole.ADMIN)) {
            throw new IllegalStateException("Bootstrap is only allowed when no active administrator exists");
        }
        AppUser admin = new AppUser();
        admin.setName("Administrator");
        admin.setEmail(email);
        admin.setPasswordHash(encoder.encode(environment.getRequiredProperty("app.bootstrap.admin-password")));
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        users.save(admin);
    }
}
