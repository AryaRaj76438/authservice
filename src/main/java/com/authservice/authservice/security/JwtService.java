package com.authservice.authservice.security;

import com.authservice.authservice.config.JwtProperties;

import io.jsonwebtoken.*;

import io.jsonwebtoken.security.Keys;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {

        byte[] keyBytes =
                jwtProperties
                        .getSecret()
                        .getBytes(StandardCharsets.UTF_8);

        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(
            UserPrincipal user
    ) {

        Date now = new Date();

        Date expiration =
                new Date(
                        now.getTime()
                                + jwtProperties
                                .getAccessTokenExpiration()
                );

        return Jwts.builder()

                .subject(user.getUsername())

                .claim(
                        "userId",
                        user.getId()
                )

                .claim(
                        "roles",
                        user.getAuthorities()
                                .stream()
                                .map(Object::toString)
                                .toList()
                )

                .issuedAt(now)

                .expiration(expiration)

                .signWith(getSigningKey())

                .compact();
    }

    public String extractUsername(
            String token
    ) {

        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean isTokenValid(
            String token,
            UserPrincipal user
    ) {

        try {

            String username =
                    extractUsername(token);

            return username.equals(
                    user.getUsername()
            ) && !isExpired(token);

        } catch (JwtException |
                 IllegalArgumentException e) {

            return false;
        }
    }

    private boolean isExpired(
            String token
    ) {

        Date expiration =
                Jwts.parser()
                        .verifyWith(getSigningKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload()
                        .getExpiration();

        return expiration.before(
                new Date()
        );
    }
}