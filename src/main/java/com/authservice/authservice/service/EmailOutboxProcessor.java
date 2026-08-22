package com.authservice.authservice.service;

import com.authservice.authservice.entity.EmailOutbox;
import com.authservice.authservice.entity.EmailOutboxStatus;
import com.authservice.authservice.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailOutboxProcessor {
    private final EmailOutboxRepository emailOutboxRepository;
    private final JavaMailSender mailSender;

    private static final int MAX_ATTEMPTS = 5;

    @Scheduled(
            fixedDelayString = "${app.email-outbox.poll-interval-ms:5000}"
    )
    public void processOutbox(){
        List<EmailOutbox> emails = emailOutboxRepository
                .findPendingEmails(EmailOutboxStatus.PENDING, LocalDateTime.now());
        for(EmailOutbox email: emails){
            processEmail(email);
        }
    }

    private void processEmail(EmailOutbox email) {
        try {
            email.setStatus(EmailOutboxStatus.PROCESSING);
            email.setLastAttemptAt(LocalDateTime.now());
            email.setAttemptCount(email.getAttemptCount()+1);

            emailOutboxRepository.save(email);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email.getRecipient());
            message.setSubject(email.getSubject());
            message.setText(email.getBody());

            mailSender.send(message);

            email.setStatus(EmailOutboxStatus.SENT);
            email.setSentAt(LocalDateTime.now());
            email.setLastError(null);

            emailOutboxRepository.save(email);

            log.info(
                    "Email sent successfully. outboxId={}, recipient={}",
                    email.getId(),
                    email.getRecipient()
            );
        }catch (Exception e){
            handleFailure(email, e);
        }
    }

    private void handleFailure(EmailOutbox email, Exception exception) {
        log.error(
                "Failed to send email. outboxId={}, attempt={}",
                email.getId(),
                email.getAttemptCount(),
                exception
        );
        email.setLastError(exception.getMessage());
        if(email.getAttemptCount()>=MAX_ATTEMPTS){
            email.setStatus(EmailOutboxStatus.FAILED);
        }else{
            email.setStatus(EmailOutboxStatus.PENDING);

            long delaySeconds = 10L * (1L << Math.min(email.getAttemptCount() - 1, 6));
            email.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));
        }
        emailOutboxRepository.save(email);
    }
}
