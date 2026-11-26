package com.trading.trading_service.security;

import com.trading.trading_service.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The service refuses unauthenticated calls by itself, even if the gateway were bypassed. */
@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired MockMvc mvc;

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk());
    }

    @Test
    void everythingElseNeedsAToken() throws Exception {
        mvc.perform(get("/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/x")).andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        mvc.perform(get("/orders").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }
}
