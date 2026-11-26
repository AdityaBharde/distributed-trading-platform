package com.trading.api_gateway;

import com.trading.api_gateway.filter.RequireRedisGatewayFilterFactory;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.core.ReactiveRedisCallback;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit test of the fail-closed guard: Redis down or silent means 503 and the request goes no further. */
class RequireRedisFilterTest {

    private final AtomicBoolean chainCalled = new AtomicBoolean(false);
    private final GatewayFilterChain chain = exchange -> {
        chainCalled.set(true);
        return Mono.empty();
    };

    @SuppressWarnings("unchecked")
    private MockServerWebExchange run(Flux<String> redisAnswer) {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(any(ReactiveRedisCallback.class))).thenReturn((Flux) redisAnswer);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/auth/login").build());
        new RequireRedisGatewayFilterFactory(redis).apply(new Object()).filter(exchange, chain).block();
        return exchange;
    }

    @Test
    void redisUpLetsTheRequestThrough() {
        MockServerWebExchange exchange = run(Flux.just("PONG"));

        assertThat(chainCalled).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void redisErrorRejectsWith503() {
        MockServerWebExchange exchange = run(Flux.error(new RedisConnectionFailureException("down")));

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void redisThatNeverAnswersRejectsWith503AfterTheTimeout() {
        MockServerWebExchange exchange = run(Flux.never());

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
