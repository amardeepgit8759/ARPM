package com.placefy.application.port.in;

import com.placefy.domain.taxonomy.Domain;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** What the outside world learns about a taxonomy version without loading its whole tree. */
public record TaxonomyVersionSummary(
        UUID id,
        String label,
        String status,
        Instant importedAt,
        Instant publishedAt,
        int domainCount,
        int skillCount,
        int subSkillCount) {

    public static TaxonomyVersionSummary from(TaxonomyVersion version) {
        // Counted from the tree rather than read from a stored total, so the summary cannot
        // drift from the content it claims to describe.
        int skills = version.domains().stream().map(Domain::skills).mapToInt(List::size).sum();

        return new TaxonomyVersionSummary(
                version.id().value(),
                version.label().value(),
                version.status().name(),
                version.importedAt(),
                version.publishedAt().orElse(null),
                version.domains().size(),
                skills,
                version.subSkillCount());
    }
}
