package com.placefy.domain;

/**
 * Raised when a value object or aggregate is handed a value the domain does not accept.
 *
 * <p>Carries a stable {@code field} name so the web layer can build an RFC 7807 response
 * without string-matching the message.
 */
public class DomainValidationException extends RuntimeException {

    private final String field;

    public DomainValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
