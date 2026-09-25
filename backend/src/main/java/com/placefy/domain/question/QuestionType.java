package com.placefy.domain.question;

/**
 * The shape of a question, which determines how its answer is checked.
 *
 * <p>These two differ structurally — one correct option against several — rather than by
 * product policy. Formats that need a different grading path, such as free text or code
 * submission, are a product decision and a scoring decision, and belong here only once
 * somebody has made both.
 */
public enum QuestionType {

    /** Exactly one option is correct. */
    SINGLE_CHOICE,

    /** Two or more options are correct; a response must select all of them. */
    MULTIPLE_CHOICE
}
