package org.thomcgn.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.thomcgn.backend.auth.domain.AppUser;
import org.thomcgn.backend.auth.domain.UserRole;
import org.thomcgn.backend.auth.repository.AppUserRepository;
import org.thomcgn.backend.auth.service.AdminBootstrap;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AdminBootstrapTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String password = UUID.randomUUID().toString();
    private final MockEnvironment environment = new MockEnvironment()
            .withProperty("app.bootstrap.admin-email", "operator@example.test")
            .withProperty("app.bootstrap.admin-password", password);

    @Test
    void createsFirstAdminWithHashInsteadOfPlaintext() {
        when(users.findByEmailIgnoreCase("operator@example.test")).thenReturn(Optional.empty());
        new AdminBootstrap(users, encoder, environment).run(null);
        var captured = org.mockito.ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(captured.capture());
        AppUser admin = captured.getValue();
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getPasswordHash()).isNotEqualTo(password);
        assertThat(encoder.matches(password, admin.getPasswordHash())).isTrue();
    }

    @Test
    void isOptIn() {
        new AdminBootstrap(users, encoder, new MockEnvironment()).run(null);
        verifyNoInteractions(users);
    }

    @Test
    void restartDoesNotResetExistingAdminPassword() {
        AppUser admin = new AppUser();
        admin.setActive(true);
        admin.setRole(UserRole.ADMIN);
        admin.setPasswordHash("existing-hash");
        when(users.findByEmailIgnoreCase("operator@example.test")).thenReturn(Optional.of(admin));
        new AdminBootstrap(users, encoder, environment).run(null);
        assertThat(admin.getPasswordHash()).isEqualTo("existing-hash");
        verify(users, never()).save(any());
    }

    @Test
    void cannotReactivateDisabledSeedOrPromoteStaff() {
        AppUser disabled = new AppUser();
        disabled.setRole(UserRole.ADMIN);
        when(users.findByEmailIgnoreCase("operator@example.test")).thenReturn(Optional.of(disabled));
        assertThatThrownBy(() -> new AdminBootstrap(users, encoder, environment).run(null))
                .isInstanceOf(IllegalStateException.class);
        disabled.setActive(true);
        disabled.setRole(UserRole.STAFF);
        assertThatThrownBy(() -> new AdminBootstrap(users, encoder, environment).run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(users, never()).save(any());
    }

    @Test
    void cannotAddAdminWhenAnotherAdminAlreadyExists() {
        when(users.findByEmailIgnoreCase("operator@example.test")).thenReturn(Optional.empty());
        when(users.existsByRoleAndActiveTrue(UserRole.ADMIN)).thenReturn(true);
        assertThatThrownBy(() -> new AdminBootstrap(users, encoder, environment).run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(users, never()).save(any());
    }
}
