package com.placefy.infrastructure.persistence;

import com.placefy.application.error.EmailAlreadyRegisteredException;
import com.placefy.application.port.out.UserRepository;
import com.placefy.domain.user.Email;
import com.placefy.domain.user.Role;
import com.placefy.domain.user.User;
import com.placefy.domain.user.UserId;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class UserRepositoryAdapter implements UserRepository {

    /** Must match the index name in V1__create_users_and_refresh_tokens.sql. */
    private static final String EMAIL_UNIQUE_INDEX = "users_email_key";

    private final UserJpaRepository jpa;

    UserRepositoryAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(Email email) {
        return jpa.existsByEmail(email.value());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(Email email) {
        return jpa.findByEmail(email.value()).map(UserPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UserId id) {
        return jpa.findById(id.value()).map(UserPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public User save(User user) {
        try {
            // saveAndFlush, not save: the constraint violation has to surface here, inside this
            // try, rather than at commit time where it would escape as an opaque 500.
            jpa.saveAndFlush(UserPersistenceMapper.toEntity(user));
            return user;
        } catch (DataIntegrityViolationException e) {
            if (violatesEmailUniqueIndex(e)) {
                throw new EmailAlreadyRegisteredException();
            }
            throw e;
        }
    }

    /**
     * Matches on the constraint name so that a future unique index on this table does not get
     * silently reported to the user as "email already registered".
     */
    @Override
    @Transactional
    public boolean deleteById(UserId id) {
        if (!jpa.existsById(id.value())) {
            return false;
        }
        // refresh_tokens carries ON DELETE CASCADE, so a user's sessions go with them. Any future
        // user-owned table must do the same, or deletion will start failing on a foreign key
        // rather than silently leaving orphaned rows behind.
        jpa.deleteById(id.value());
        jpa.flush();
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public long countByRole(Role role) {
        return jpa.countByRole(role.name());
    }

    private boolean violatesEmailUniqueIndex(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return EMAIL_UNIQUE_INDEX.equalsIgnoreCase(violation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }
}
