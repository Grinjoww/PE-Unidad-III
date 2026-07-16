package com.biopet.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class JwtCookieService {
    private final String cookieName;
    private final boolean secure;
    private final long expirationMs;

    public JwtCookieService(
            @Value("${security.jwt.cookie.name}") String cookieName,
            @Value("${security.jwt.cookie.secure}") boolean secure,
            @Value("${security.jwt.expiration-ms}") long expirationMs
    ) {
        this.cookieName = cookieName;
        this.secure = secure;
        this.expirationMs = expirationMs;
    }

    public String cookieName() {
        return cookieName;
    }

    public ResponseCookie build(String jwt) {
        return baseCookie(jwt)
                .maxAge(Duration.ofMillis(expirationMs))
                .build();
    }

    public ResponseCookie buildExpired() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("Lax");
    }
}
