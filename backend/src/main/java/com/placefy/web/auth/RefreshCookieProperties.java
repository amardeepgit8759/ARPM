package com.placefy.web.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the refresh cookie is written. This is an HTTP transport concern and it lives in the web
 * layer, which is why it is not folded into the security properties: the web layer is not allowed
 * to reach into infrastructure for its own configuration.
 *
 * @param secure must be true wherever a browser reaches Placefy over HTTPS. The local profile
 *     turns it off only because a Secure cookie is never sent over plain-HTTP localhost.
 * @param sameSite Strict. The cookie is the sole credential {@code /refresh} accepts, and Strict
 *     means a cross-site request cannot carry it — which is what stands in for a CSRF token here.
 */
@ConfigurationProperties(prefix = "placefy.web.refresh-cookie")
public record RefreshCookieProperties(String name, boolean secure, String path, String sameSite) {

    public RefreshCookieProperties {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("placefy.web.refresh-cookie.name must be set.");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("placefy.web.refresh-cookie.path must be set.");
        }
        if (sameSite == null || sameSite.isBlank()) {
            throw new IllegalStateException("placefy.web.refresh-cookie.same-site must be set.");
        }
    }
}
