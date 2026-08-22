package com.authservice.authservice.service;

import com.authservice.authservice.dto.request.LoginRequest;
import com.authservice.authservice.dto.request.RefreshTokenRequest;
import com.authservice.authservice.dto.request.SignupRequest;
import com.authservice.authservice.dto.response.AuthResponse;
import com.authservice.authservice.dto.response.AuthenticationResult;
import com.authservice.authservice.dto.response.UserResponse;
import com.authservice.authservice.entity.RefreshToken;
import com.authservice.authservice.entity.User;
import com.authservice.authservice.entity.VerificationToken;
import com.authservice.authservice.enums.Role;
import com.authservice.authservice.exception.BadRequestException;
import com.authservice.authservice.exception.RefreshTokenReuseException;
import com.authservice.authservice.exception.TooManyRequestsException;
import com.authservice.authservice.exception.UnauthorizedException;
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

import com.authservice.authservice.util.RedisKeys;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final RedisService redisService;

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;

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

    @Value("${app.refresh-token.expiration-days:7}")
    private long refreshTokenExpirationDays;

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

        User user = userRepository.findByEmail(email).orElse(null);

        // Don't reveal whether the account exists.
        if (user == null || user.isEmailVerified()) {
            return;
        }

        String cooldownKey = RedisKeys.verificationCooldown(email);

        // Check Redis cooldown.
        if (redisService.exists(cooldownKey)) {
            long retryAfter = redisService.getTtl(cooldownKey);

            throw new TooManyRequestsException(
                    "Please wait before requesting another verification email",
                    retryAfter
            );
        }

        VerificationToken verificationToken = verificationTokenRepository
                .findByUser(user)
                .orElse(null);

        String rawToken = tokenUtil.generateToken();
        String tokenHash = tokenUtil.hashToken(rawToken);
        LocalDateTime now = LocalDateTime.now();

        if (verificationToken == null) {
            verificationToken = VerificationToken.builder()
                    .tokenHash(tokenHash)
                    .user(user)
                    .expiresAt(now.plusHours(verificationTokenExpirationHours))
                    .lastSentAt(now)
                    .build();
        } else {
            verificationToken.setTokenHash(tokenHash);
            verificationToken.setExpiresAt(
                    now.plusHours(verificationTokenExpirationHours)
            );
            verificationToken.setLastSentAt(now);
        }

        verificationTokenRepository.save(verificationToken);

        emailService.sendVerificationEmail(user, rawToken);

        redisService.set(
                cooldownKey,
                "1",
                Duration.ofSeconds(verificationResendCooldownSeconds)
        );
    }

    // =====================================================
    // LOGIN
    // =====================================================

    @Transactional
    public AuthenticationResult login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());

        String lockKey = RedisKeys.loginLock(email);
        if(redisService.exists(lockKey)){
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

        return AuthenticationResult.builder()
                .authResponse(
                        AuthResponse.builder()
                                .accessToken(accessToken)
                                .tokenType("Bearer")
                                .user(
                                        UserResponse.from(user)
                                )
                                .build()
                )
                .refreshToken(refreshToken)
                .build();
    }

    // =====================================================
    // REFRESH TOKEN
    // =====================================================

    @Transactional
    public AuthenticationResult refreshToken(RefreshTokenRequest request) {
        String rawToken = request.getRefreshToken();

        if(rawToken==null || rawToken.isBlank()){
            throw new UnauthorizedException("Refresh Token is Required");
        }
        String tokenHash = tokenUtil.hashToken(rawToken);

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() ->
                        new UnauthorizedException(
                                "Invalid refresh token"
                        )
                );

        User user = refreshToken.getUser();

        /*
         * TOKEN REUSE DETECTION
         *
         * A refresh token is rotated after every use.
         *
         * Therefore, a revoked token being presented again
         * is suspicious.
         */

        if (refreshToken.isRevoked()) {
            refreshTokenRepository.revokeAllByUserId(user.getId());
            throw new UnauthorizedException(
                    "Refresh token has been revoked"
            );
        }

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);

            throw new RefreshTokenReuseException(
                    "Refresh token reuse detected. All sessions have been revoked."
            );
        }

        if(refreshToken.getExpiresAt().isBefore(LocalDateTime.now())){
            refreshToken.setRevoked(true);
            refreshToken.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(refreshToken);
            throw new UnauthorizedException("Refresh Token has expired");
        }

        if (!user.isEmailVerified()) {
            throw new UnauthorizedException(
                    "Email is not verified"
            );
        }

        // Rotate refresh token.

        String newRawToken = tokenUtil.generateToken();
        String newTokenHash = tokenUtil.hashToken(newRawToken);
        RefreshToken newRefreshToken =
                RefreshToken.builder()
                        .tokenHash(newTokenHash)
                        .user(user)
                        .expiresAt(
                                LocalDateTime.now()
                                        .plusDays(
                                                refreshTokenExpirationDays
                                        )
                        )
                        .revoked(false)
                        .build();

        // Link old token to new token

        refreshToken.setRevoked(true);
        refreshToken.setRevokedAt(LocalDateTime.now());
        refreshToken.setReplacedByTokenHash(newTokenHash);
        refreshTokenRepository.save(refreshToken);
        refreshTokenRepository.save(newRefreshToken);

        UserPrincipal principal = new UserPrincipal(user);

        String accessToken =
                jwtService.generateAccessToken(principal);

        return AuthenticationResult.builder()
                .authResponse(
                        AuthResponse.builder()
                                .accessToken(accessToken)
                                .tokenType("Bearer")
                                .user(
                                        UserResponse.from(user)
                                )
                                .build()
                )
                .refreshToken(newRawToken)
                .build();
    }

    @Transactional
    public void logout(String rawRefreshToken){
        if(rawRefreshToken==null || rawRefreshToken.isBlank()) {
            return;
        }
        String tokenHash = tokenUtil.hashToken(rawRefreshToken);
        refreshTokenRepository
                .findByTokenHash(tokenHash)
                .ifPresent(token -> {
                    if (!token.isRevoked()) {
                        token.setRevoked(true);
                        token.setRevokedAt(LocalDateTime.now());
                        refreshTokenRepository.save(token);
                    }
                });
    }

    @Transactional
    public void logoutAll(Long userId){
        refreshTokenRepository.revokeAllByUserId(userId);
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
                .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpirationDays))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }


    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private void recordFailedLogin(String email) {
        String attemptsKeys = RedisKeys.loginAttempts(email);
        String lockKeys = RedisKeys.loginLock(email);
        Long attempts = redisService.increment(attemptsKeys);

        if(attempts!=null && attempts==1){
            redisService.expire(attemptsKeys, Duration.ofMinutes(attemptWindowMinutes));
        }
        if(attempts!=null && attempts>=maxFailedAttempts){
            redisService.set(lockKeys, "1", Duration.ofMinutes(lockDurationMinutes));
        }
    }

    private void resetLoginAttempts(String email) {
        redisService.delete(RedisKeys.loginAttempts(email));
        redisService.delete(RedisKeys.loginLock(email));
    }


}