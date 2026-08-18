package com.authservice.authservice.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {

    private String accessToken;

    private String refreshToken;

    private String tokenType;

    private UserResponse user;
}