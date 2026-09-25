package com.placefy.web.user;

import com.placefy.application.port.in.AuthenticatedSession;
import com.placefy.application.port.in.ChangeMyPassword;
import com.placefy.application.port.in.DeleteMyAccount;
import com.placefy.application.port.in.ExportMyData;
import com.placefy.application.port.in.UserDataExport;
import com.placefy.domain.user.UserId;
import com.placefy.web.auth.RefreshCookies;
import com.placefy.web.auth.SessionResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a person can do to their own account: secure it, take their data, or end it.
 *
 * <p>All three resolve the user from the verified token's subject. Neither accepts an identifier, so
 * neither can be pointed at somebody else.
 */
@RestController
@RequestMapping("/api/v1/students/me")
class AccountController {

    private final ChangeMyPassword changeMyPassword;
    private final ExportMyData exportMyData;
    private final DeleteMyAccount deleteMyAccount;
    private final RefreshCookies cookies;

    AccountController(
            ChangeMyPassword changeMyPassword,
            ExportMyData exportMyData,
            DeleteMyAccount deleteMyAccount,
            RefreshCookies cookies) {
        this.changeMyPassword = changeMyPassword;
        this.exportMyData = exportMyData;
        this.deleteMyAccount = deleteMyAccount;
        this.cookies = cookies;
    }

    /**
     * Answers with a new session, cookie included, because every previous session — this one's
     * included — has just been revoked. The caller swaps its access token for the new one.
     */
    @PostMapping("/password")
    ResponseEntity<SessionResponse> changePassword(
            @AuthenticationPrincipal Jwt jwt, @RequestBody AccountRequests.ChangePasswordRequest request) {

        AuthenticatedSession session = changeMyPassword.handle(new ChangeMyPassword.ChangeMyPasswordCommand(
                UserId.parse(jwt.getSubject()), request.currentPassword(), request.newPassword()));

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookies.issue(session.refreshToken(), session.refreshTokenExpiresAt())
                                .toString())
                .body(SessionResponse.from(session));
    }

    /**
     * Served as a download rather than an inline body. The point of an export is to leave with
     * it, and a browser that renders 40KB of JSON in a tab has not helped anybody do that.
     */
    @GetMapping("/export")
    ResponseEntity<UserDataExport> export(@AuthenticationPrincipal Jwt jwt) {
        UserDataExport export = exportMyData.handle(UserId.parse(jwt.getSubject()));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"aprm-export.json\"")
                .body(export);
    }

    /**
     * Requires the current password in the body, on top of the access token.
     *
     * <p>A body on DELETE is legal and Spring reads it, but some proxies drop it. If that ever
     * bites in a deployment, the fix is a POST alias rather than dropping the password check.
     */
    @DeleteMapping
    ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt, @RequestBody AccountRequests.DeleteAccountRequest request) {

        deleteMyAccount.handle(
                new DeleteMyAccount.DeleteMyAccountCommand(UserId.parse(jwt.getSubject()), request.password()));

        // The account is gone; the browser should not keep a cookie pointing at it.
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }
}
