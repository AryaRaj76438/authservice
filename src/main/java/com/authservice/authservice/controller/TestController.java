package com.authservice.authservice.controller;

import com.authservice.authservice.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    @GetMapping("/protected")
    public Map<String, Object> protectedEndpoint(
            @AuthenticationPrincipal
            UserPrincipal user
    ) {

        return Map.of(
                "message",
                "You accessed a protected endpoint",
                "userId",
                user.getId(),
                "email",
                user.getUsername(),
                "roles",
                user.getAuthorities()
        );
    }
}