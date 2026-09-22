package com.placefy.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "taxonomy_sub_skills")
public class TaxonomySubSkillJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "taxonomy_version_id", nullable = false, updatable = false)
    private UUID taxonomyVersionId;

    @Column(name = "taxonomy_skill_id", nullable = false, updatable = false)
    private UUID taxonomySkillId;

    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, updatable = false, length = 160)
    private String name;

    @Column(name = "default_effort_hours", nullable = false, updatable = false)
    private int defaultEffortHours;

    @Column(name = "ordinal", nullable = false, updatable = false)
    private int ordinal;

    protected TaxonomySubSkillJpaEntity() {
        // for Hibernate
    }

    TaxonomySubSkillJpaEntity(
            UUID id,
            UUID taxonomyVersionId,
            UUID taxonomySkillId,
            String code,
            String name,
            int defaultEffortHours,
            int ordinal) {
        this.id = id;
        this.taxonomyVersionId = taxonomyVersionId;
        this.taxonomySkillId = taxonomySkillId;
        this.code = code;
        this.name = name;
        this.defaultEffortHours = defaultEffortHours;
        this.ordinal = ordinal;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaxonomyVersionId() {
        return taxonomyVersionId;
    }

    public UUID getTaxonomySkillId() {
        return taxonomySkillId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getDefaultEffortHours() {
        return defaultEffortHours;
    }

    public int getOrdinal() {
        return ordinal;
    }
}
