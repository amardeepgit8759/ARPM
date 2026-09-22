package com.placefy.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "taxonomy_versions")
public class TaxonomyVersionJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "label", nullable = false, updatable = false, length = 64, unique = true)
    private String label;

    /** The only mutable column in the whole taxonomy schema, and only DRAFT -> PUBLISHED. */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "source_checksum", nullable = false, updatable = false, length = 64)
    private String sourceChecksum;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private Instant importedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected TaxonomyVersionJpaEntity() {
        // for Hibernate
    }

    TaxonomyVersionJpaEntity(
            UUID id, String label, String status, String sourceChecksum, Instant importedAt, Instant publishedAt) {
        this.id = id;
        this.label = label;
        this.status = status;
        this.sourceChecksum = sourceChecksum;
        this.importedAt = importedAt;
        this.publishedAt = publishedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getStatus() {
        return status;
    }

    public String getSourceChecksum() {
        return sourceChecksum;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    void markPublished(Instant publishedAt) {
        this.status = "PUBLISHED";
        this.publishedAt = publishedAt;
    }
}
