package com.trading.identity_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.identity_service.TestcontainersConfiguration;
import com.trading.identity_service.dto.LoginRequest;
import com.trading.identity_service.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Per-account lockout, backed by a real Redis. Lock is 3 s here (15 min in production) so expiry is testable. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"DB_PASSWORD=unused-in-tests", "login.lock-duration=3s"})
class LoginLockoutTest {

    static final String PASSWORD = "correct horse battery";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String registered() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RegisterRequest(email, PASSWORD)))).andReturn();
        return email;
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, password)))).andReturn();
    }

    private void fail(String email, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            assertThat(login(email, "wrong password!!").getResponse().getStatus()).isEqualTo(401);
        }
    }

    @Test
    void fiveFailuresLockTheAccountEvenForTheCorrectPassword() throws Exception {
        String email = registered();
        fail(email, 5);

        MvcResult locked = login(email, PASSWORD);

        assertThat(locked.getResponse().getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(locked.getResponse().getHeader("Retry-After"))).isBetween(1L, 3L);
    }

    @Test
    void lockExpires() throws Exception {
        String email = registered();
        fail(email, 5);
        assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(429);

        Thread.sleep(3300);

        assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void otherAccountsAreNotAffected() throws Exception {
        String victim = registered();
        String bystander = registered();
        fail(victim, 5);

        assertThat(login(bystander, PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void unknownEmailsLockTooSoTheLockoutRevealsNothing() throws Exception {
        String existing = registered();
        String ghost = "ghost-" + UUID.randomUUID() + "@example.com";
        fail(existing, 5);
        fail(ghost, 5);

        MvcResult lockedExisting = login(existing, PASSWORD);
        MvcResult lockedGhost = login(ghost, PASSWORD);

        assertThat(lockedGhost.getResponse().getStatus()).isEqualTo(429);
        assertThat(lockedGhost.getResponse().getStatus()).isEqualTo(lockedExisting.getResponse().getStatus());
        assertThat(lockedGhost.getResponse().getContentAsString()).isEqualTo(lockedExisting.getResponse().getContentAsString());
    }

    @Test
    void successfulLoginResetsTheFailureCounter() throws Exception {
        String email = registered();
        fail(email, 4);
        assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(200);

        fail(email, 4);   // would be the 8th failure overall; the counter restarted so still not locked

        assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }
}
