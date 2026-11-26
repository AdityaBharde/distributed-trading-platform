package com.trading.identity_service.service;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import com.trading.identity_service.user.Role;
import com.trading.identity_service.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit test (no Spring, no DB): proves the token is really signed with the key we publish. */
class JwtServiceTest {

    JwtService jwtService;
    User user;

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new JwtService("identity-service", Duration.ofMinutes(15));
        user = new User("Ada@Example.com", "hash", Role.ADMIN);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());   // normally assigned by the DB layer
    }

    private RSAKey publishedKey() throws Exception {
        JWKSet set = JWKSet.parse(com.nimbusds.jose.util.JSONObjectUtils.toJSONString(jwtService.publicJwks()));
        return (RSAKey) set.getKeys().get(0);
    }

    @Test
    void tokenVerifiesWithPublishedPublicKeyAndCarriesClaims() throws Exception {
        SignedJWT jwt = SignedJWT.parse(jwtService.issueAccessToken(user));

        assertThat(jwt.verify(new RSASSAVerifier(publishedKey()))).isTrue();
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(publishedKey().getKeyID());
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("ADMIN");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("identity-service");
        long lifetime = jwt.getJWTClaimsSet().getExpirationTime().getTime()
                - jwt.getJWTClaimsSet().getIssueTime().getTime();
        assertThat(lifetime).isEqualTo(Duration.ofMinutes(15).toMillis());
    }

    @Test
    void tamperedPayloadFailsVerification() throws Exception {
        String[] parts = jwtService.issueAccessToken(user).split("\\.");
        // Swap in a different payload (role forged to ADMIN for someone else) but keep the old signature.
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"" + UUID.randomUUID() + "\",\"role\":\"ADMIN\"}").getBytes());
        SignedJWT forged = SignedJWT.parse(parts[0] + "." + forgedPayload + "." + parts[2]);

        assertThat(forged.verify(new RSASSAVerifier(publishedKey()))).isFalse();
    }

    @Test
    void anotherServiceInstanceKeyCannotVerify() throws Exception {
        JwtService other = new JwtService("identity-service", Duration.ofMinutes(15));
        SignedJWT jwt = SignedJWT.parse(other.issueAccessToken(user));

        assertThat(jwt.verify(new RSASSAVerifier(publishedKey()))).isFalse();
    }

    @Test
    void refreshTokenRoundTripsAndAccessTokenIsRejectedAsRefresh() {
        UUID sid = UUID.randomUUID();
        UUID jti = UUID.randomUUID();
        String refresh = jwtService.issueRefreshToken(user.getId(), sid, jti, java.time.Instant.now().plus(Duration.ofDays(30)));

        JwtService.RefreshClaims claims = jwtService.parseRefreshToken(refresh);
        assertThat(claims.userId()).isEqualTo(user.getId());
        assertThat(claims.sessionId()).isEqualTo(sid);
        assertThat(claims.tokenId()).isEqualTo(jti);

        org.junit.jupiter.api.Assertions.assertThrows(com.trading.identity_service.exception.InvalidRefreshTokenException.class,
                () -> jwtService.parseRefreshToken(jwtService.issueAccessToken(user)));
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        String expired = jwtService.issueRefreshToken(user.getId(), UUID.randomUUID(), UUID.randomUUID(),
                java.time.Instant.now().minusSeconds(5));

        org.junit.jupiter.api.Assertions.assertThrows(com.trading.identity_service.exception.InvalidRefreshTokenException.class,
                () -> jwtService.parseRefreshToken(expired));
    }

    @Test
    void publishedJwksNeverContainsPrivateKeyMaterial() {
        String json = com.nimbusds.jose.util.JSONObjectUtils.toJSONString(jwtService.publicJwks());

        assertThat(json).contains("\"kty\":\"RSA\"").contains("\"n\"").contains("\"e\"");
        assertThat(json).doesNotContain("\"d\"").doesNotContain("\"p\"").doesNotContain("\"q\"");
    }
}
