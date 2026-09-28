package org.thomcgn.backend.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.thomcgn.backend.auth.domain.AppUser;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where lower(u.email) = lower(:email)")
    Optional<AppUser> lockByEmail(@Param("email") String email);

    boolean existsByRoleAndActiveTrue(org.thomcgn.backend.auth.domain.UserRole role);

    Optional<AppUser> findByEmailIgnoreCaseAndActiveTrue(String email);
}

