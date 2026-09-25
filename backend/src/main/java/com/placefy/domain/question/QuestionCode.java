package com.placefy.domain.question;

import com.placefy.domain.DomainValidationException;
import java.util.regex.Pattern;

/**
 * Stable identity for a question, unique across the whole bank.
 *
 * <p>A response records the code it answered, so a code that changes silently severs a stored
 * response from the question it was given. Stems may be reworded and options reordered; codes
 * may not be reused or renamed.
 */
public record QuestionCode(String value) {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int MAX_LENGTH = 80;

    public QuestionCode {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("questionCode", "Question code is required.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new DomainValidationException(
                    "questionCode", "Question code must be at most " + MAX_LENGTH + " characters: " + value);
        }
        if (!SLUG.matcher(value).matches()) {
            throw new DomainValidationException(
                    "questionCode", "Question code must be lower-case kebab-case: " + value);
        }
    }

    public static QuestionCode of(String value) {
        return new QuestionCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
