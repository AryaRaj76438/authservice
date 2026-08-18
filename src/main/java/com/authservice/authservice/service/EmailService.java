package com.authservice.authservice.service;

import com.authservice.authservice.entity.User;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;

import org.springframework.mail.javamail.MimeMessageHelper;

import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.verification-url}")
    private String verificationUrl;

    public void sendVerificationEmail(
            User user,
            String token
    ) {

        String verificationLink =
                verificationUrl
                        + "?token="
                        + token;

        MimeMessage message =
                mailSender.createMimeMessage();

        try {

            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            message,
                            true,
                            "UTF-8"
                    );

            helper.setTo(user.getEmail());

            helper.setSubject(
                    "Verify your AuthService account"
            );

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
                        This verification link will expire
                        in 24 hours.
                    </p>

                    <p>
                        If you did not create this account,
                        you can safely ignore this email.
                    </p>

                </body>
                </html>
                """.formatted(
                    user.getName(),
                    verificationLink
            );

            helper.setText(html, true);

            mailSender.send(message);

        } catch (MessagingException e) {

            throw new RuntimeException(
                    "Unable to send verification email",
                    e
            );
        }
    }
}