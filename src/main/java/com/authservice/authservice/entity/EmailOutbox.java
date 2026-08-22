package com.authservice.authservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "email_outbox",
        indexes = {
                @Index(
                        name = "idx_email_outbox_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_email_outbox_next_attempt",
                        columnList = "nextAttemptAt"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailOutbox {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 320)
    private String recipient;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private EmailOutboxStatus status = EmailOutboxStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    private LocalDateTime nextAttemptAt;

    private LocalDateTime sentAt;

    private LocalDateTime lastAttemptAt;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
