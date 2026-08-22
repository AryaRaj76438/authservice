package com.authservice.authservice.controller;

import com.authservice.authservice.dto.request.*;
import com.authservice.authservice.dto.response.AuthResponse;
import com.authservice.authservice.dto.response.AuthenticationResult;
import com.authservice.authservice.dto.response.MessageResponse;
import com.authservice.authservice.exception.UnauthorizedException;
import com.authservice.authservice.security.UserPrincipal;
import com.authservice.authservice.service.AuthService;
import com.authservice.authservice.service.CookieService;
import com.authservice.authservice.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final CookieService cookieService;

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
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletResponse response) {
        AuthenticationResult result = authService.login(request);
        cookieService.addRefreshTokenCookie(response,result.getRefreshToken());
        return ResponseEntity.ok(result.getAuthResponse());
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response){
        String refreshToken = cookieService.getRefreshToken(request);
        authService.logout(refreshToken);
        cookieService.clearRefreshTokenCookie(response);
        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Logged out successfully"
                )
        );
    }

    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(Authentication authentication){
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        authService.logoutAll(userPrincipal.getId());
        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "All sessions have been logged out"
                )
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = cookieService.getRefreshToken(request);

        if (refreshToken == null ||
                refreshToken.isBlank()) {

            throw new UnauthorizedException("Refresh token is missing");
        }


        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();

        refreshRequest.setRefreshToken(refreshToken);

        AuthenticationResult result = authService.refreshToken(refreshRequest);

        /*
         * Rotate refresh-token cookie.
         */
        cookieService.addRefreshTokenCookie(response, result.getRefreshToken());

        return ResponseEntity.ok(
                result.getAuthResponse()
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