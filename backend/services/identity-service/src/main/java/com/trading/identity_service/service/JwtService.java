package com.trading.identity_service.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.trading.identity_service.exception.InvalidRefreshTokenException;
import com.trading.identity_service.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Creates signed access tokens (RS256) and exposes the matching PUBLIC key as a JWKS.
 *
 * The private key never leaves this object. Verifiers (the gateway, Step 5) download the public key
 * from /.well-known/jwks.json and can check signatures, but cannot create tokens.
 *
 * Dev simplification: the key pair is generated at startup, so restarting the service invalidates
 * all issued tokens and two instances would disagree. Loading a persistent key (file/secret) is the
 * production fix; the "kid" header already supports rotating between keys.
 */
@Service
public class JwtService {

    private final RSAKey rsaKey;
    private final RSASSASigner signer;
    private final RSASSAVerifier verifier;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtService(@Value("${jwt.issuer}") String issuer,
                      @Value("${jwt.access-token-ttl}") Duration accessTokenTtl) throws JOSEException {
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
        this.rsaKey = new RSAKeyGenerator(2048)
                .keyID(UUID.randomUUID().toString())   // "kid": tells verifiers which key signed a token
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .generate();
        this.signer = new RSASSASigner(rsaKey);
        this.verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());
    }

    /** What we read back from a verified refresh token. */
    public record RefreshClaims(UUID userId, UUID sessionId, UUID tokenId) {
    }

    /** Builds header.payload and signs it. Claims: sub = user id, role, iss, iat, exp, jti. */
    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("token_use", "access")   // a refresh token can never pass as an access token
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(accessTokenTtl)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        return sign(claims);
    }

    /** Refresh token: no role, carries the session id (sid) and a unique jti that is its DB row id. */
    public String issueRefreshToken(UUID userId, UUID sessionId, UUID tokenId, Instant expiresAt) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("sid", sessionId.toString())
                .claim("token_use", "refresh")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(expiresAt))
                .jwtID(tokenId.toString())
                .build();
        return sign(claims);
    }

    /**
     * Verifies signature, issuer, token_use and expiry. Anything wrong (garbage, tampered, wrong kind,
     * expired) gives the same exception so callers cannot tell why.
     */
    public RefreshClaims parseRefreshToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
                throw new InvalidRefreshTokenException();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!issuer.equals(claims.getIssuer())
                    || !"refresh".equals(claims.getStringClaim("token_use"))
                    || claims.getExpirationTime() == null
                    || !claims.getExpirationTime().after(new Date())) {
                throw new InvalidRefreshTokenException();
            }
            return new RefreshClaims(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.getStringClaim("sid")),
                    UUID.fromString(claims.getJWTID()));
        } catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException e) {
            throw new InvalidRefreshTokenException();
        }
    }

    private String sign(JWTClaimsSet claims) {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaKey.getKeyID())
                .type(JOSEObjectType.JWT)
                .build();
        try {
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign token", e);
        }
    }

    /** Lets this service verify its own access tokens in-process (no HTTP call to its own JWKS endpoint). */
    public java.security.interfaces.RSAPublicKey publicKey() {
        try {
            return rsaKey.toRSAPublicKey();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not read public key", e);
        }
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }

    /** Public half only: toPublicJWK() drops the private exponent. */
    public Map<String, Object> publicJwks() {
        return new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
    }
}
