package com.placefy.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TaxonomyVersionJpaRepository extends JpaRepository<TaxonomyVersionJpaEntity, UUID> {

    Optional<TaxonomyVersionJpaEntity> findByLabel(String label);

    boolean existsByLabel(String label);
}
