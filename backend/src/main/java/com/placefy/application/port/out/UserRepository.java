package com.placefy.application.port.out;

import com.placefy.domain.user.Email;
import com.placefy.domain.user.User;
import com.placefy.domain.user.UserId;
import java.util.Optional;

/**
 * Outbound port for user storage. Speaks only in domain types: the use cases never learn that
 * a row, an entity or a JPA session exists.
 */
public interface UserRepository {

    boolean existsByEmail(Email email);

    Optional<User> findByEmail(Email email);

    Optional<User> findById(UserId id);

    /**
     * Persists a user.
     *
     * @throws com.placefy.application.error.EmailAlreadyRegisteredException if the address is
     *     taken. The check-then-save in the register use case narrows the window, but only the
     *     unique index closes it, so adapters must translate that constraint violation here.
     */
    User save(User user);
}
