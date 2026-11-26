package com.trading.api_gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.trading.api_gateway.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway's security and rate limiting end to end: real Spring Security chain, real Redis (Testcontainers),
 * a stub downstream server, and tokens signed with a throwaway key. Only the key SOURCE is swapped
 * (a fixed public key instead of downloading the JWKS); every other check is the production code.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "eureka.client.enabled=false")
@Import({TestcontainersConfiguration.class, GatewaySecurityTest.TestJwtConfig.class})
class GatewaySecurityTest {

    static final String ISSUER = "identity-service";
    static final RSAKey SIGNING_KEY = newKey();
    static final RSAKey OTHER_KEY = newKey();

    /** Pretend downstream service: answers with the Authorization header it received. */
    static final DisposableServer STUB = HttpServer.create().port(0)
            .handle((req, res) -> res.sendString(Mono.just(String.valueOf(req.requestHeaders().get("Authorization")))))
            .bindNow();

    @TestConfiguration
    static class TestJwtConfig {
        @Bean
        @Primary
        ReactiveJwtDecoder testJwtDecoder() throws Exception {
            NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(SIGNING_KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(SecurityConfig.accessTokenValidator(ISSUER));
            return decoder;
        }

        /**
         * Two extra routes to the stub, defined in Java (a property list from another source would REPLACE the
         * real routes instead of adding to them). "limited" gets a tiny bucket: 2 requests, 1 refill per second.
         */
        @Bean
        RouteLocator testRoutes(RouteLocatorBuilder builder, RedisRateLimiter rateLimiter, KeyResolver userOrIpKeyResolver) {
            String stub = "http://localhost:" + STUB.port();
            rateLimiter.getConfig().put("limited", new RedisRateLimiter.Config()
                    .setReplenishRate(1).setBurstCapacity(2).setRequestedTokens(1));
            return builder.routes()
                    .route("echo", r -> r.path("/echo/**").uri(stub))
                    .route("limited", r -> r.path("/limited/**")
                            .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(rateLimiter).setKeyResolver(userOrIpKeyResolver)))
                            .uri(stub))
                    .build();
        }
    }

    @LocalServerPort int port;

    /** Talks to the real running server over HTTP (a mock-bound client has no Host header, so CORS cannot be judged). */
    WebTestClient client;

    @BeforeEach
    void connect() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private static RSAKey newKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String token(RSAKey key, String sub, String role, String tokenUse, String issuer, Instant expires) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer).subject(sub).claim("role", role).claim("token_use", tokenUse)
                    .issueTime(new Date()).expirationTime(Date.from(expires)).jwtID(UUID.randomUUID().toString()).build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String userToken(String sub) {
        return token(SIGNING_KEY, sub, "USER", "access", ISSUER, Instant.now().plusSeconds(600));
    }

    @Test
    void requestWithoutTokenIsUnauthorized() {
        client.get().uri("/echo/x").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void validAccessTokenReachesDownstreamAndTheAuthorizationHeaderIsForwarded() {
        String token = userToken(UUID.randomUUID().toString());

        client.get().uri("/echo/x").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk()
                .expectBody(String.class).isEqualTo("Bearer " + token);   // services re-check it themselves
    }

    @Test
    void invalidTokensAreAllRejectedWith401() {
        String sub = UUID.randomUUID().toString();
        List<String> bad = List.of(
                token(SIGNING_KEY, sub, "USER", "access", ISSUER, Instant.now().minusSeconds(3600)),       // expired
                token(OTHER_KEY, sub, "USER", "access", ISSUER, Instant.now().plusSeconds(600)),            // forged: wrong key
                token(SIGNING_KEY, sub, "USER", "refresh", ISSUER, Instant.now().plusSeconds(600)),         // refresh token as bearer
                token(SIGNING_KEY, sub, "USER", "access", "someone-else", Instant.now().plusSeconds(600)),  // wrong issuer
                "garbage");

        for (String token : bad) {
            client.get().uri("/echo/x").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .exchange().expectStatus().isUnauthorized();
        }
    }

    @Test
    void adminAreaIs403ForUsersAndOpenForAdmins() {
        String user = userToken(UUID.randomUUID().toString());
        String admin = token(SIGNING_KEY, UUID.randomUUID().toString(), "ADMIN", "access", ISSUER, Instant.now().plusSeconds(600));

        client.get().uri("/admin/x").header(HttpHeaders.AUTHORIZATION, "Bearer " + user).exchange().expectStatus().isForbidden();
        // Passed security; the gateway just has no route for it yet
        client.get().uri("/admin/x").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).exchange().expectStatus().isNotFound();
    }

    @Test
    void marketAdminPathsNeedTheAdminRole() {
        String user = userToken(UUID.randomUUID().toString());

        client.post().uri("/market/admin/instruments/import").header(HttpHeaders.AUTHORIZATION, "Bearer " + user)
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void corsPreflightIsAnsweredWithoutATokenAndErrorsStillCarryCorsHeaders() {
        client.options().uri("/echo/x")
                .header("Origin", "http://localhost:3000").header("Access-Control-Request-Method", "GET")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000");

        // A 401 without CORS headers would show up in the browser as a confusing "CORS error"
        client.get().uri("/echo/x").header("Origin", "http://localhost:3000")
                .exchange().expectStatus().isUnauthorized()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000");
    }

    @Test
    void rateLimitIsPerUserAndAnswers429() {
        String alice = userToken("alice-" + UUID.randomUUID());
        String bob = userToken("bob-" + UUID.randomUUID());

        client.get().uri("/limited/x").header(HttpHeaders.AUTHORIZATION, "Bearer " + alice).exchange().expectStatus().isOk();
        client.get().uri("/limited/x").header(HttpHeaders.AUTHORIZATION, "Bearer " + alice).exchange().expectStatus().isOk();
        client.get().uri("/limited/x").header(HttpHeaders.AUTHORIZATION, "Bearer " + alice).exchange()
                .expectStatus().isEqualTo(429);

        // Alice's bucket is empty; Bob has his own
        client.get().uri("/limited/x").header(HttpHeaders.AUTHORIZATION, "Bearer " + bob).exchange().expectStatus().isOk();
    }

    @Test
    void authEndpointsArePublicButStrictlyRateLimitedPerIp() {
        // No token needed. With no identity-service running the route fails (503), but never 401/403.
        for (int i = 1; i <= 5; i++) {
            int status = client.post().uri("/auth/login").exchange().returnResult(String.class).getStatus().value();
            assertThat(status).as("attempt %d", i).isNotIn(401, 403, 429);
        }
        // Sixth attempt inside the same minute: the bucket (burst of 5) is empty
        client.post().uri("/auth/login").exchange().expectStatus().isEqualTo(429);
    }
}
