package com.trading.api_gateway.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;

/**
 * "Who is this request from?" A rate limit gives each key its own token bucket in Redis.
 *
 * The Next.js server calls the gateway on behalf of browsers, so the connection's address is the same for every
 * user. X-Forwarded-For carries the browser's address, but anyone can type that header, so it is believed ONLY
 * when the request also carries the shared secret that just the BFF knows.
 */
@Configuration
public class RateLimitConfig {

    private final byte[] bffSecret;

    public RateLimitConfig(@Value("${app.bff.shared-secret:}") String bffSecret) {
        this.bffSecret = bffSecret.getBytes(StandardCharsets.UTF_8);
    }

    /** Normal API routes: a logged-in user gets their own bucket (JWT sub), anonymous traffic is bucketed by IP. */
    @Bean
    @Primary   // Spring Cloud Gateway injects one default KeyResolver; routes still choose theirs by name
    KeyResolver userOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .map(sub -> "user:" + sub)
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + clientIp(exchange)));
    }

    /** Auth routes: always by IP, because the caller is by definition not logged in yet. */
    @Bean
    KeyResolver ipKeyResolver() {
        return exchange -> Mono.just("ip:" + clientIp(exchange));
    }

    private String clientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank() && fromTrustedBff(exchange)) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
    }

    private boolean fromTrustedBff(ServerWebExchange exchange) {
        String presented = exchange.getRequest().getHeaders().getFirst("X-BFF-Secret");
        // Constant-time comparison so response timing does not leak the secret
        return bffSecret.length > 0 && presented != null
                && MessageDigest.isEqual(bffSecret, presented.getBytes(StandardCharsets.UTF_8));
    }
}
