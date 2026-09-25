package com.placefy.domain.question;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A stem reduced to the form duplicate detection compares.
 *
 * <p>Normalisation strips the things that differ between two copies of the same question without
 * changing what it asks: case, punctuation, and runs of whitespace. Two stems that normalise to
 * the same text are the same question however differently they were typed.
 *
 * <p>Near-duplicates are caught separately, by overlap of word trigrams. Trigrams rather than
 * individual words because word overlap alone flags any two questions on the same topic — they
 * share the vocabulary of the subject — while shared three-word runs indicate shared phrasing.
 */
public record StemFingerprint(String normalised) {

    private static final int SHINGLE_SIZE = 3;

    public static StemFingerprint of(String stem) {
        String cleaned = stem == null ? "" : stem.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
        return new StemFingerprint(cleaned.replaceAll("\\s+", " "));
    }

    public List<String> words() {
        return normalised.isEmpty() ? List.of() : List.of(normalised.split(" "));
    }

    /**
     * Word trigrams, or the bare words when the stem is too short to have any — a four-word
     * question still deserves to be compared with something.
     */
    public Set<String> shingles() {
        List<String> words = words();
        if (words.size() < SHINGLE_SIZE) {
            return new HashSet<>(words);
        }

        Set<String> shingles = new HashSet<>();
        for (int i = 0; i + SHINGLE_SIZE <= words.size(); i++) {
            shingles.add(String.join(" ", words.subList(i, i + SHINGLE_SIZE)));
        }
        return shingles;
    }

    /** Jaccard overlap, 0.0 for nothing in common and 1.0 for identical phrasing. */
    public double similarityTo(StemFingerprint other) {
        Set<String> mine = shingles();
        Set<String> theirs = other.shingles();

        if (mine.isEmpty() && theirs.isEmpty()) {
            return 1.0;
        }
        if (mine.isEmpty() || theirs.isEmpty()) {
            return 0.0;
        }

        Set<String> union = new HashSet<>(mine);
        union.addAll(theirs);

        List<String> shared = new ArrayList<>(mine);
        shared.retainAll(theirs);

        return (double) shared.size() / union.size();
    }

    public boolean isEmpty() {
        return normalised.isEmpty();
    }
}
