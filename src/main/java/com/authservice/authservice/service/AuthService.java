package com.authservice.authservice.service;

import com.authservice.authservice.dto.request.LoginRequest;
import com.authservice.authservice.dto.request.RefreshTokenRequest;
import com.authservice.authservice.dto.request.SignupRequest;
import com.authservice.authservice.dto.response.AuthResponse;
import com.authservice.authservice.dto.response.UserResponse;

import com.authservice.authservice.entity.RefreshToken;
import com.authservice.authservice.entity.User;
import com.authservice.authservice.entity.VerificationToken;

import com.authservice.authservice.enums.Role;

import com.authservice.authservice.exception.BadRequestException;
import com.authservice.authservice.exception.UnauthorizedException;

import com.authservice.authservice.repository.RefreshTokenRepository;
import com.authservice.authservice.repository.UserRepository;
import com.authservice.authservice.repository.VerificationTokenRepository;

import com.authservice.authservice.security.JwtService;
import com.authservice.authservice.security.UserPrincipal;

import com.authservice.authservice.util.TokenUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import org.springframework.security.core.Authentication;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    private final VerificationTokenRepository
            verificationTokenRepository;

    private final RefreshTokenRepository
            refreshTokenRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManager
            authenticationManager;

    private final JwtService jwtService;

    private final TokenUtil tokenUtil;

    private final EmailService emailService;


    // =====================================================
    // SIGNUP
    // =====================================================

    @Transactional
    public void signup(
            SignupRequest request
    ) {

        String email =
                request.getEmail()
                        .trim()
                        .toLowerCase();

        if (userRepository.existsByEmail(email)) {

            throw new BadRequestException(
                    "An account with this email already exists"
            );
        }

        User user =
                User.builder()

                        .name(
                                request.getName().trim()
                        )

                        .email(email)

                        .password(
                                passwordEncoder.encode(
                                        request.getPassword()
                                )
                        )

                        .role(Role.USER)

                        .emailVerified(false)

                        .enabled(true)

                        .build();

        userRepository.save(user);


        // Generate secure verification token
        String rawToken =
                tokenUtil.generateToken();

        String tokenHash =
                tokenUtil.hashToken(rawToken);


        VerificationToken verificationToken =
                VerificationToken.builder()

                        .tokenHash(tokenHash)

                        .user(user)

                        .expiresAt(
                                LocalDateTime.now()
                                        .plusHours(24)
                        )

                        .build();

        verificationTokenRepository.save(
                verificationToken
        );


        // Send email
        emailService.sendVerificationEmail(
                user,
                rawToken
        );
    }


    // =====================================================
    // VERIFY EMAIL
    // =====================================================

    @Transactional
    public void verifyEmail(
            String rawToken
    ) {

        String tokenHash =
                tokenUtil.hashToken(rawToken);

        VerificationToken verificationToken =
                verificationTokenRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(() ->
                                new BadRequestException(
                                        "Invalid verification link"
                                )
                        );


        if (verificationToken
                .getExpiresAt()
                .isBefore(LocalDateTime.now())) {

            verificationTokenRepository.delete(
                    verificationToken
            );

            throw new BadRequestException(
                    "Verification link has expired"
            );
        }


        User user =
                verificationToken.getUser();

        user.setEmailVerified(true);

        user.setUpdatedAt(
                LocalDateTime.now()
        );

        userRepository.save(user);


        // Token is single-use
        verificationTokenRepository.delete(
                verificationToken
        );
    }


    // =====================================================
    // LOGIN
    // =====================================================

    @Transactional
    public AuthResponse login(
            LoginRequest request
    ) {

        String email =
                request.getEmail()
                        .trim()
                        .toLowerCase();

        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(() ->
                                new UnauthorizedException(
                                        "Invalid email or password"
                                )
                        );


        // VERY IMPORTANT
        if (!user.isEmailVerified()) {

            throw new UnauthorizedException(
                    "Please verify your email before logging in"
            );
        }


        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                email,
                                request.getPassword()
                        )
                );


        UserPrincipal principal =
                (UserPrincipal)
                        authentication.getPrincipal();


        String accessToken =
                jwtService.generateAccessToken(
                        principal
                );


        String refreshToken =
                createRefreshToken(user);


        return AuthResponse.builder()

                .accessToken(accessToken)

                .refreshToken(refreshToken)

                .tokenType("Bearer")

                .user(
                        UserResponse.from(user)
                )

                .build();
    }


    // =====================================================
    // REFRESH TOKEN
    // =====================================================

    @Transactional
    public AuthResponse refreshToken(
            RefreshTokenRequest request
    ) {

        String rawToken =
                request.getRefreshToken();

        String tokenHash =
                tokenUtil.hashToken(rawToken);

        RefreshToken refreshToken =
                refreshTokenRepository
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


        if (refreshToken
                .getExpiresAt()
                .isBefore(LocalDateTime.now())) {

            refreshTokenRepository.delete(
                    refreshToken
            );

            throw new UnauthorizedException(
                    "Refresh token has expired"
            );
        }


        User user =
                refreshToken.getUser();


        if (!user.isEmailVerified()) {

            throw new UnauthorizedException(
                    "Email is not verified"
            );
        }


        // Rotate refresh token
        refreshToken.setRevoked(true);

        refreshTokenRepository.save(
                refreshToken
        );


        UserPrincipal principal =
                new UserPrincipal(user);

        String accessToken =
                jwtService.generateAccessToken(
                        principal
                );

        String newRefreshToken =
                createRefreshToken(user);


        return AuthResponse.builder()

                .accessToken(accessToken)

                .refreshToken(newRefreshToken)

                .tokenType("Bearer")

                .user(
                        UserResponse.from(user)
                )

                .build();
    }


    // =====================================================
    // RESEND VERIFICATION
    // =====================================================

    @Transactional
    public void resendVerificationEmail(
            String email
    ) {

        email =
                email.trim()
                        .toLowerCase();

        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(() ->
                                new BadRequestException(
                                        "Unable to process request"
                                )
                        );


        if (user.isEmailVerified()) {

            throw new BadRequestException(
                    "Email is already verified"
            );
        }


        verificationTokenRepository
                .deleteByUser(user);


        String rawToken =
                tokenUtil.generateToken();

        String tokenHash =
                tokenUtil.hashToken(rawToken);


        VerificationToken verificationToken =
                VerificationToken.builder()

                        .tokenHash(tokenHash)

                        .user(user)

                        .expiresAt(
                                LocalDateTime.now()
                                        .plusHours(24)
                        )

                        .build();


        verificationTokenRepository.save(
                verificationToken
        );


        emailService.sendVerificationEmail(
                user,
                rawToken
        );
    }


    // =====================================================
    // CREATE REFRESH TOKEN
    // =====================================================

    private String createRefreshToken(
            User user
    ) {

        String rawToken =
                tokenUtil.generateToken();

        String tokenHash =
                tokenUtil.hashToken(rawToken);


        RefreshToken refreshToken =
                RefreshToken.builder()

                        .tokenHash(tokenHash)

                        .user(user)

                        .expiresAt(
                                LocalDateTime.now()
                                        .plusDays(7)
                        )

                        .revoked(false)

                        .build();


        refreshTokenRepository.save(
                refreshToken
        );


        return rawToken;
    }
}