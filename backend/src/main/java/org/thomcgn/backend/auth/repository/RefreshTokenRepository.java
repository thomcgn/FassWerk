package org.thomcgn.backend.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thomcgn.backend.auth.domain.AppUser;
import org.thomcgn.backend.auth.domain.RefreshToken;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenId(String tokenId);

    Optional<RefreshToken> findByTokenIdAndRevokedAtIsNull(String tokenId);

    List<RefreshToken> findByUserAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(AppUser user, OffsetDateTime now);

    Optional<RefreshToken> findByIdAndUserAndRevokedAtIsNull(Long id, AppUser user);

    boolean existsByUserIdAndFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(Long userId, String familyId, OffsetDateTime now);

    List<RefreshToken> findByUserAndFamilyId(AppUser user, String familyId);

    long deleteByExpiresAtBefore(OffsetDateTime now);
}

