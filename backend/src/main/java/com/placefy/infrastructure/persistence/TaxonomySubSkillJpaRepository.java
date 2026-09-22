package com.placefy.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TaxonomySubSkillJpaRepository extends JpaRepository<TaxonomySubSkillJpaEntity, UUID> {

    List<TaxonomySubSkillJpaEntity> findByTaxonomyVersionIdOrderByOrdinalAsc(UUID taxonomyVersionId);
}
