package com.placefy.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "taxonomy_skills")
public class TaxonomySkillJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "taxonomy_version_id", nullable = false, updatable = false)
    private UUID taxonomyVersionId;

    @Column(name = "taxonomy_domain_id", nullable = false, updatable = false)
    private UUID taxonomyDomainId;

    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, updatable = false, length = 160)
    private String name;

    @Column(name = "ordinal", nullable = false, updatable = false)
    private int ordinal;

    protected TaxonomySkillJpaEntity() {
        // for Hibernate
    }

    TaxonomySkillJpaEntity(
            UUID id, UUID taxonomyVersionId, UUID taxonomyDomainId, String code, String name, int ordinal) {
        this.id = id;
        this.taxonomyVersionId = taxonomyVersionId;
        this.taxonomyDomainId = taxonomyDomainId;
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

    public UUID getTaxonomyDomainId() {
        return taxonomyDomainId;
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
