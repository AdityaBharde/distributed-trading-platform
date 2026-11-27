package com.trading.identity_service.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.identity_service.TestcontainersConfiguration;
import com.trading.identity_service.dto.LoginRequest;
import com.trading.identity_service.dto.RefreshRequest;
import com.trading.identity_service.dto.RegisterRequest;
import com.trading.identity_service.user.Role;
import com.trading.identity_service.user.User;
import com.trading.identity_service.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Who may call what: public endpoints, bearer-token endpoints, role checks and "log out everywhere". */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"DB_PASSWORD=unused-in-tests"})
class AccessControlTest {

    static final String PASSWORD = "correct horse battery";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private JsonNode login(String email) throws Exception {
        MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, PASSWORD)))).andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }

    private JsonNode registerAndLogin(String email) throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RegisterRequest(email, PASSWORD)))).andReturn();
        return login(email);
    }

    private int refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RefreshRequest(refreshToken)))).andReturn().getResponse().getStatus();
    }

    private int logoutAll(String bearer) throws Exception {
        var request = post("/auth/logout-all");
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    void protectedEndpointNeedsAToken() throws Exception {
        assertThat(logoutAll(null)).isEqualTo(401);
        assertThat(logoutAll("garbage")).isEqualTo(401);
    }

    @Test
    void refreshTokenIsNotAcceptedAsABearerToken() throws Exception {
        JsonNode session = registerAndLogin(uniqueEmail());

        assertThat(logoutAll(session.get("refreshToken").asText())).isEqualTo(401);
    }

    @Test
    void publicEndpointsNeedNoToken() throws Exception {
        assertThat(mvc.perform(get("/.well-known/jwks.json")).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get("/health")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void adminAreaIsForbiddenToUsersButOpenToAdmins() throws Exception {
        String userToken = registerAndLogin(uniqueEmail()).get("accessToken").asText();
        String adminEmail = uniqueEmail();
        users.save(new User(adminEmail, encoder.encode(PASSWORD), Role.ADMIN));
        String adminToken = login(adminEmail).get("accessToken").asText();

        int asUser = mvc.perform(get("/admin/anything").header("Authorization", "Bearer " + userToken))
                .andReturn().getResponse().getStatus();
        int asAdmin = mvc.perform(get("/admin/anything").header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getStatus();

        assertThat(asUser).isEqualTo(403);    // authenticated but not allowed
        assertThat(asAdmin).isEqualTo(404);   // passed security; there is simply no such page yet
    }

    @Test
    void logoutAllEndsEverySessionOfTheCallerAndNobodyElses() throws Exception {
        String email = uniqueEmail();
        JsonNode laptop = registerAndLogin(email);
        JsonNode phone = login(email);
        JsonNode stranger = registerAndLogin(uniqueEmail());

        assertThat(logoutAll(laptop.get("accessToken").asText())).isEqualTo(204);

        assertThat(refresh(laptop.get("refreshToken").asText())).isEqualTo(401);
        assertThat(refresh(phone.get("refreshToken").asText())).isEqualTo(401);
        assertThat(refresh(stranger.get("refreshToken").asText())).isEqualTo(200);
        assertThat(logoutAll(laptop.get("accessToken").asText())).isEqualTo(204);   // idempotent; access token still valid until exp
    }
}
