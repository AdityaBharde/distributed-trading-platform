package com.trading.api_gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Makes a route FAIL CLOSED when Redis is down.
 *
 * Spring's RequestRateLimiter fails OPEN: if Redis errors, it lets the request through. That is right for
 * ordinary API calls, but for login it would switch off brute-force protection exactly when an attacker
 * might have caused the outage. Put this filter BEFORE RequestRateLimiter on such routes: it pings Redis
 * and answers 503 if there is no answer within 500 ms.
 *
 * Used in a route as:  filters[0].name=RequireRedis
 */
@Component
public class RequireRedisGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private static final Duration PING_TIMEOUT = Duration.ofMillis(500);

    private final ReactiveStringRedisTemplate redis;

    public RequireRedisGatewayFilterFactory(ReactiveStringRedisTemplate redis) {
        super(Object.class);
        this.redis = redis;
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> redisIsUp()
                .flatMap(up -> up ? chain.filter(exchange) : reject(exchange));
    }

    private Mono<Boolean> redisIsUp() {
        return redis.execute(connection -> connection.ping())
                .next()
                .timeout(PING_TIMEOUT)
                .map(pong -> true)
                .onErrorReturn(false)
                .defaultIfEmpty(false);
    }

    private static Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return exchange.getResponse().setComplete();
    }
}
