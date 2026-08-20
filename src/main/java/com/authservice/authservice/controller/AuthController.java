package com.authservice.authservice.controller;

import com.authservice.authservice.dto.request.*;
import com.authservice.authservice.dto.response.AuthResponse;
import com.authservice.authservice.dto.response.MessageResponse;
import com.authservice.authservice.service.AuthService;
import com.authservice.authservice.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/signup")
    public ResponseEntity<MessageResponse> signup(
            @Valid @RequestBody SignupRequest request) {

        authService.signup(request);

        return ResponseEntity.ok(
                new MessageResponse(
                        "Account created. Please check your email to verify your account."
                )
        );
    }

    @GetMapping("/verify")
    public ResponseEntity<MessageResponse> verifyEmail(
            @RequestParam String token) {

        authService.verifyEmail(token);

        return ResponseEntity.ok(
                new MessageResponse(
                        "Email verified successfully. You can now login."
                )
        );
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {

        authService.resendVerificationEmail(request.getEmail());

        return ResponseEntity.ok(
                new MessageResponse(
                        "If the account exists and is not verified, a verification email has been sent."
                )
        );
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(
                authService.login(request)
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {

        return ResponseEntity.ok(
                authService.refreshToken(request)
        );
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request){
        passwordResetService.forgotPassword(request.getEmail());
        /*
         * Always return the same response,
         * regardless of whether the email exists.
         */
        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "If an account exists for this email, a password reset link has been sent."
                )
        );
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request){
        passwordResetService.resetPassword(request.getToken(),request.getNewPassword());
        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Password has been reset successfully."
                )
        );
    }
}