package com.placefy.application.fake;

import com.placefy.application.error.EmailAlreadyRegisteredException;
import com.placefy.application.port.out.UserRepository;
import com.placefy.domain.user.Email;
import com.placefy.domain.user.User;
import com.placefy.domain.user.UserId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Honours the same contract as the real adapter, including rejecting a duplicate email on save,
 * so a use-case test that passes here is not passing for a reason the database would reject.
 */
public class InMemoryUserRepository implements UserRepository {

    private final Map<UserId, User> byId = new LinkedHashMap<>();

    @Override
    public boolean existsByEmail(Email email) {
        return findByEmail(email).isPresent();
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return byId.values().stream().filter(user -> user.email().equals(email)).findFirst();
    }

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public User save(User user) {
        byId.values().stream()
                .filter(existing -> existing.email().equals(user.email()))
                .filter(existing -> !existing.id().equals(user.id()))
                .findAny()
                .ifPresent(clash -> {
                    throw new EmailAlreadyRegisteredException();
                });
        byId.put(user.id(), user);
        return user;
    }

    public int count() {
        return byId.size();
    }
}
