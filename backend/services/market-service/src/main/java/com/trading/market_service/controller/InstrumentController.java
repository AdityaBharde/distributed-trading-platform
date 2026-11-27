package com.trading.market_service.controller;

import com.trading.market_service.dto.InstrumentResponse;
import com.trading.market_service.exception.InstrumentNotFoundException;
import com.trading.market_service.instrument.InstrumentQueries;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only, for any signed-in user (SecurityConfig). Reached through the gateway as /market/instruments/... */
@RestController
@RequestMapping("/instruments")
public class InstrumentController {

    private static final int MAX_RESULTS = 20;

    private final InstrumentQueries instruments;

    public InstrumentController(InstrumentQueries instruments) {
        this.instruments = instruments;
    }

    @GetMapping("/search")
    public List<InstrumentResponse> search(@RequestParam @NotBlank @Size(max = 50) String q,
                                           @RequestParam(defaultValue = "10") int limit) {
        return instruments.search(q.strip(), Math.max(1, Math.min(limit, MAX_RESULTS)));
    }

    @GetMapping("/{symbol}")
    public InstrumentResponse bySymbol(@PathVariable String symbol) {
        return instruments.findBySymbol(symbol).orElseThrow(() -> new InstrumentNotFoundException(symbol));
    }
}
