package com.placefy.domain.narrative;

import com.placefy.domain.DomainValidationException;
import java.util.List;

/**
 * The shape a narrative must have: which sections, and how long.
 *
 * <p>Data rather than constants. The bounds and headings are supplied by whoever asks for the
 * generation, so changing the house style never means changing this code, and the deterministic
 * renderer can declare the exact format it satisfies.
 *
 * @param requiredSections headings that must all be present, matched exactly after trimming
 * @param allowExtraSections whether headings outside {@code requiredSections} are tolerated.
 *     False is the safe default: an unexpected section is usually a model inventing a "Next
 *     steps" heading and filling it with advice nobody computed.
 */
public record NarrativeFormat(
        List<String> requiredSections, boolean allowExtraSections, int minCharacters, int maxCharacters) {

    public NarrativeFormat {
        requiredSections = requiredSections == null ? List.of() : List.copyOf(requiredSections);

        if (minCharacters < 0) {
            throw new DomainValidationException("minCharacters", "Minimum length cannot be negative.");
        }
        if (maxCharacters < minCharacters) {
            throw new DomainValidationException(
                    "maxCharacters", "Maximum length must not be below the minimum length.");
        }
    }

    public static NarrativeFormat of(List<String> requiredSections, int minCharacters, int maxCharacters) {
        return new NarrativeFormat(requiredSections, false, minCharacters, maxCharacters);
    }
}
