package com.authservice.authservice.controller;

import com.authservice.authservice.dto.request.LoginRequest;
import com.authservice.authservice.dto.request.RefreshTokenRequest;
import com.authservice.authservice.dto.request.ResendVerificationRequest;
import com.authservice.authservice.dto.request.SignupRequest;

import com.authservice.authservice.dto.response.AuthResponse;
import com.authservice.authservice.dto.response.MessageResponse;

import com.authservice.authservice.service.AuthService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;


    // =====================================================
    // SIGNUP
    // =====================================================

    @PostMapping("/signup")
    public ResponseEntity<MessageResponse> signup(
            @Valid @RequestBody SignupRequest request
    ) {

        authService.signup(request);

        return ResponseEntity.ok(
                new MessageResponse(
                        "Account created. Please check your email to verify your account."
                )
        );
    }


    // =====================================================
    // VERIFY EMAIL
    // =====================================================

    @GetMapping("/verify")
    public ResponseEntity<MessageResponse> verifyEmail(
            @RequestParam String token
    ) {

        authService.verifyEmail(token);

        return ResponseEntity.ok(
                new MessageResponse(
                        "Email verified successfully. You can now login."
                )
        );
    }


    // =====================================================
    // RESEND VERIFICATION
    // =====================================================

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse>
    resendVerification(
            @Valid
            @RequestBody
            ResendVerificationRequest request
    ) {

        authService.resendVerificationEmail(
                request.getEmail()
        );

        return ResponseEntity.ok(
                new MessageResponse(
                        "If the account exists and is not verified, a verification email has been sent."
                )
        );
    }


    // =====================================================
    // LOGIN
    // =====================================================

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {

        return ResponseEntity.ok(
                authService.login(request)
        );
    }


    // =====================================================
    // REFRESH
    // =====================================================

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid
            @RequestBody
            RefreshTokenRequest request
    ) {

        return ResponseEntity.ok(
                authService.refreshToken(request)
        );
    }
}