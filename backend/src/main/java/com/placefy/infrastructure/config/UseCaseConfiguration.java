package com.placefy.infrastructure.config;

import com.placefy.application.port.in.GetCurrentUser;
import com.placefy.application.port.in.ImportTaxonomyVersion;
import com.placefy.application.port.in.Login;
import com.placefy.application.port.in.PublishTaxonomyVersion;
import com.placefy.application.port.in.RefreshSession;
import com.placefy.application.port.in.RegisterUser;
import com.placefy.application.port.out.AccessTokenIssuer;
import com.placefy.application.port.out.IdGenerator;
import com.placefy.application.port.out.PasswordHasher;
import com.placefy.application.port.out.RefreshTokenGenerator;
import com.placefy.application.port.out.RefreshTokenHasher;
import com.placefy.application.port.out.RefreshTokenRepository;
import com.placefy.application.port.out.TaxonomyVersionRepository;
import com.placefy.application.port.out.TimeProvider;
import com.placefy.application.port.out.UserRepository;
import com.placefy.application.service.GetCurrentUserService;
import com.placefy.application.service.ImportTaxonomyVersionService;
import com.placefy.application.service.LoginService;
import com.placefy.application.service.PublishTaxonomyVersionService;
import com.placefy.application.service.RefreshSessionService;
import com.placefy.application.service.RegisterUserService;
import com.placefy.application.service.SessionIssuer;
import com.placefy.infrastructure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the use cases by hand.
 *
 * <p>The application layer carries no {@code @Service} or {@code @Autowired}, so every use case is
 * a plain object that can be constructed in a test with three lines and no container. The cost is
 * this file; the benefit is that the dependency rule holds by construction rather than by
 * convention, and ArchUnit can assert it.
 */
@Configuration
class UseCaseConfiguration {

    @Bean
    SessionIssuer sessionIssuer(
            RefreshTokenRepository refreshTokens,
            AccessTokenIssuer accessTokens,
            RefreshTokenGenerator tokenGenerator,
            RefreshTokenHasher tokenHasher,
            IdGenerator ids,
            SecurityProperties properties) {
        return new SessionIssuer(
                refreshTokens, accessTokens, tokenGenerator, tokenHasher, ids, properties.refreshToken().ttl());
    }

    @Bean
    RegisterUser registerUser(
            UserRepository users,
            PasswordHasher passwordHasher,
            SessionIssuer sessions,
            TimeProvider time,
            IdGenerator ids) {
        return new TransactionalRegisterUser(new RegisterUserService(users, passwordHasher, sessions, time, ids));
    }

    @Bean
    Login login(
            UserRepository users,
            PasswordHasher passwordHasher,
            SessionIssuer sessions,
            TimeProvider time,
            IdGenerator ids) {
        return new TransactionalLogin(new LoginService(users, passwordHasher, sessions, time, ids));
    }

    @Bean
    RefreshSession refreshSession(
            RefreshTokenRepository refreshTokens,
            UserRepository users,
            RefreshTokenHasher tokenHasher,
            SessionIssuer sessions,
            TimeProvider time) {
        return new TransactionalRefreshSession(
                new RefreshSessionService(refreshTokens, users, tokenHasher, sessions, time));
    }

    @Bean
    GetCurrentUser getCurrentUser(UserRepository users) {
        return new GetCurrentUserService(users);
    }

    @Bean
    ImportTaxonomyVersion importTaxonomyVersion(TaxonomyVersionRepository taxonomies) {
        return new TransactionalImportTaxonomyVersion(new ImportTaxonomyVersionService(taxonomies));
    }

    @Bean
    PublishTaxonomyVersion publishTaxonomyVersion(TaxonomyVersionRepository taxonomies, TimeProvider time) {
        return new TransactionalPublishTaxonomyVersion(new PublishTaxonomyVersionService(taxonomies, time));
    }
}
