package com.authservice.authservice.service;

import com.authservice.authservice.dto.request.LoginRequest;
import com.authservice.authservice.dto.request.RefreshTokenRequest;
import com.authservice.authservice.dto.request.SignupRequest;
import com.authservice.authservice.dto.response.AuthResponse;
import com.authservice.authservice.dto.response.UserResponse;
import com.authservice.authservice.entity.LoginAttempt;
import com.authservice.authservice.entity.RefreshToken;
import com.authservice.authservice.entity.User;
import com.authservice.authservice.entity.VerificationToken;
import com.authservice.authservice.enums.Role;
import com.authservice.authservice.exception.BadRequestException;
import com.authservice.authservice.exception.TooManyRequestsException;
import com.authservice.authservice.exception.UnauthorizedException;
import com.authservice.authservice.repository.LoginAttemptRepository;
import com.authservice.authservice.repository.RefreshTokenRepository;
import com.authservice.authservice.repository.UserRepository;
import com.authservice.authservice.repository.VerificationTokenRepository;
import com.authservice.authservice.security.JwtService;
import com.authservice.authservice.security.UserPrincipal;
import com.authservice.authservice.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;


    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenUtil tokenUtil;
    private final EmailService emailService;

    @Value("${app.verification-token-expiration-hours:24}")
    private long verificationTokenExpirationHours;

    @Value("${app.verification-resend-cooldown-seconds:60}")
    private long verificationResendCooldownSeconds;

    @Value("${app.login.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.login.lock-duration-minutes:15}")
    private long lockDurationMinutes;

    @Value("${app.login.attempt-window-minutes:15}")
    private long attemptWindowMinutes;

    // =====================================================
    // SIGNUP
    // =====================================================

    @Transactional
    public void signup(SignupRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException(
                    "An account with this email already exists"
            );
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .emailVerified(false)
                .enabled(true)
                .build();

        userRepository.save(user);

        createAndSendVerificationToken(user);
    }

    // =====================================================
    // VERIFY EMAIL
    // =====================================================

    @Transactional
    public void verifyEmail(String rawToken) {
        String tokenHash = tokenUtil.hashToken(rawToken);

        VerificationToken verificationToken = verificationTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() ->
                        new BadRequestException("Invalid verification link")
                );

        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            verificationTokenRepository.delete(verificationToken);

            throw new BadRequestException(
                    "Verification link has expired"
            );
        }

        User user = verificationToken.getUser();

        user.setEmailVerified(true);
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);

        // Verification token is single-use.
        verificationTokenRepository.delete(verificationToken);
    }

    // =====================================================
    // RESEND VERIFICATION
    // =====================================================

    @Transactional
    public void resendVerificationEmail(String email) {
        email = normalizeEmail(email);

        /*
         * Do not reveal whether an account exists.
         * This prevents email/account enumeration.
         */
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null || user.isEmailVerified()) {
            return;
        }

        VerificationToken verificationToken =
                verificationTokenRepository.findByUser(user).orElse(null);

        if (verificationToken == null) {
            createAndSendVerificationToken(user);
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        long secondsSinceLastEmail = Duration.between(
                verificationToken.getLastSentAt(),
                now
        ).getSeconds();

        if (secondsSinceLastEmail < verificationResendCooldownSeconds) {
            long retryAfter =
                    verificationResendCooldownSeconds - secondsSinceLastEmail;

            throw new TooManyRequestsException(
                    "Please wait before requesting another verification email",
                    retryAfter
            );
        }

        String rawToken = tokenUtil.generateToken();
        String tokenHash = tokenUtil.hashToken(rawToken);

        verificationToken.setTokenHash(tokenHash);
        verificationToken.setExpiresAt(
                now.plusHours(verificationTokenExpirationHours)
        );
        verificationToken.setLastSentAt(now);

        verificationTokenRepository.save(verificationToken);

        emailService.sendVerificationEmail(user, rawToken);
    }

    // =====================================================
    // LOGIN
    // =====================================================

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());

        LoginAttempt loginAttempt = loginAttemptRepository.findByEmail(email).orElse(null);

        if (loginAttempt != null &&
                loginAttempt.getLockedUntil() != null &&
                loginAttempt.getLockedUntil()
                        .isAfter(LocalDateTime.now())) {

            throw new UnauthorizedException(
                    "Too many failed login attempts. Please try again later."
            );
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UnauthorizedException(
                                "Invalid email or password"
                        )
                );

        if (!user.isEmailVerified()) {
            throw new UnauthorizedException(
                    "Please verify your email before logging in"
            );
        }

        Authentication authentication;

        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            email,
                            request.getPassword()
                    )
            );
        } catch (AuthenticationException exception) {
            throw new UnauthorizedException(
                    "Invalid email or password"
            );
        }

        resetLoginAttempts(email);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(UserResponse.from(user))
                .build();
    }

    // =====================================================
    // REFRESH TOKEN
    // =====================================================

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String rawToken = request.getRefreshToken();
        String tokenHash = tokenUtil.hashToken(rawToken);

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() ->
                        new UnauthorizedException(
                                "Invalid refresh token"
                        )
                );

        if (refreshToken.isRevoked()) {
            throw new UnauthorizedException(
                    "Refresh token has been revoked"
            );
        }

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);

            throw new UnauthorizedException(
                    "Refresh token has expired"
            );
        }

        User user = refreshToken.getUser();

        if (!user.isEmailVerified()) {
            throw new UnauthorizedException(
                    "Email is not verified"
            );
        }

        // Rotate refresh token.
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        UserPrincipal principal = new UserPrincipal(user);

        String accessToken =
                jwtService.generateAccessToken(principal);

        String newRefreshToken =
                createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .user(UserResponse.from(user))
                .build();
    }

    // =====================================================
    // CREATE VERIFICATION TOKEN
    // =====================================================

    private void createAndSendVerificationToken(User user) {
        LocalDateTime now = LocalDateTime.now();

        String rawToken = tokenUtil.generateToken();
        String tokenHash = tokenUtil.hashToken(rawToken);

        VerificationToken verificationToken = VerificationToken.builder()
                .tokenHash(tokenHash)
                .user(user)
                .expiresAt(
                        now.plusHours(verificationTokenExpirationHours)
                )
                .lastSentAt(now)
                .build();

        verificationTokenRepository.save(verificationToken);

        emailService.sendVerificationEmail(user, rawToken);
    }

    // =====================================================
    // CREATE REFRESH TOKEN
    // =====================================================

    private String createRefreshToken(User user) {
        String rawToken = tokenUtil.generateToken();
        String tokenHash = tokenUtil.hashToken(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(tokenHash)
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }


    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private void recordFailedLogin(String email) {
        LocalDateTime now = LocalDateTime.now();

        LoginAttempt attempt = loginAttemptRepository
                        .findByEmail(email)
                        .orElseGet(() ->
                                LoginAttempt.builder()
                                        .email(email)
                                        .failedAttempts(0)
                                        .firstFailedAt(now)
                                        .build()
                        );

        if (attempt.getFirstFailedAt()
                .plusMinutes(attemptWindowMinutes)
                .isBefore(now)) {

            attempt.setFailedAttempts(0);
            attempt.setFirstFailedAt(now);
        }


        int failures = attempt.getFailedAttempts() + 1;
        attempt.setFailedAttempts(failures);

        if (failures >= maxFailedAttempts) {
            attempt.setLockedUntil(now.plusMinutes(lockDurationMinutes));
        }
        loginAttemptRepository.save(attempt);
    }

    private void resetLoginAttempts(String email) {
        loginAttemptRepository
                .findByEmail(email)
                .ifPresent(
                        loginAttemptRepository::delete
                );
    }


}