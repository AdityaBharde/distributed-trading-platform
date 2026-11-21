package com.trading.identity_service.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data generates the SQL from the method names. Callers must pass an email already run
 * through {@link User#normalize(String)}; that keeps these queries on the unique index.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
