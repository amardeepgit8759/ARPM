package com.placefy.domain.narrative;

import com.placefy.domain.taxonomy.SubSkill;
import com.placefy.domain.taxonomy.SubSkillCode;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Every sub-skill name and code the taxonomy knows, and the masking that keeps their digits out
 * of the number extractor.
 *
 * <p>Two problems this solves, both of which produce false accusations without it.
 *
 * <p>First, digits inside identifiers. A narrative that mentions the sub-skill {@code os2} or
 * "Dynamic Programming on Grids" is naming a thing, not claiming a quantity, and reading the 2
 * out of {@code os2} would reject a perfectly honest sentence.
 *
 * <p>Second, and more insidiously, spelled numbers inside names. The shipped taxonomy contains
 * "Two-Pointer Technique". Extract before masking and every correct mention of it asserts the
 * number two.
 *
 * <p>So masking happens first, and it replaces matched terms with spaces rather than deleting
 * them, because every offset reported afterwards has to still point at the original text.
 */
public final class SubSkillVocabulary {

    private final Map<String, SubSkillCode> termsToCodes;
    private final List<String> termsLongestFirst;

    private SubSkillVocabulary(Map<String, SubSkillCode> termsToCodes) {
        this.termsToCodes = Map.copyOf(termsToCodes);
        // Longest first: masking "Two-Pointer Technique" before "Two" stops a partial match from
        // shadowing the full one.
        List<String> ordered = new ArrayList<>(termsToCodes.keySet());
        ordered.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));
        this.termsLongestFirst = List.copyOf(ordered);
    }

    public static SubSkillVocabulary from(TaxonomyVersion taxonomy) {
        Map<String, SubSkillCode> terms = new LinkedHashMap<>();
        for (SubSkill subSkill : taxonomy.allSubSkills()) {
            terms.put(subSkill.code().value().toLowerCase(Locale.ROOT), subSkill.code());
            terms.put(subSkill.name().value().toLowerCase(Locale.ROOT), subSkill.code());
        }
        return new SubSkillVocabulary(terms);
    }

    /** For tests and for callers that hold names without a whole taxonomy. */
    public static SubSkillVocabulary of(Map<String, SubSkillCode> termsToCodes) {
        Map<String, SubSkillCode> lowered = new LinkedHashMap<>();
        termsToCodes.forEach((term, code) -> lowered.put(term.toLowerCase(Locale.ROOT), code));
        return new SubSkillVocabulary(lowered);
    }

    /** Every sub-skill this text names, in the order they first appear. */
    public List<SubSkillCode> mentionedIn(String text) {
        String haystack = text.toLowerCase(Locale.ROOT);
        List<SubSkillCode> found = new ArrayList<>();

        for (String term : termsLongestFirst) {
            int from = 0;
            int at;
            while ((at = haystack.indexOf(term, from)) >= 0) {
                if (isWholeTerm(haystack, at, term.length()) && !found.contains(termsToCodes.get(term))) {
                    found.add(termsToCodes.get(term));
                }
                from = at + term.length();
            }
        }
        return List.copyOf(found);
    }

    public Optional<SubSkillCode> codeFor(String term) {
        return Optional.ofNullable(termsToCodes.get(term.toLowerCase(Locale.ROOT)));
    }

    /**
     * Blanks out every known term, preserving the length of the text so offsets stay meaningful.
     */
    String maskTerms(String text) {
        char[] masked = text.toCharArray();
        String haystack = text.toLowerCase(Locale.ROOT);

        for (String term : termsLongestFirst) {
            int from = 0;
            int at;
            while ((at = haystack.indexOf(term, from)) >= 0) {
                if (isWholeTerm(haystack, at, term.length()) && notAlreadyMasked(masked, at, term.length())) {
                    for (int i = at; i < at + term.length(); i++) {
                        masked[i] = ' ';
                    }
                }
                from = at + term.length();
            }
        }
        return new String(masked);
    }

    /**
     * A term only counts when it stands alone. Hyphens count as part of a term, so
     * "two-pointer-technique" matches, while "subarrays" does not match "arrays".
     */
    private static boolean isWholeTerm(String haystack, int start, int length) {
        return isBoundary(haystack, start - 1) && isBoundary(haystack, start + length);
    }

    private static boolean isBoundary(String haystack, int index) {
        if (index < 0 || index >= haystack.length()) {
            return true;
        }
        char c = haystack.charAt(index);
        return !Character.isLetterOrDigit(c) && c != '-';
    }

    private static boolean notAlreadyMasked(char[] masked, int start, int length) {
        for (int i = start; i < start + length; i++) {
            if (masked[i] != ' ') {
                return true;
            }
        }
        return false;
    }
}
