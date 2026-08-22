package com.authservice.authservice.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CookieService {
    @Value("${app.auth.refresh-cookie-name:refresh_token}")
    private String cookieName;

    @Value("${app.auth.refresh-cookie-path:/api/auth}")
    private String cookiePath;

    @Value("${app.auth.refresh-cookie-secure:false}")
    private boolean secure;

    @Value("${app.auth.refresh-cookie-http-only:true}")
    private boolean httpOnly;

    @Value("${app.auth.refresh-cookie-same-site:Lax}")
    private String sameSite;

    @Value("${app.auth.refresh-cookie-max-age-days:7}")
    private long maxAgeDays;

    public void addRefreshTokenCookie(HttpServletResponse response, String refreshToken){
        ResponseCookie cookie = ResponseCookie
                .from(cookieName, refreshToken)
                .httpOnly(httpOnly)
                .secure(secure)
                .path(cookiePath)
                .sameSite(sameSite)
                .maxAge(Duration.ofDays(maxAgeDays))
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void clearRefreshTokenCookie(HttpServletResponse response){
        ResponseCookie cookie = ResponseCookie
                .from(cookieName, "")
                .httpOnly(httpOnly)
                .secure(secure)
                .path(cookiePath)
                .sameSite(sameSite)
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    public String getRefreshToken(HttpServletRequest request){
        if(request.getCookies()==null) return null;
        for(Cookie cookie: request.getCookies()){
            if(cookieName.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
