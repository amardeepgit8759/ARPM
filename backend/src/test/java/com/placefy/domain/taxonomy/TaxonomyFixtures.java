package com.placefy.domain.taxonomy;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Builders that keep the taxonomy tests readable. Nothing here validates; the aggregate does. */
final class TaxonomyFixtures {

    static final Instant IMPORTED_AT = Instant.parse("2026-09-21T09:00:00Z");
    static final ContentChecksum CHECKSUM = ContentChecksum.of("a".repeat(64));

    private TaxonomyFixtures() {}

    static SubSkill subSkill(String code, String... prerequisites) {
        return SubSkill.of(
                SubSkillCode.of(code),
                NodeName.of("Sub-skill " + code),
                EffortHours.of(8),
                Arrays.stream(prerequisites).map(SubSkillCode::of).toList());
    }

    static Skill skill(String code, SubSkill... subSkills) {
        return Skill.of(SkillCode.of(code), NodeName.of("Skill " + code), List.of(subSkills));
    }

    static Domain domain(String code, Skill... skills) {
        return Domain.of(DomainCode.of(code), NodeName.of("Domain " + code), List.of(skills));
    }

    static TaxonomyVersion taxonomy(Domain... domains) {
        return TaxonomyVersion.importedFrom(
                TaxonomyVersionId.of(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                TaxonomyVersionLabel.of("2026.1"),
                CHECKSUM,
                IMPORTED_AT,
                List.of(domains));
    }

    /** The smallest taxonomy that is valid: one domain, one skill, one sub-skill. */
    static TaxonomyVersion minimal() {
        return taxonomy(domain("dsa", skill("arrays", subSkill("two-pointers"))));
    }
}
