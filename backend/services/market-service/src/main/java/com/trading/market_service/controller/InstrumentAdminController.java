package com.trading.market_service.controller;

import com.trading.market_service.service.InstrumentImportService;
import com.trading.market_service.service.InstrumentImportService.ImportResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN only: SecurityConfig protects /admin/** here, and the gateway protects /market/admin/**. */
@RestController
public class InstrumentAdminController {

    private final InstrumentImportService imports;

    public InstrumentAdminController(InstrumentImportService imports) {
        this.imports = imports;
    }

    @PostMapping("/admin/instruments/import")
    public ImportResult importInstruments() {
        return imports.importNow();
    }
}
