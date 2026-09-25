package com.placefy.infrastructure.config;

import com.placefy.application.port.in.ChangeMyPassword;
import com.placefy.application.port.in.DeleteMyAccount;
import com.placefy.application.port.in.ExportMyData;
import com.placefy.application.port.in.GetAdminOverview;
import com.placefy.application.port.in.GetCurrentUser;
import com.placefy.application.port.in.ImportTaxonomyVersion;
import com.placefy.application.port.in.Login;
import com.placefy.application.port.in.Logout;
import com.placefy.application.port.in.PublishTaxonomyVersion;
import com.placefy.application.port.in.RefreshSession;
import com.placefy.application.port.in.RegisterUser;
import com.placefy.application.port.in.UpdateMyProfile;
import com.placefy.application.port.out.AccessTokenIssuer;
import com.placefy.application.port.out.IdGenerator;
import com.placefy.application.port.out.PasswordHasher;
import com.placefy.application.port.out.RefreshTokenGenerator;
import com.placefy.application.port.out.RefreshTokenHasher;
import com.placefy.application.port.out.RefreshTokenRepository;
import com.placefy.application.port.out.TaxonomyVersionRepository;
import com.placefy.application.port.out.TimeProvider;
import com.placefy.application.port.out.UserRepository;
import com.placefy.application.service.ChangeMyPasswordService;
import com.placefy.application.service.DeleteMyAccountService;
import com.placefy.application.service.ExportMyDataService;
import com.placefy.application.service.GetAdminOverviewService;
import com.placefy.application.service.GetCurrentUserService;
import com.placefy.application.service.ImportTaxonomyVersionService;
import com.placefy.application.service.LoginService;
import com.placefy.application.service.LogoutService;
import com.placefy.application.service.PublishTaxonomyVersionService;
import com.placefy.application.service.RefreshSessionService;
import com.placefy.application.service.RegisterUserService;
import com.placefy.application.service.SessionIssuer;
import com.placefy.application.service.UpdateMyProfileService;
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
    Logout logout(RefreshTokenRepository refreshTokens, RefreshTokenHasher tokenHasher, TimeProvider time) {
        return new TransactionalLogout(new LogoutService(refreshTokens, tokenHasher, time));
    }

    @Bean
    UpdateMyProfile updateMyProfile(UserRepository users, TimeProvider time) {
        return new TransactionalUpdateMyProfile(new UpdateMyProfileService(users, time));
    }

    @Bean
    ChangeMyPassword changeMyPassword(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordHasher passwordHasher,
            SessionIssuer sessions,
            TimeProvider time,
            IdGenerator ids) {
        return new TransactionalChangeMyPassword(
                new ChangeMyPasswordService(users, refreshTokens, passwordHasher, sessions, time, ids));
    }

    @Bean
    GetAdminOverview getAdminOverview(UserRepository users) {
        return new GetAdminOverviewService(users);
    }

    @Bean
    ExportMyData exportMyData(UserRepository users, RefreshTokenRepository refreshTokens, TimeProvider time) {
        return new ExportMyDataService(users, refreshTokens, time);
    }

    @Bean
    DeleteMyAccount deleteMyAccount(UserRepository users, PasswordHasher passwordHasher) {
        return new TransactionalDeleteMyAccount(new DeleteMyAccountService(users, passwordHasher));
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
