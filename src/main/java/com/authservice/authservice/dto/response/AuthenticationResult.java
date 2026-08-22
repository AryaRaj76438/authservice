package com.authservice.authservice.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthenticationResult {
    private AuthResponse authResponse;
    private String refreshToken;
}