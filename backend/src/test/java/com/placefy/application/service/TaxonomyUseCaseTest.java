package com.placefy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.application.error.TaxonomyContentChangedException;
import com.placefy.application.error.TaxonomyVersionNotFoundException;
import com.placefy.application.fake.FixedTimeProvider;
import com.placefy.application.fake.InMemoryTaxonomyVersionRepository;
import com.placefy.application.port.in.ImportTaxonomyVersion;
import com.placefy.application.port.in.ImportTaxonomyVersion.ImportTaxonomyVersionCommand;
import com.placefy.application.port.in.PublishTaxonomyVersion;
import com.placefy.application.port.in.PublishTaxonomyVersion.PublishTaxonomyVersionCommand;
import com.placefy.application.port.in.TaxonomyVersionSummary;
import com.placefy.domain.taxonomy.ContentChecksum;
import com.placefy.domain.taxonomy.Domain;
import com.placefy.domain.taxonomy.DomainCode;
import com.placefy.domain.taxonomy.EffortHours;
import com.placefy.domain.taxonomy.NodeName;
import com.placefy.domain.taxonomy.Skill;
import com.placefy.domain.taxonomy.SkillCode;
import com.placefy.domain.taxonomy.SubSkill;
import com.placefy.domain.taxonomy.SubSkillCode;
import com.placefy.domain.taxonomy.TaxonomyAlreadyPublishedException;
import com.placefy.domain.taxonomy.TaxonomyStatus;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import com.placefy.domain.taxonomy.TaxonomyVersionId;
import com.placefy.domain.taxonomy.TaxonomyVersionLabel;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TaxonomyUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-21T09:00:00Z");
    private static final TaxonomyVersionLabel LABEL = TaxonomyVersionLabel.of("2026.1");

    private final InMemoryTaxonomyVersionRepository repository = new InMemoryTaxonomyVersionRepository();
    private final FixedTimeProvider time = new FixedTimeProvider(NOW);

    private final ImportTaxonomyVersion importTaxonomy = new ImportTaxonomyVersionService(repository);
    private final PublishTaxonomyVersion publishTaxonomy = new PublishTaxonomyVersionService(repository, time);

    @Nested
    class Importing {

        @Test
        void importsANewVersionAsADraft() {
            ImportTaxonomyVersion.Result result = importTaxonomy.handle(new ImportTaxonomyVersionCommand(version("a")));

            assertThat(result.outcome()).isEqualTo(ImportTaxonomyVersion.Outcome.IMPORTED);
            assertThat(result.summary().status()).isEqualTo(TaxonomyStatus.DRAFT.name());
            assertThat(result.summary().label()).isEqualTo("2026.1");
            assertThat(repository.insertCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("the summary counts come from the tree, not from a stored total")
        void summaryReportsTheContentCounts() {
            TaxonomyVersionSummary summary =
                    importTaxonomy.handle(new ImportTaxonomyVersionCommand(version("a"))).summary();

            assertThat(summary.domainCount()).isEqualTo(1);
            assertThat(summary.skillCount()).isEqualTo(1);
            assertThat(summary.subSkillCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("re-importing identical content writes nothing, so seeding is safe on every boot")
        void isIdempotentForIdenticalContent() {
            importTaxonomy.handle(new ImportTaxonomyVersionCommand(version("a")));

            ImportTaxonomyVersion.Result second =
                    importTaxonomy.handle(new ImportTaxonomyVersionCommand(version("a")));

            assertThat(second.outcome()).isEqualTo(ImportTaxonomyVersion.Outcome.ALREADY_IMPORTED);
            assertThat(repository.insertCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("the same label with different content is refused, not merged")
        void refusesChangedContentUnderAnExistingLabel() {
            importTaxonomy.handle(new ImportTaxonomyVersionCommand(version("a")));

            assertThatThrownBy(() -> importTaxonomy.handle(new ImportTaxonomyVersionCommand(version("b"))))
                    .isInstanceOf(TaxonomyContentChangedException.class)
                    .hasMessageContaining("2026.1")
                    .hasMessageContaining("bump");

            assertThat(repository.insertCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("a rejected re-import leaves the stored version untouched")
        void aRejectedReImportChangesNothing() {
            importTaxonomy.handle(new ImportTaxonomyVersionCommand(version("a")));
            ContentChecksum before = repository.findByLabel(LABEL).orElseThrow().sourceChecksum();

            assertThatThrownBy(() -> importTaxonomy.handle(new ImportTaxonomyVersionCommand(version("b"))))
                    .isInstanceOf(TaxonomyContentChangedException.class);

            assertThat(repository.findByLabel(LABEL).orElseThrow().sourceChecksum()).isEqualTo(before);
        }

        @Test
        void differentLabelsCoexist() {
            importTaxonomy.handle(new ImportTaxonomyVersionCommand(version("a")));
            importTaxonomy.handle(new ImportTaxonomyVersionCommand(
                    version(TaxonomyVersionLabel.of("2026.2"), "b")));

            assertThat(repository.insertCount()).isEqualTo(2);
            assertThat(repository.findByLabel(TaxonomyVersionLabel.of("2026.2"))).isPresent();
        }
    }

    @Nested
    class Publishing {

        @Test
        void publishesADraft() {
            importTaxonomy.handle(new ImportTaxonomyVersionCommand(version("a")));
            time.advanceBy(Duration.ofMinutes(5));

            TaxonomyVersionSummary summary = publishTaxonomy.handle(new PublishTaxonomyVersionCommand(LABEL));

            assertThat(summary.status()).isEqualTo(TaxonomyStatus.PUBLISHED.name());
            assertThat(summary.publishedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
            assertThat(repository.publishCount()).isEqualTo(1);
        }

        @Test
        void rejectsAnUnknownLabel() {
            assertThatThrownBy(() ->
                            publishTaxonomy.handle(new PublishTaxonomyVersionCommand(TaxonomyVersionLabel.of("ghost"))))
                    .isInstanceOf(TaxonomyVersionNotFoundException.class);
        }

        @Test
        @DisplayName("publishing twice is refused by the aggregate, not silently repeated")
        void refusesToPublishTwice() {
            importTaxonomy.handle(new ImportTaxonomyVersionCommand(version("a")));
            publishTaxonomy.handle(new PublishTaxonomyVersionCommand(LABEL));

            assertThatThrownBy(() -> publishTaxonomy.handle(new PublishTaxonomyVersionCommand(LABEL)))
                    .isInstanceOf(TaxonomyAlreadyPublishedException.class);

            assertThat(repository.publishCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("re-importing a published version's own content is still a no-op")
        void reImportingAPublishedVersionIsStillIdempotent() {
            importTaxonomy.handle(new ImportTaxonomyVersionCommand(version("a")));
            publishTaxonomy.handle(new PublishTaxonomyVersionCommand(LABEL));

            ImportTaxonomyVersion.Result result =
                    importTaxonomy.handle(new ImportTaxonomyVersionCommand(version("a")));

            assertThat(result.outcome()).isEqualTo(ImportTaxonomyVersion.Outcome.ALREADY_IMPORTED);
            assertThat(result.summary().status()).isEqualTo(TaxonomyStatus.PUBLISHED.name());
            assertThat(repository.insertCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("changed content under a published label is refused, protecting stored results")
        void refusesChangedContentUnderAPublishedLabel() {
            importTaxonomy.handle(new ImportTaxonomyVersionCommand(version("a")));
            publishTaxonomy.handle(new PublishTaxonomyVersionCommand(LABEL));

            assertThatThrownBy(() -> importTaxonomy.handle(new ImportTaxonomyVersionCommand(version("b"))))
                    .isInstanceOf(TaxonomyContentChangedException.class);
        }
    }

    private static TaxonomyVersion version(String checksumSeed) {
        return version(LABEL, checksumSeed);
    }

    private static TaxonomyVersion version(TaxonomyVersionLabel label, String checksumSeed) {
        return TaxonomyVersion.importedFrom(
                TaxonomyVersionId.of(UUID.nameUUIDFromBytes((label.value() + checksumSeed).getBytes())),
                label,
                ContentChecksum.of(checksumSeed.repeat(64).substring(0, 64)),
                NOW,
                List.of(Domain.of(
                        DomainCode.of("dsa"),
                        NodeName.of("Data Structures"),
                        List.of(Skill.of(
                                SkillCode.of("arrays"),
                                NodeName.of("Arrays"),
                                List.of(
                                        SubSkill.of(
                                                SubSkillCode.of("array-traversal"),
                                                NodeName.of("Array Traversal"),
                                                EffortHours.of(4),
                                                List.of()),
                                        SubSkill.of(
                                                SubSkillCode.of("two-pointers"),
                                                NodeName.of("Two Pointers"),
                                                EffortHours.of(6),
                                                List.of(SubSkillCode.of("array-traversal")))))))));
    }
}
