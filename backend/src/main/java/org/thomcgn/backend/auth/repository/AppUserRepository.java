package org.thomcgn.backend.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thomcgn.backend.auth.domain.AppUser;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByRoleAndActiveTrue(org.thomcgn.backend.auth.domain.UserRole role);

    Optional<AppUser> findByEmailIgnoreCaseAndActiveTrue(String email);
}

