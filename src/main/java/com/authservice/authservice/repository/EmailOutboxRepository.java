package com.authservice.authservice.repository;

import com.authservice.authservice.entity.EmailOutbox;
import com.authservice.authservice.entity.EmailOutboxStatus;
import lombok.extern.java.Log;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {
    @Query("""
        SELECT e
        FROM EmailOutbox e
        WHERE e.status = :status
          AND (
              e.nextAttemptAt IS NULL
              OR e.nextAttemptAt <= :now
          )
        ORDER BY e.createdAt ASC
    """)
    List<EmailOutbox> findPendingEmails(
            @Param("status")
            EmailOutboxStatus status,

            @Param("now")
            LocalDateTime now
    );
}
