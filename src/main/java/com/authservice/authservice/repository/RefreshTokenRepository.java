package com.authservice.authservice.repository;

import com.authservice.authservice.entity.RefreshToken;

import org.springframework.data.jpa.repository.*;

import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {


    Optional<RefreshToken> findByTokenHash(
            String tokenHash
    );


    @Modifying
    @Query("""
        UPDATE RefreshToken r
        SET r.revoked = true,
            r.revokedAt = CURRENT_TIMESTAMP
        WHERE r.user.id = :userId
          AND r.revoked = false
    """)
    void revokeAllByUserId(
            @Param("userId") Long userId
    );
}