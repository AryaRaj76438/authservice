package com.authservice.authservice.service;

import com.authservice.authservice.entity.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.verification-url}")
    private String verificationUrl;

    @Value("${app.password-reset.frontend-url}")
    private String frontendUrl;

    @Value("${app.password-reset.token-expiration-minutes:30}")
    private long passwordResetExpirationMinutes;

    public void sendVerificationEmail(User user, String token) {
        String verificationLink = verificationUrl + "?token=" + token;

        String subject = "Verify your AuthService account";

        String html = """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif;">

                    <h2>Welcome to AuthService</h2>

                    <p>Hello %s,</p>

                    <p>
                        Thank you for creating an account.
                        Please verify your email address by
                        clicking the button below.
                    </p>

                    <p>
                        <a href="%s"
                           style="
                           display:inline-block;
                           padding:12px 20px;
                           background:#2563eb;
                           color:white;
                           text-decoration:none;
                           border-radius:6px;">
                           Verify Email
                        </a>
                    </p>

                    <p>
                        This verification link will expire in %d hours.
                    </p>

                    <p>
                        If you did not create this account,
                        you can safely ignore this email.
                    </p>

                </body>
                </html>
                """.formatted(
                user.getName(),
                verificationLink,
                24
        );

        sendHtmlEmail(user.getEmail(), subject, html);
    }

    public void sendPasswordResetEmail(User user, String rawToken) {
        String resetUrl = frontendUrl + "/reset-password?token=" + rawToken;

        String subject = "Reset your password";

        String body = """
                Hello %s,

                We received a request to reset your password.

                Click the link below to reset your password:
                %s

                This link will expire in %d minutes.

                If you did not request a password reset,
                you can safely ignore this email.

                Regards,
                AuthService
                """.formatted(
                user.getName(),
                resetUrl,
                passwordResetExpirationMinutes
        );

        sendEmail(user.getEmail(), subject, body);
    }

    private void sendEmail(String to, String subject, String body) {
        MimeMessage message = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    false,
                    "UTF-8"
            );

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);

            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException(
                    "Unable to send email",
                    e
            );
        }
    }

    private void sendHtmlEmail(String to, String subject, String html) {
        MimeMessage message = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    true,
                    "UTF-8"
            );

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException(
                    "Unable to send email",
                    e
            );
        }
    }
}