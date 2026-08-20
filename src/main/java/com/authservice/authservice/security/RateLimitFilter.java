package com.authservice.authservice.security;

import com.authservice.authservice.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String uri = request.getRequestURI();

        if (!uri.startsWith("/api/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIP(request);
        String identifier = clientIp + ":" + uri;

        RateLimitService.RateLimitResult result =
                rateLimitService.check(
                        identifier,
                        20,
                        Duration.ofMinutes(1)
                );

        if (!result.allowed()) {
            response.setStatus(429);
            response.setHeader(
                    "Retry-After",
                    String.valueOf(result.retryAfter())
            );
            response.setContentType("application/json");

            response.getWriter().write("""
                    {
                      "error": "Too many requests"
                    }
                    """);

            return;
        }

        response.setHeader(
                "X-RateLimit-Remaining",
                String.valueOf(result.remaining())
        );

        filterChain.doFilter(request, response);
    }

    private String getClientIP(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}