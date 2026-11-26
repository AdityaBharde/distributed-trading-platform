package com.trading.api_gateway.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The PRODUCTION decoder (the one built from security.jwt.jwk-set-uri), pointed at a tiny server that publishes
 * a real JWKS over HTTP, exactly like identity-service does. Proves the key is downloaded and selected by "kid".
 */
class JwksDecoderTest {

    static final RSAKey PUBLISHED = key();
    static final RSAKey IMPOSTOR = key();

    static final DisposableServer JWKS_SERVER = HttpServer.create().port(0)
            .route(routes -> routes.get("/.well-known/jwks.json", (req, res) -> res
                    .header("Content-Type", "application/json")
                    .sendString(Mono.just(new JWKSet(PUBLISHED.toPublicJWK()).toString()))))
            .bindNow();

    @AfterAll
    static void stop() {
        JWKS_SERVER.disposeNow();
    }

    private static RSAKey key() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String token(RSAKey signer, String sub, String tokenUse) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("identity-service").subject(sub)
                .claim("role", "USER").claim("token_use", tokenUse)
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(600))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signer.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(signer));
        return jwt.serialize();
    }

    private ReactiveJwtDecoder decoder() {
        return new SecurityConfig().reactiveJwtDecoder(
                "http://localhost:" + JWKS_SERVER.port() + "/.well-known/jwks.json", "identity-service");
    }

    @Test
    void tokenSignedByThePublishedKeyIsAccepted() throws Exception {
        String sub = UUID.randomUUID().toString();

        Jwt jwt = decoder().decode(token(PUBLISHED, sub, "access")).block();

        assertThat(jwt).isNotNull();
        assertThat(jwt.getSubject()).isEqualTo(sub);
        assertThat(jwt.<String>getClaim("role")).isEqualTo("USER");
    }

    @Test
    void tokenSignedByAnyOtherKeyIsRejected() throws Exception {
        String forged = token(IMPOSTOR, UUID.randomUUID().toString(), "access");

        assertThrows(JwtException.class, () -> decoder().decode(forged).block());
    }

    @Test
    void refreshTokenIsRejectedEvenWithAValidSignature() throws Exception {
        String refresh = token(PUBLISHED, UUID.randomUUID().toString(), "refresh");

        assertThrows(JwtException.class, () -> decoder().decode(refresh).block());
    }
}
