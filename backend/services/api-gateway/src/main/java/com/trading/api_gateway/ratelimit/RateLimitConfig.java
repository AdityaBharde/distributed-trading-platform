package com.trading.api_gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.security.Principal;

/**
 * "Who is this request from?" A rate limit gives each key its own token bucket in Redis.
 *
 * Note for Step 8: when the Next.js server calls the gateway on behalf of browsers, the remote address is the
 * Next.js server for everyone. Trusting X-Forwarded-For from that one known proxy will be needed then.
 */
@Configuration
public class RateLimitConfig {

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

    private static String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
    }
}
