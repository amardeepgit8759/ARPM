package com.placefy.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Flat, with plain UUID columns instead of {@code @ManyToOne}/{@code @OneToMany} associations.
 *
 * <p>The adapter loads the four content tables with four ordered queries and assembles the tree
 * in Java. That is more explicit code than a cascading object graph, and in exchange there is no
 * lazy-loading behaviour to reason about, no N+1 to discover later, and the load order is
 * visible at the call site.
 */
@Entity
@Table(name = "taxonomy_domains")
public class TaxonomyDomainJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "taxonomy_version_id", nullable = false, updatable = false)
    private UUID taxonomyVersionId;

    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, updatable = false, length = 160)
    private String name;

    @Column(name = "ordinal", nullable = false, updatable = false)
    private int ordinal;

    protected TaxonomyDomainJpaEntity() {
        // for Hibernate
    }

    TaxonomyDomainJpaEntity(UUID id, UUID taxonomyVersionId, String code, String name, int ordinal) {
        this.id = id;
        this.taxonomyVersionId = taxonomyVersionId;
        this.code = code;
        this.name = name;
        this.ordinal = ordinal;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaxonomyVersionId() {
        return taxonomyVersionId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getOrdinal() {
        return ordinal;
    }
}
