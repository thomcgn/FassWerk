package org.thomcgn.backend.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thomcgn.backend.auth.domain.RevokedAccessToken;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface RevokedAccessTokenRepository extends JpaRepository<RevokedAccessToken, Long> {

    boolean existsByTokenIdAndExpiresAtAfter(String tokenId, OffsetDateTime now);

    Optional<RevokedAccessToken> findByTokenId(String tokenId);

    long deleteByExpiresAtBefore(OffsetDateTime now);
}

