package com.placefy.domain.question;

import com.placefy.domain.DomainValidationException;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A named person's claim that a question is their own work.
 *
 * <p>This exists because of a specific risk: a question bank assembled for interview preparation
 * is the most tempting place in the product to paste in somebody's real assessment. That is a
 * copyright problem and a fairness problem, and it is invisible once the question is in the bank
 * — a reproduced item looks exactly like an original one.
 *
 * <p>So the attestation is structural, not advisory. {@code originalWork} must be true for a
 * question to enter the bank; false is a rejection rather than a note, because there is no
 * approved path for non-original items. The source note is required even then, so there is
 * always a sentence on the record saying where the question came from.
 */
public record OriginalityAttestation(
        String attestedBy, LocalDate attestedOn, boolean originalWork, String sourceNote) {

    private static final int MIN_NOTE = 10;

    public OriginalityAttestation {
        if (attestedBy == null || attestedBy.isBlank()) {
            throw new DomainValidationException("attestedBy", "An originality attestation needs a named author.");
        }
        Objects.requireNonNull(attestedOn, "attestedOn");
        if (sourceNote == null || sourceNote.strip().length() < MIN_NOTE) {
            throw new DomainValidationException(
                    "sourceNote",
                    "The source note must say where the question came from, in at least " + MIN_NOTE
                            + " characters.");
        }
        attestedBy = attestedBy.strip();
        sourceNote = sourceNote.strip();
    }
}
