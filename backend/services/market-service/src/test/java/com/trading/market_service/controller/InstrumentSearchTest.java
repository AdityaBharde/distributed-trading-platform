package com.trading.market_service.controller;

import com.trading.market_service.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Search and lookup against a real Postgres (pg_trgm included). Rows are rolled back after each test. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class InstrumentSearchTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @MockitoBean JwtDecoder jwtDecoder;

    @BeforeEach
    void stocks() {
        when(jwtDecoder.decode("user")).thenReturn(
                Jwt.withTokenValue("user").header("alg", "none").subject("someone").claim("role", "USER").build());
        jdbc.sql("DELETE FROM instruments").update();
        add("RELAXO", "Relaxo Footwears Limited", false, true);
        add("RELCHEMQ", "Reliance Chemotex Industries Limited", false, true);
        add("RELIANCE", "Reliance Industries Limited", true, true);
        add("TCS", "Tata Consultancy Services Limited", true, true);
        add("M&M", "Mahindra & Mahindra Limited", true, true);
        add("RELOLD", "Relold Limited", false, false);   // delisted
    }

    private void add(String symbol, String name, boolean nifty500, boolean active) {
        jdbc.sql("""
                        INSERT INTO instruments (exchange, isin, symbol, name, series, nifty500, active)
                        VALUES ('NSE', ?, ?, ?, 'EQ', ?, ?)
                        """)
                .params(String.format("%-12s", symbol).replace(' ', 'X').substring(0, 12), symbol, name,
                        nifty500, active)
                .update();
    }

    private ResultActions call(String url, Object... variables) throws Exception {
        return mvc.perform(get(url, variables).header("Authorization", "Bearer user"));
    }

    private ResultActions search(String q) throws Exception {
        return call("/instruments/search?q={q}", q).andExpect(status().isOk());
    }

    @Test
    void relPutsRelianceFirstAndHidesDelistedStocks() throws Exception {
        search("rel").andExpect(jsonPath("$[*].symbol", contains("RELIANCE", "RELAXO", "RELCHEMQ")));
    }

    @Test
    void exactSymbolBeatsEverythingElse() throws Exception {
        search("relaxo").andExpect(jsonPath("$[0].symbol").value("RELAXO"));
    }

    @Test
    void findsByNameAndDespiteATypo() throws Exception {
        search("tata").andExpect(jsonPath("$[0].symbol").value("TCS"));
        search("consultancy").andExpect(jsonPath("$[0].symbol").value("TCS"));
        search("relaince").andExpect(jsonPath("$[0].symbol").value("RELIANCE"));
    }

    @Test
    void wildcardCharactersAreTakenLiterally() throws Exception {
        search("%").andExpect(jsonPath("$", empty()));
        search("_").andExpect(jsonPath("$", empty()));
        search("m&m").andExpect(jsonPath("$[0].symbol").value("M&M"));
    }

    @Test
    void limitIsApplied() throws Exception {
        call("/instruments/search?q=rel&limit=2").andExpect(jsonPath("$", hasSize(2)));
        call("/instruments/search?q=rel&limit=0").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void badQueriesAre400() throws Exception {
        call("/instruments/search").andExpect(status().isBadRequest());
        call("/instruments/search?q={q}", "   ").andExpect(status().isBadRequest());
        call("/instruments/search?q={q}", "x".repeat(51)).andExpect(status().isBadRequest());
    }

    @Test
    void lookupBySymbol() throws Exception {
        call("/instruments/{symbol}", "reliance").andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Reliance Industries Limited"))
                .andExpect(jsonPath("$.nifty500").value(true));
        call("/instruments/{symbol}", "M&M").andExpect(status().isOk());
        call("/instruments/{symbol}", "RELOLD").andExpect(jsonPath("$.active").value(false));
        call("/instruments/{symbol}", "NOPE").andExpect(status().isNotFound());
    }

    @Test
    void bothEndpointsNeedAToken() throws Exception {
        mvc.perform(get("/instruments/search?q=rel")).andExpect(status().isUnauthorized());
        mvc.perform(get("/instruments/RELIANCE")).andExpect(status().isUnauthorized());
    }
}
