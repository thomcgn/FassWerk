package org.thomcgn.backend.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.thomcgn.backend.common.domain.BaseEntity;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "revoked_access_tokens")
public class RevokedAccessToken extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String tokenId;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    @Column(nullable = false)
    private OffsetDateTime revokedAt;
}

