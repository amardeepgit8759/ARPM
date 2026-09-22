package com.placefy.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One prerequisite edge.
 *
 * <p>Carries a surrogate id rather than a composite key of the two sub-skill ids. The natural
 * key is expressed as a unique index in the migration, which enforces it just as strictly while
 * leaving the JPA mapping a plain single-column identifier.
 */
@Entity
@Table(name = "taxonomy_sub_skill_prerequisites")
public class TaxonomySubSkillPrerequisiteJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "sub_skill_id", nullable = false, updatable = false)
    private UUID subSkillId;

    @Column(name = "prerequisite_sub_skill_id", nullable = false, updatable = false)
    private UUID prerequisiteSubSkillId;

    @Column(name = "ordinal", nullable = false, updatable = false)
    private int ordinal;

    protected TaxonomySubSkillPrerequisiteJpaEntity() {
        // for Hibernate
    }

    TaxonomySubSkillPrerequisiteJpaEntity(UUID id, UUID subSkillId, UUID prerequisiteSubSkillId, int ordinal) {
        this.id = id;
        this.subSkillId = subSkillId;
        this.prerequisiteSubSkillId = prerequisiteSubSkillId;
        this.ordinal = ordinal;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubSkillId() {
        return subSkillId;
    }

    public UUID getPrerequisiteSubSkillId() {
        return prerequisiteSubSkillId;
    }

    public int getOrdinal() {
        return ordinal;
    }
}
