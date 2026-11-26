package com.trading.api_gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/** X-Forwarded-For is only believed from the BFF that knows the shared secret; everyone else cannot spoof it. */
class ClientIpTest {

    private String key(String configuredSecret, String presentedSecret) {
        var request = MockServerHttpRequest.post("/auth/login")
                .remoteAddress(new InetSocketAddress("10.0.0.5", 5000))
                .header("X-Forwarded-For", "203.0.113.7");
        if (presentedSecret != null) {
            request.header("X-BFF-Secret", presentedSecret);
        }
        return new RateLimitConfig(configuredSecret).ipKeyResolver()
                .resolve(MockServerWebExchange.from(request.build())).block();
    }

    @Test
    void trustedBffMayForwardTheBrowserAddress() {
        assertThat(key("s3cret", "s3cret")).isEqualTo("ip:203.0.113.7");
    }

    @Test
    void callerWithoutTheSecretCannotSpoofItsAddress() {
        assertThat(key("s3cret", null)).isEqualTo("ip:10.0.0.5");
        assertThat(key("s3cret", "wrong")).isEqualTo("ip:10.0.0.5");
    }

    @Test
    void noConfiguredSecretMeansNobodyIsTrusted() {
        assertThat(key("", "")).isEqualTo("ip:10.0.0.5");
    }
}
