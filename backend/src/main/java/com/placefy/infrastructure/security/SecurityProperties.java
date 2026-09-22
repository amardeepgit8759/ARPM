package com.placefy.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every tunable of the auth slice, bound from configuration.
 *
 * <p>The signing secret has no default. An application that starts without one would be signing
 * tokens with a value present in the repository, which is the same as not signing them.
 */
@ConfigurationProperties(prefix = "placefy.security")
public record SecurityProperties(Jwt jwt, RefreshTokenSettings refreshToken, Cors cors) {

    public record Jwt(String secret, String issuer, Duration accessTokenTtl) {

        /** HS256 gives no more security than the key length, so a short secret is a real defect. */
        private static final int MIN_SECRET_BYTES = 32;

        public Jwt {
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "placefy.security.jwt.secret must be set to at least " + MIN_SECRET_BYTES
                                + " bytes. Provide it through the PLACEFY_JWT_SECRET environment variable; "
                                + "it is deliberately absent from committed configuration.");
            }
            if (issuer == null || issuer.isBlank()) {
                throw new IllegalStateException("placefy.security.jwt.issuer must be set.");
            }
            if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
                throw new IllegalStateException("placefy.security.jwt.access-token-ttl must be positive.");
            }
        }
    }

    public record RefreshTokenSettings(Duration ttl) {

        public RefreshTokenSettings {
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                throw new IllegalStateException("placefy.security.refresh-token.ttl must be positive.");
            }
        }
    }

    public record Cors(List<String> allowedOrigins) {

        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }
}
