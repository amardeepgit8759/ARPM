package com.placefy.application.port.in;

import com.placefy.domain.session.RefreshToken;
import com.placefy.domain.user.User;
import java.time.Instant;
import java.util.List;

/**
 * Everything Placefy holds about one person, in a form they can take away.
 *
 * <p>Built explicitly rather than by serialising whatever objects happen to be in scope. An
 * export is the one place where "include everything" and "include no secrets" pull in opposite
 * directions, and a reflective dump resolves that tension the wrong way by default — a new field
 * on an entity would silently join the export without anyone deciding it should.
 *
 * @param sessions metadata only. The token hashes are deliberately absent: they are credentials,
 *     not personal data, and a file a student might email to themselves is the last place they
 *     belong.
 */
public record UserDataExport(
        Instant exportedAt, String formatVersion, ExportedUser user, List<ExportedSession> sessions) {

    /** Bumped whenever a field is added or removed, so a consumer can tell what it is reading. */
    public static final String FORMAT_VERSION = "1";

    public record ExportedUser(
            String id, String name, String email, String role, Instant createdAt, Instant updatedAt) {}

    public record ExportedSession(Instant issuedAt, Instant expiresAt, Instant revokedAt, boolean revoked) {}

    public UserDataExport {
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
    }

    public static UserDataExport of(User user, List<RefreshToken> sessions, Instant exportedAt) {
        List<ExportedSession> exportedSessions = sessions.stream()
                .map(token -> new ExportedSession(
                        token.issuedAt(),
                        token.expiresAt(),
                        token.revokedAt().orElse(null),
                        token.isRevoked()))
                .toList();

        return new UserDataExport(
                exportedAt,
                FORMAT_VERSION,
                new ExportedUser(
                        user.id().toString(),
                        user.name().value(),
                        user.email().value(),
                        user.role().name(),
                        user.createdAt(),
                        user.updatedAt()),
                exportedSessions);
    }
}
