package com.trading.identity_service.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit test: no Spring, no database. Proves the properties we rely on. */
class PasswordHashingTest {

    private final PasswordEncoder encoder = new PasswordConfig().passwordEncoder();

    @Test
    void hashIsNotThePlainPassword() {
        String hash = encoder.encode("correct-horse-battery");

        assertThat(hash).doesNotContain("correct-horse-battery");
    }

    @Test
    void hashHasBcryptFormatWithConfiguredCost() {
        String hash = encoder.encode("correct-horse-battery");

        // $2a$ = algorithm version, 12 = cost, then 22 chars of salt + 31 chars of hash = 60 total
        assertThat(hash).hasSize(60).matches("^\\$2a\\$12\\$.{53}$");
    }

    @Test
    void samePasswordGivesDifferentHashesBecauseOfRandomSalt() {
        String first = encoder.encode("correct-horse-battery");
        String second = encoder.encode("correct-horse-battery");

        assertThat(first).isNotEqualTo(second);
        // ...yet both still verify, because the salt is stored inside the hash string itself
        assertThat(encoder.matches("correct-horse-battery", first)).isTrue();
        assertThat(encoder.matches("correct-horse-battery", second)).isTrue();
    }

    @Test
    void wrongPasswordDoesNotMatch() {
        String hash = encoder.encode("correct-horse-battery");

        assertThat(encoder.matches("Correct-horse-battery", hash)).isFalse();
        assertThat(encoder.matches("", hash)).isFalse();
    }
}
