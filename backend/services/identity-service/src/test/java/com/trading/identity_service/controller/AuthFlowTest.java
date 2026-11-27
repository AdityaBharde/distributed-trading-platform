package com.trading.identity_service.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import com.trading.identity_service.TestcontainersConfiguration;
import com.trading.identity_service.dto.LoginRequest;
import com.trading.identity_service.dto.RegisterRequest;
import com.trading.identity_service.user.UserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end through the HTTP layer, the real service and a real Postgres. Each test uses its own email. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"DB_PASSWORD=unused-in-tests"})
class AuthFlowTest {

    static final String PASSWORD = "correct horse battery";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private MvcResult register(String email, String password) throws Exception {
        return mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RegisterRequest(email, password)))).andReturn();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, password)))).andReturn();
    }

    @Test
    void registerCreatesUserRoleAndNeverLeaksHash() throws Exception {
        String email = uniqueEmail();
        MvcResult result = register(email, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("email").asText()).isEqualTo(email);
        assertThat(body.get("role").asText()).isEqualTo("USER");
        assertThat(body.has("passwordHash")).isFalse();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("$2a$");

        // What is stored is a BCrypt hash, not the password.
        String stored = users.findByEmail(email).orElseThrow().getPasswordHash();
        assertThat(stored).startsWith("$2a$12$").isNotEqualTo(PASSWORD);
    }

    @Test
    void clientCannotChooseItsOwnRole() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void duplicateEmailIsRejectedIgnoringCase() throws Exception {
        String email = uniqueEmail();
        assertThat(register(email, PASSWORD).getResponse().getStatus()).isEqualTo(201);

        assertThat(register(email.toUpperCase(), PASSWORD).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void invalidInputIsRejectedWithFieldErrorsAndNoEcho() throws Exception {
        MvcResult shortPw = register(uniqueEmail(), "short");
        MvcResult badEmail = register("not-an-email", PASSWORD);
        MvcResult tooLongBytes = register(uniqueEmail(), "é".repeat(40));   // 40 chars but 80 bytes

        assertThat(shortPw.getResponse().getStatus()).isEqualTo(400);
        assertThat(shortPw.getResponse().getContentAsString()).contains("password");
        assertThat(shortPw.getResponse().getContentAsString()).doesNotContain("short\"");
        assertThat(badEmail.getResponse().getStatus()).isEqualTo(400);
        assertThat(tooLongBytes.getResponse().getStatus()).isEqualTo(400);
        assertThat(tooLongBytes.getResponse().getContentAsString()).contains("at most 72 bytes");
    }

    @Test
    void loginIssuesJwtVerifiableWithPublishedJwks() throws Exception {
        String email = uniqueEmail();
        String userId = json.readTree(register(email, PASSWORD).getResponse().getContentAsString()).get("id").asText();

        MvcResult result = login(email.toUpperCase(), PASSWORD);   // login is case-insensitive on email
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("expiresIn").asLong()).isEqualTo(900);

        // Act like the gateway: download the public key set, find the key by "kid", verify the signature.
        String jwksJson = mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        SignedJWT jwt = SignedJWT.parse(body.get("accessToken").asText());
        RSAKey key = (RSAKey) JWKSet.parse(jwksJson).getKeyByKeyId(jwt.getHeader().getKeyID());

        assertThat(jwt.verify(new RSASSAVerifier(key))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(userId);
        assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("USER");
        assertThat(jwt.getJWTClaimsSet().getExpirationTime()).isAfter(new java.util.Date());
    }

    @Test
    void unknownEmailAndWrongPasswordLookIdentical() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);

        MvcResult wrongPassword = login(email, "wrong password!!");
        MvcResult unknownEmail = login(uniqueEmail(), PASSWORD);

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());
    }

    @Test
    void disabledAccountCannotLogIn() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);
        var user = users.findByEmail(email).orElseThrow();
        user.setEnabled(false);
        users.save(user);

        assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(401);
    }
}
