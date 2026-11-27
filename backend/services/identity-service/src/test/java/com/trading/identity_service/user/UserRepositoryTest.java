package com.trading.identity_service.user;

import com.trading.identity_service.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository slice test against a real Postgres (Testcontainers). Flyway runs V1 first, so these tests
 * also check the migration itself, not just the Java mapping.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // use the container, not H2
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"DB_PASSWORD=unused-in-tests"})
class UserRepositoryTest {

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void savesUserAndFillsIdAndTimestamps() {
        User saved = users.saveAndFlush(new User("alice@example.com", "$2a$12$fakehash", Role.USER));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.isEnabled()).isTrue();
        assertThat(users.findByEmail("alice@example.com")).get()
                .extracting(User::getPasswordHash).isEqualTo("$2a$12$fakehash");
    }

    @Test
    void emailIsStoredTrimmedAndLowerCased() {
        User user = new User("  Alice@Example.COM ", "hash", Role.USER);

        assertThat(user.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void databaseRejectsDuplicateEmailIgnoringCase() {
        users.saveAndFlush(new User("alice@example.com", "hash", Role.USER));

        // Bypass the entity (which would normalise) to prove the DB index itself enforces it.
        assertThatThrownBy(() -> jdbc.update(
                "insert into users (id, email, password_hash) values (gen_random_uuid(), 'ALICE@example.com', 'h')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnknownRole() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into users (id, email, password_hash, role) values (gen_random_uuid(), 'b@example.com', 'h', 'HACKER')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void flywayAppliedV1() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success", Integer.class);

        assertThat(applied).isEqualTo(1);
    }
}
