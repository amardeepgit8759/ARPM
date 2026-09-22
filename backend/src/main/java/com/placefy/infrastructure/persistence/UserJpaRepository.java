package com.placefy.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data plumbing, not a port. Only {@link UserRepositoryAdapter} calls it; the application
 * layer sees {@code UserRepository} and nothing else.
 */
interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
