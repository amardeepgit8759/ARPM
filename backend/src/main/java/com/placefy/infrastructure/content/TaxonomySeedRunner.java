package com.placefy.infrastructure.content;

import com.placefy.application.port.in.ImportTaxonomyVersion;
import com.placefy.application.port.in.PublishTaxonomyVersion;
import com.placefy.application.port.in.TaxonomyVersionSummary;
import com.placefy.application.port.out.IdGenerator;
import com.placefy.application.port.out.TimeProvider;
import com.placefy.domain.taxonomy.InvalidTaxonomyException;
import com.placefy.domain.taxonomy.TaxonomyStatus;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import com.placefy.domain.taxonomy.TaxonomyVersionId;
import com.placefy.domain.taxonomy.TaxonomyVersionLabel;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Loads {@code content/taxonomy} into the database at startup.
 *
 * <p>Safe to run on every boot: importing a label that already exists with identical content is
 * a no-op, and importing one whose content has changed is refused outright rather than merged.
 *
 * <p>Failure is fatal by design. A deployment configured to seed but unable to — malformed
 * content, a missing directory — has no vocabulary, and every feature above it would fail later
 * in a way that looks like a different bug.
 */
@Component
@ConditionalOnProperty(prefix = "placefy.taxonomy.seed", name = "enabled", havingValue = "true")
class TaxonomySeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TaxonomySeedRunner.class);

    private final TaxonomySeedProperties properties;
    private final ImportTaxonomyVersion importTaxonomyVersion;
    private final PublishTaxonomyVersion publishTaxonomyVersion;
    private final IdGenerator ids;
    private final TimeProvider time;

    TaxonomySeedRunner(
            TaxonomySeedProperties properties,
            ImportTaxonomyVersion importTaxonomyVersion,
            PublishTaxonomyVersion publishTaxonomyVersion,
            IdGenerator ids,
            TimeProvider time) {
        this.properties = properties;
        this.importTaxonomyVersion = importTaxonomyVersion;
        this.publishTaxonomyVersion = publishTaxonomyVersion;
        this.ids = ids;
        this.time = time;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path contentRoot = Path.of(properties.path());
        log.info("Seeding taxonomy from {}", contentRoot.toAbsolutePath());

        TaxonomyVersion loaded;
        try {
            loaded = new TaxonomyContentLoader()
                    .load(contentRoot, TaxonomyVersionId.of(ids.newId()), time.now());
        } catch (InvalidTaxonomyException e) {
            // The exception message already lists every violation with its location.
            log.error("Taxonomy content is invalid; refusing to start.{}{}", System.lineSeparator(), e.getMessage());
            throw e;
        }

        ImportTaxonomyVersion.Result result =
                importTaxonomyVersion.handle(new ImportTaxonomyVersion.ImportTaxonomyVersionCommand(loaded));
        TaxonomyVersionSummary summary = result.summary();

        log.info(
                "Taxonomy '{}' {} ({} domains, {} skills, {} sub-skills)",
                summary.label(),
                result.outcome() == ImportTaxonomyVersion.Outcome.IMPORTED ? "imported" : "already present, unchanged",
                summary.domainCount(),
                summary.skillCount(),
                summary.subSkillCount());

        if (properties.publish() && TaxonomyStatus.DRAFT.name().equals(summary.status())) {
            TaxonomyVersionSummary published =
                    publishTaxonomyVersion.handle(new PublishTaxonomyVersion.PublishTaxonomyVersionCommand(
                            TaxonomyVersionLabel.of(summary.label())));
            log.info("Taxonomy '{}' published at {}", published.label(), published.publishedAt());
        }
    }
}
