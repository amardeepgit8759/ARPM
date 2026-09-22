package com.placefy.infrastructure.security;

import com.placefy.application.port.out.AccessTokenIssuer;
import com.placefy.domain.user.User;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Signs a short-lived access token.
 *
 * <p>The claims are identity only. Nothing the product computes — no score, no readiness band —
 * will ever ride in a token: a token is a client-held copy of server state, and a number a client
 * holds is a number with no scoring run behind it.
 */
@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration ttl;

    JwtAccessTokenIssuer(JwtEncoder encoder, SecurityProperties properties) {
        this.encoder = encoder;
        this.issuer = properties.jwt().issuer();
        this.ttl = properties.jwt().accessTokenTtl();
    }

    @Override
    public IssuedAccessToken issue(User user, Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(ttl);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.id().value().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", user.email().value())
                .claim("role", user.role().name())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new IssuedAccessToken(value, expiresAt);
    }
}
