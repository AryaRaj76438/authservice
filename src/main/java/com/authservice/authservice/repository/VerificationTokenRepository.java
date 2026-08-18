package com.authservice.authservice.repository;

import com.authservice.authservice.entity.VerificationToken;
import com.authservice.authservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerificationTokenRepository
        extends JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    Optional<VerificationToken> findByUser(User user);

    void deleteByUser(User user);
}