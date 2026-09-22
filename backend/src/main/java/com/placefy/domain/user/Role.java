package com.placefy.domain.user;

/**
 * What a user is allowed to be.
 *
 * <p>{@link #STUDENT} is the only role registration can produce, and the only role the product
 * is about. {@link #ADMIN} exists solely to gate the content-publishing endpoints that put a
 * taxonomy version or a requirement set into circulation.
 *
 * <p><strong>There is deliberately no API that grants ADMIN.</strong> Not an endpoint, not a
 * flag on registration, not a configuration property that promotes an email address.
 * Promotion is a deliberate database action — a migration or a DBA statement — so there is no
 * privilege-escalation surface to get wrong, and granting it always leaves a trace outside the
 * application. {@code UserTest} asserts that registration cannot produce this role.
 */
public enum Role {
    STUDENT,
    ADMIN
}
