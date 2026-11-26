package com.trading.market_service.service;

import com.trading.market_service.TestcontainersConfiguration;
import com.trading.market_service.exception.ImportFailedException;
import com.trading.market_service.service.InstrumentImportService.ImportResult;
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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Import against a real Postgres with the download faked. Deliberately NOT @Transactional: the delisting rule
 * depends on each import being its own transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InstrumentImportTest {

    private static final String HEADER =
            "SYMBOL,NAME OF COMPANY, SERIES, DATE OF LISTING, PAID UP VALUE, MARKET LOT, ISIN NUMBER, FACE VALUE\n";
    private static final String RELIANCE = row("RELIANCE", "INE002A01018");
    private static final String TCS = row("TCS", "INE467B01029");
    private static final String NIFTY =
            "Company Name,Industry,Symbol,Series,ISIN Code\nReliance,Energy,RELIANCE,EQ,INE002A01018\n";

    @Autowired InstrumentImportService imports;
    @Autowired JdbcClient jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean NseFileClient files;
    @MockitoBean JwtDecoder jwtDecoder;

    @BeforeEach
    void emptyTable() {
        jdbc.sql("TRUNCATE instruments").update();
        when(files.nifty500List()).thenReturn(NIFTY);
    }

    private static String row(String symbol, String isin) {
        return symbol + "," + symbol + " Limited,EQ,29-NOV-1995,10,1," + isin + ",10\n";
    }

    private ImportResult importRows(String... rows) {
        when(files.equityList()).thenReturn(HEADER + String.join("", rows));
        return imports.importNow();
    }

    private long idOf(String symbol) {
        return jdbc.sql("SELECT id FROM instruments WHERE symbol = ?").param(symbol).query(Long.class).single();
    }

    private boolean isActive(String symbol) {
        return jdbc.sql("SELECT active FROM instruments WHERE symbol = ?").param(symbol).query(Boolean.class).single();
    }

    @Test
    void firstImportInsertsAndASecondOneChangesNothing() {
        assertThat(imports.isOlderThan(Duration.ofHours(24))).isTrue();

        assertThat(importRows(RELIANCE, TCS)).isEqualTo(new ImportResult(2, 2, 0));
        long id = idOf("RELIANCE");
        assertThat(importRows(RELIANCE, TCS)).isEqualTo(new ImportResult(2, 0, 0));

        assertThat(idOf("RELIANCE")).isEqualTo(id);
        assertThat(imports.isOlderThan(Duration.ofHours(24))).isFalse();
    }

    @Test
    void renamedStockKeepsItsRow() {
        importRows(RELIANCE);
        long id = idOf("RELIANCE");

        importRows(row("RIL", "INE002A01018"));

        assertThat(idOf("RIL")).isEqualTo(id);
    }

    @Test
    void missingStockIsSwitchedOffAndComesBackWhenRelisted() {
        importRows(RELIANCE, TCS);

        assertThat(importRows(RELIANCE)).isEqualTo(new ImportResult(1, 0, 1));
        assertThat(isActive("TCS")).isFalse();

        importRows(RELIANCE, TCS);
        assertThat(isActive("TCS")).isTrue();
    }

    @Test
    void badFileLeavesTheDataAlone() {
        importRows(RELIANCE, TCS);

        assertThatThrownBy(() -> importRows(RELIANCE, "TCS,cut off")).isInstanceOf(ImportFailedException.class);

        assertThat(isActive("TCS")).isTrue();
        assertThat(importRows(RELIANCE, TCS).listed()).isEqualTo(2);   // the "running" flag was released
    }

    @Test
    void onlyAdminsCanTriggerAnImport() throws Exception {
        when(jwtDecoder.decode("user")).thenReturn(token("USER"));
        when(jwtDecoder.decode("admin")).thenReturn(token("ADMIN"));
        when(files.equityList()).thenReturn(HEADER + RELIANCE);

        mvc.perform(post("/admin/instruments/import")).andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/instruments/import").header("Authorization", "Bearer user"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/instruments/import").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(1));

        when(files.equityList()).thenReturn("<html>blocked</html>");
        mvc.perform(post("/admin/instruments/import").header("Authorization", "Bearer admin"))
                .andExpect(status().isBadGateway());
    }

    private static Jwt token(String role) {
        return Jwt.withTokenValue(role).header("alg", "none").subject("someone").claim("role", role).build();
    }
}
