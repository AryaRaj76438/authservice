package com.authservice.authservice.repository;

import com.authservice.authservice.entity.RefreshToken;
import com.authservice.authservice.entity.User;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUser(User user);
    @Modifying
    @Query("""
    UPDATE RefreshToken r
    SET r.revoked = true
    WHERE r.user.id = :userId
""")
    void revokeAllByUserId(
            @Param("userId") Long userId
    );
}