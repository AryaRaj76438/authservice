package com.authservice.authservice.repository;

import com.authservice.authservice.entity.LoginAttempt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoginAttemptRepository
        extends JpaRepository<LoginAttempt, Long> {

    Optional<LoginAttempt> findByEmail(
            String email
    );
}