package com.trading.identity_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.identity_service.TestcontainersConfiguration;
import com.trading.identity_service.dto.LoginRequest;
import com.trading.identity_service.dto.ProfileUpdateRequest;
import com.trading.identity_service.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"DB_PASSWORD=unused-in-tests", "eureka.client.enabled=false"})
class ProfileFlowTest {

    static final String PASSWORD = "correct horse battery";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String bearer(String email) throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RegisterRequest(email, PASSWORD))));
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, PASSWORD)))).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asText();
    }

    private String update(String theme, String exchange, String timezone) throws Exception {
        return json.writeValueAsString(new ProfileUpdateRequest(theme, exchange, timezone));
    }

    @Test
    void profileNeedsAToken() throws Exception {
        mvc.perform(get("/profile/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void firstReadCreatesDefaultsExactlyOnce() throws Exception {
        String email = uniqueEmail();
        String token = bearer(email);

        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/profile/me").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(email))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.theme").value("SYSTEM"))
                    .andExpect(jsonPath("$.defaultExchange").value("NSE"))
                    .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"));
        }
        Integer rows = jdbc.queryForObject(
                "select count(*) from user_profiles p join users u on u.id = p.user_id where u.email = ?", Integer.class, email);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void updateIsSavedAndOnlyAffectsTheCaller() throws Exception {
        String alice = bearer(uniqueEmail());
        String bob = bearer(uniqueEmail());

        mvc.perform(put("/profile/me").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                        .content(update("DARK", "NYSE", "America/New_York")))
                .andExpect(status().isOk());

        mvc.perform(get("/profile/me").header("Authorization", alice))
                .andExpect(jsonPath("$.theme").value("DARK"))
                .andExpect(jsonPath("$.defaultExchange").value("NYSE"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"));
        mvc.perform(get("/profile/me").header("Authorization", bob))
                .andExpect(jsonPath("$.theme").value("SYSTEM"));
    }

    @Test
    void invalidValuesAreRejected() throws Exception {
        String token = bearer(uniqueEmail());

        for (String body : new String[]{
                update("PURPLE", "NSE", "Asia/Kolkata"),
                update("DARK", "LSE", "Asia/Kolkata"),
                update("DARK", "NSE", "Mars/Olympus"),
                "{}"}) {
            mvc.perform(put("/profile/me").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }
}
