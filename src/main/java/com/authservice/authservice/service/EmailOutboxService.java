package com.authservice.authservice.service;

import com.authservice.authservice.entity.EmailOutbox;
import com.authservice.authservice.entity.EmailOutboxStatus;
import com.authservice.authservice.repository.EmailOutboxRepository;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailOutboxService {
    private final EmailOutboxRepository emailOutboxRepository;

    public void queueEmail(String recipient, String subject, String body){
        EmailOutbox email = EmailOutbox.builder()
                .recipient(recipient)
                .subject(subject)
                .body(body)
                .status(EmailOutboxStatus.PENDING)
                .build();

        emailOutboxRepository.save(email);
    }
}
