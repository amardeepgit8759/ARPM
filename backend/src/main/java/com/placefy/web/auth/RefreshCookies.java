package com.placefy.web.auth;

import com.placefy.application.port.out.TimeProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Reads and writes the one cookie APRM sets. */
@Component
public class RefreshCookies {

    private final RefreshCookieProperties properties;
    private final TimeProvider time;

    RefreshCookies(RefreshCookieProperties properties, TimeProvider time) {
        this.properties = properties;
        this.time = time;
    }

    /**
     * Max-Age is derived from the expiry the application layer already decided, rather than from a
     * second copy of the lifetime in web configuration. One source of truth, no drift.
     */
    public ResponseCookie issue(String token, Instant expiresAt) {
        Duration maxAge = Duration.between(time.now(), expiresAt);
        return base(token).maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build();
    }

    /** Expires the cookie in the browser: sent when a refresh token is rejected. */
    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    Optional<String> readFrom(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> properties.name().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(properties.name(), value)
                .httpOnly(true)
                .secure(properties.secure())
                .path(properties.path())
                .sameSite(properties.sameSite());
    }
}
