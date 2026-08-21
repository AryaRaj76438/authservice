package com.authservice.authservice.exception;

public class RefreshTokenReuseException extends UnauthorizedException {
    public RefreshTokenReuseException(String message) {
        super(message);
    }
}
