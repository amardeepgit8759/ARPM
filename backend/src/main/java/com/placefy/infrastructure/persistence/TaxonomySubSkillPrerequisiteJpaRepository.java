package com.placefy.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TaxonomySubSkillPrerequisiteJpaRepository
        extends JpaRepository<TaxonomySubSkillPrerequisiteJpaEntity, UUID> {

    List<TaxonomySubSkillPrerequisiteJpaEntity> findBySubSkillIdInOrderByOrdinalAsc(Collection<UUID> subSkillIds);
}
