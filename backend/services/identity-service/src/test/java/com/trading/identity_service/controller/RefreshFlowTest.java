package com.trading.identity_service.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.trading.identity_service.TestcontainersConfiguration;
import com.trading.identity_service.dto.LoginRequest;
import com.trading.identity_service.dto.RefreshRequest;
import com.trading.identity_service.dto.RegisterRequest;
import com.trading.identity_service.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** The refresh-token lifecycle: rotation, reuse detection, expiry, logout, and what the DB stores. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"DB_PASSWORD=unused-in-tests"})
class RefreshFlowTest {

    static final String PASSWORD = "correct horse battery";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    /** Registers a fresh user and logs in. Returns the login response. */
    private JsonNode newSession(String email) throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RegisterRequest(email, PASSWORD)))).andReturn();
        MvcResult login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, PASSWORD)))).andReturn();
        return json.readTree(login.getResponse().getContentAsString());
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private MvcResult refresh(String token) throws Exception {
        return mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RefreshRequest(token)))).andReturn();
    }

    private MvcResult logout(String token) throws Exception {
        return mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RefreshRequest(token)))).andReturn();
    }

    private JWTClaimsSet claims(String jwt) throws Exception {
        return SignedJWT.parse(jwt).getJWTClaimsSet();
    }

    @Test
    void loginReturnsVerifiableRefreshTokenValidFor30DaysAndStoresOnlyItsHash() throws Exception {
        JsonNode session = newSession(uniqueEmail());
        String refreshToken = session.get("refreshToken").asText();

        String jwksJson = mvc.perform(get("/.well-known/jwks.json")).andReturn().getResponse().getContentAsString();
        SignedJWT jwt = SignedJWT.parse(refreshToken);
        RSAKey key = (RSAKey) JWKSet.parse(jwksJson).getKeyByKeyId(jwt.getHeader().getKeyID());
        assertThat(jwt.verify(new RSASSAVerifier(key))).isTrue();

        JWTClaimsSet c = jwt.getJWTClaimsSet();
        assertThat(c.getStringClaim("token_use")).isEqualTo("refresh");
        assertThat(c.getStringClaim("sid")).isNotBlank();
        long lifetime = c.getExpirationTime().getTime() - c.getIssueTime().getTime();
        assertThat(lifetime).isBetween(Duration.ofDays(30).toMillis() - 2000, Duration.ofDays(30).toMillis() + 2000);
        assertThat(claims(session.get("accessToken").asText()).getStringClaim("token_use")).isEqualTo("access");

        // The database holds a hash, never the token.
        String storedHash = jdbc.queryForObject("select token_hash from refresh_tokens where id = ?",
                String.class, UUID.fromString(c.getJWTID()));
        assertThat(storedHash).hasSize(64).isNotEqualTo(refreshToken);
        assertThat(refreshToken).doesNotContain(storedHash);
    }

    @Test
    void refreshRotatesAndKeepsTheOriginalExpiry() throws Exception {
        JsonNode first = newSession(uniqueEmail());
        String t1 = first.get("refreshToken").asText();

        MvcResult result = refresh(t1);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode second = json.readTree(result.getResponse().getContentAsString());
        String t2 = second.get("refreshToken").asText();

        assertThat(t2).isNotEqualTo(t1);
        assertThat(second.get("accessToken").asText()).isNotBlank();
        assertThat(claims(second.get("accessToken").asText()).getStringClaim("token_use")).isEqualTo("access");
        // Same session, same absolute expiry: refreshing never extends the 30-day cap.
        assertThat(claims(t2).getStringClaim("sid")).isEqualTo(claims(t1).getStringClaim("sid"));
        assertThat(claims(t2).getExpirationTime()).isEqualTo(claims(t1).getExpirationTime());
        // The new token works too.
        assertThat(refresh(t2).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void reusingAnOldRefreshTokenRevokesTheWholeSession() throws Exception {
        String t1 = newSession(uniqueEmail()).get("refreshToken").asText();
        String t2 = json.readTree(refresh(t1).getResponse().getContentAsString()).get("refreshToken").asText();

        // Attacker (or buggy client) replays the already-used t1.
        assertThat(refresh(t1).getResponse().getStatus()).isEqualTo(401);

        // The legitimate newest token is now dead as well: the session was revoked.
        assertThat(refresh(t2).getResponse().getStatus()).isEqualTo(401);
        List<Boolean> revoked = jdbc.queryForList("select revoked_at is not null from refresh_tokens where session_id = ?",
                Boolean.class, UUID.fromString(claims(t1).getStringClaim("sid")));
        assertThat(revoked).isNotEmpty().allMatch(r -> r);
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        String t1 = newSession(uniqueEmail()).get("refreshToken").asText();
        jdbc.update("update refresh_tokens set expires_at = now() - interval '1 second' where id = ?",
                UUID.fromString(claims(t1).getJWTID()));

        assertThat(refresh(t1).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void accessTokenCannotBeUsedAsRefreshToken() throws Exception {
        JsonNode session = newSession(uniqueEmail());

        assertThat(refresh(session.get("accessToken").asText()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void garbageAndTamperedTokensAreRejectedIdentically() throws Exception {
        String t1 = newSession(uniqueEmail()).get("refreshToken").asText();
        String[] parts = t1.split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"" + UUID.randomUUID() + "\",\"token_use\":\"refresh\"}").getBytes());
        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

        MvcResult garbage = refresh("not-a-jwt");
        MvcResult forged = refresh(tampered);

        assertThat(garbage.getResponse().getStatus()).isEqualTo(401);
        assertThat(forged.getResponse().getStatus()).isEqualTo(401);
        assertThat(forged.getResponse().getContentAsString()).isEqualTo(garbage.getResponse().getContentAsString());
    }

    @Test
    void logoutRevokesTheSessionAndIsIdempotent() throws Exception {
        String t1 = newSession(uniqueEmail()).get("refreshToken").asText();

        assertThat(logout(t1).getResponse().getStatus()).isEqualTo(204);
        assertThat(refresh(t1).getResponse().getStatus()).isEqualTo(401);
        assertThat(logout(t1).getResponse().getStatus()).isEqualTo(204);          // again: still fine
        assertThat(logout("garbage").getResponse().getStatus()).isEqualTo(204);   // nothing to revoke: still fine
    }

    @Test
    void sessionsAreIndependent() throws Exception {
        String email = uniqueEmail();
        String laptop = newSession(email).get("refreshToken").asText();
        MvcResult phoneLogin = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, PASSWORD)))).andReturn();
        String phone = json.readTree(phoneLogin.getResponse().getContentAsString()).get("refreshToken").asText();

        logout(laptop);

        assertThat(refresh(laptop).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh(phone).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void disabledUserCannotRefresh() throws Exception {
        String email = uniqueEmail();
        String t1 = newSession(email).get("refreshToken").asText();
        var user = users.findByEmail(email).orElseThrow();
        user.setEnabled(false);
        users.save(user);

        assertThat(refresh(t1).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void missingTokenIsABadRequest() throws Exception {
        MvcResult result = mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }
}
