package com.placefy.web.auth;

import com.placefy.application.error.InvalidRefreshTokenException;
import com.placefy.application.port.in.AuthenticatedSession;
import com.placefy.application.port.in.Login;
import com.placefy.application.port.in.RefreshSession;
import com.placefy.application.port.in.RegisterUser;
import com.placefy.web.auth.AuthRequests.LoginRequest;
import com.placefy.web.auth.AuthRequests.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Talks only to inbound ports. It holds no repository, no entity manager and no rule: it turns
 * HTTP into a command, and a session into HTTP.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final RegisterUser registerUser;
    private final Login login;
    private final RefreshSession refreshSession;
    private final RefreshCookies cookies;

    AuthController(RegisterUser registerUser, Login login, RefreshSession refreshSession, RefreshCookies cookies) {
        this.registerUser = registerUser;
        this.login = login;
        this.refreshSession = refreshSession;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    ResponseEntity<SessionResponse> register(@RequestBody RegisterRequest request) {
        AuthenticatedSession session = registerUser.handle(
                new RegisterUser.RegisterUserCommand(request.name(), request.email(), request.password()));
        return respondWith(session, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    ResponseEntity<SessionResponse> login(@RequestBody LoginRequest request) {
        AuthenticatedSession session = login.handle(new Login.LoginCommand(request.email(), request.password()));
        return respondWith(session, HttpStatus.OK);
    }

    /**
     * Takes no body. The credential is the httpOnly cookie, which no script on the page can read
     * and therefore no script can accidentally forward somewhere else.
     */
    @PostMapping("/refresh")
    ResponseEntity<SessionResponse> refresh(HttpServletRequest request) {
        String presented = cookies.readFrom(request).orElseThrow(InvalidRefreshTokenException::new);
        AuthenticatedSession session = refreshSession.handle(new RefreshSession.RefreshSessionCommand(presented));
        return respondWith(session, HttpStatus.OK);
    }

    private ResponseEntity<SessionResponse> respondWith(AuthenticatedSession session, HttpStatus status) {
        ResponseCookie cookie = cookies.issue(session.refreshToken(), session.refreshTokenExpiresAt());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(SessionResponse.from(session));
    }
}
