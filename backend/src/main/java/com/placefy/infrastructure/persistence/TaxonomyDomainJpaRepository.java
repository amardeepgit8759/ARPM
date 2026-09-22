package com.placefy.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Ordered by {@code ordinal} explicitly. Declaration order is part of the content's meaning —
 * it is what a reader sees and what the validator reports against — so leaving it to whatever
 * order the database returns would make one stored taxonomy render differently between reads.
 */
interface TaxonomyDomainJpaRepository extends JpaRepository<TaxonomyDomainJpaEntity, UUID> {

    List<TaxonomyDomainJpaEntity> findByTaxonomyVersionIdOrderByOrdinalAsc(UUID taxonomyVersionId);
}
