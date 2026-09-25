package com.placefy.web.error;

import com.placefy.application.error.AdminAccessRequiredException;
import com.placefy.application.error.EmailAlreadyRegisteredException;
import com.placefy.application.error.InvalidCredentialsException;
import com.placefy.application.error.InvalidRefreshTokenException;
import com.placefy.application.error.UserNotFoundException;
import com.placefy.domain.DomainValidationException;
import com.placefy.web.auth.RefreshCookies;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Every failure leaves this API as RFC 7807 {@code application/problem+json}.
 *
 * <p>The {@code type} URIs are URNs rather than http URLs so that they are stable identifiers a
 * client can branch on without implying a documentation site exists to fetch.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private static final String TYPE_PREFIX = "urn:placefy:problem:";

    private final RefreshCookies refreshCookies;

    GlobalExceptionHandler(RefreshCookies refreshCookies) {
        this.refreshCookies = refreshCookies;
    }

    @ExceptionHandler(DomainValidationException.class)
    ProblemDetail handleValidation(DomainValidationException e, HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed", e.getMessage(), request);
        // The offending field, so a form can highlight one input instead of showing a banner.
        problem.setProperty("field", e.field());
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "malformed-request",
                "Malformed request",
                "The request body could not be read as JSON.",
                request);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ProblemDetail handleDuplicateEmail(EmailAlreadyRegisteredException e, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT, "email-already-registered", "Email already registered", e.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException e, HttpServletRequest request) {
        return problem(
                HttpStatus.UNAUTHORIZED, "invalid-credentials", "Invalid credentials", e.getMessage(), request);
    }

    /**
     * Also clears the cookie. The browser is holding a token the server will never accept again;
     * leaving it in place means every subsequent page load retries a doomed refresh.
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    ResponseEntity<ProblemDetail> handleInvalidRefreshToken(
            InvalidRefreshTokenException e, HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.UNAUTHORIZED, "invalid-refresh-token", "Session expired", e.getMessage(), request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .body(problem);
    }

    /** Same type URI as ProblemDetailAccessDeniedHandler, which refuses the same thing one layer out. */
    @ExceptionHandler(AdminAccessRequiredException.class)
    ProblemDetail handleAdminAccessRequired(AdminAccessRequiredException e, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", e.getMessage(), request);
    }

    /** A signature that verifies over an account that no longer exists is not an authentication. */
    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleUserNotFound(HttpServletRequest request) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                "unauthenticated",
                "Unauthenticated",
                "A valid access token is required for this resource.",
                request);
    }

    private ProblemDetail problem(
            HttpStatus status, String type, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(TYPE_PREFIX + type));
        problem.setTitle(title);
        problem.setDetail(detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
