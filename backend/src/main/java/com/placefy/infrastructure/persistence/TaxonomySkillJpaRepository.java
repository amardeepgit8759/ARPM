package com.placefy.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TaxonomySkillJpaRepository extends JpaRepository<TaxonomySkillJpaEntity, UUID> {

    List<TaxonomySkillJpaEntity> findByTaxonomyVersionIdOrderByOrdinalAsc(UUID taxonomyVersionId);
}
