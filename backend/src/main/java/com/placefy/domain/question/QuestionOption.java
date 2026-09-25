package com.placefy.domain.question;

import com.placefy.domain.DomainValidationException;
import java.util.regex.Pattern;

/**
 * One answer choice.
 *
 * @param id stable within its question, so a stored response survives the options being
 *     reordered or reworded on screen
 */
public record QuestionOption(String id, String text) {

    private static final Pattern ID = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int MAX_TEXT = 1000;

    public QuestionOption {
        if (id == null || !ID.matcher(id).matches()) {
            throw new DomainValidationException(
                    "optionId", "Option id must be lower-case kebab-case: " + id);
        }
        if (text == null || text.isBlank()) {
            throw new DomainValidationException("optionText", "Option '" + id + "' has no text.");
        }
        if (text.length() > MAX_TEXT) {
            throw new DomainValidationException(
                    "optionText", "Option '" + id + "' exceeds " + MAX_TEXT + " characters.");
        }
        text = text.strip();
    }

    public static QuestionOption of(String id, String text) {
        return new QuestionOption(id, text);
    }
}
