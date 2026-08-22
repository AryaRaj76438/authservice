package com.authservice.authservice.service;

import com.authservice.authservice.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailOutboxService emailOutboxService;

    @Value("${app.verification-url}")
    private String verificationFrontendUrl;

    @Value("${app.verification.token-expiration-minutes:15}")
    private long verificationExpirationMinutes;

    @Value("${app.password-reset.frontend-url}")
    private String passwordResetFrontendUrl;

    @Value("${app.password-reset.token-expiration-minutes:30}")
    private long passwordResetExpirationMinutes;

    public void sendVerificationEmail(User user, String rawToken) {
        String verificationUrl =
                verificationFrontendUrl + "?token=" + rawToken;

        String subject = "Verify your AuthService account";

        String body = """
                ============================================================
                AUTH SERVICE
                ============================================================

                Hello %s,

                Thank you for creating an account with AuthService.

                Please verify your email address using the link below:

                %s

                This verification link will expire in %d minutes.

                If you did not create this account, you can safely ignore
                this email.

                ------------------------------------------------------------

                Regards,
                AuthService

                This is an automated email. Please do not reply.
                ============================================================
                """.formatted(
                user.getName(),
                verificationUrl,
                verificationExpirationMinutes
        );

        emailOutboxService.queueEmail(
                user.getEmail(),
                subject,
                body
        );
    }

    public void sendPasswordResetEmail(User user, String rawToken) {
        String resetUrl =
                passwordResetFrontendUrl + "?token=" + rawToken;

        String subject = "Reset your password";

        String body = """
                ============================================================
                AUTH SERVICE
                ============================================================

                Hello %s,

                We received a request to reset your AuthService password.

                Use the link below to reset your password:

                %s

                This password reset link will expire in %d minutes.

                If you did not request a password reset, you can safely
                ignore this email. Your password will remain unchanged.

                ------------------------------------------------------------

                Regards,
                AuthService

                This is an automated email. Please do not reply.
                ============================================================
                """.formatted(
                user.getName(),
                resetUrl,
                passwordResetExpirationMinutes
        );

        emailOutboxService.queueEmail(
                user.getEmail(),
                subject,
                body
        );
    }
}