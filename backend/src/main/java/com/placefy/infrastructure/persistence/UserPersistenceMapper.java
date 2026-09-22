package com.placefy.infrastructure.persistence;

import com.placefy.domain.user.Email;
import com.placefy.domain.user.FullName;
import com.placefy.domain.user.PasswordHash;
import com.placefy.domain.user.Role;
import com.placefy.domain.user.User;
import com.placefy.domain.user.UserId;

/**
 * Hand-written both ways. A reflective mapper would be shorter, but it would also let a new
 * domain field reach the database without anyone deciding how it should be stored.
 */
final class UserPersistenceMapper {

    private UserPersistenceMapper() {}

    static UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(
                user.id().value(),
                user.name().value(),
                user.email().value(),
                user.passwordHash().value(),
                user.role().name(),
                user.createdAt(),
                user.updatedAt());
    }

    static User toDomain(UserJpaEntity entity) {
        return User.rehydrate(
                UserId.of(entity.getId()),
                FullName.of(entity.getName()),
                new Email(entity.getEmail()),
                PasswordHash.of(entity.getPasswordHash()),
                Role.valueOf(entity.getRole()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
