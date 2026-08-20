package com.authservice.authservice.service;

import com.authservice.authservice.entity.PasswordResetToken;
import com.authservice.authservice.entity.User;
import com.authservice.authservice.exception.BadRequestException;
import com.authservice.authservice.repository.PasswordResetTokenRepository;
import com.authservice.authservice.repository.RefreshTokenRepository;
import com.authservice.authservice.repository.UserRepository;
import com.authservice.authservice.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenUtil tokenUtil;
    private final EmailService emailService;

    @Value("${app.password-reset.token-expiration-minutes:30}")
    private long expirationMinutes;

    @Transactional
    public void forgotPassword(String email) {
        String normalizedEmail = email.trim().toLowerCase();

        // Don't reveal whether the email exists.
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (user == null) {
            return;
        }

        // Delete any previous reset token.
        passwordResetTokenRepository.findByUserId(user.getId())
                .ifPresent(passwordResetTokenRepository::delete);

        // Generate a cryptographically secure random token.
        String rawToken = tokenUtil.generateToken();
        String tokenHash = tokenUtil.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .tokenHash(tokenHash)
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(expirationMinutes))
                .used(false)
                .build();

        passwordResetTokenRepository.save(resetToken);

        // Send the raw token only through email.
        emailService.sendPasswordResetEmail(user, rawToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Invalid password reset token");
        }

        String tokenHash = tokenUtil.hashToken(rawToken);

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() ->
                        new BadRequestException(
                                "Invalid password reset token"
                        )
                );

        // Token can only be used once.
        if (resetToken.isUsed()) {
            throw new BadRequestException(
                    "Password reset token has already been used"
            );
        }

        // Check expiration.
        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            passwordResetTokenRepository.delete(resetToken);

            throw new BadRequestException(
                    "Password reset token has expired"
            );
        }

        User user = resetToken.getUser();

        // Update password.
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // Mark token as consumed.
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Revoke all existing refresh tokens.
        refreshTokenRepository.revokeAllByUserId(user.getId());
    }
}