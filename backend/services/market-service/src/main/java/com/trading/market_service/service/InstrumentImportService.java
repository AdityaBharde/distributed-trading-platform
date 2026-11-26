package com.trading.market_service.service;

import com.trading.market_service.exception.ImportInProgressException;
import com.trading.market_service.instrument.InstrumentRepository;
import com.trading.market_service.instrument.ListedInstrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Download, check, then write everything in one transaction: readers see the old list or the new one. */
@Service
public class InstrumentImportService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentImportService.class);
    private static final String EXCHANGE = "NSE";

    public record ImportResult(int listed, int added, int deactivated) {
    }

    private final NseFileClient files;
    private final NseCsvParser parser;
    private final InstrumentRepository repository;
    private final TransactionTemplate transaction;
    private final AtomicBoolean running = new AtomicBoolean();   // enough for ONE instance of the service

    public InstrumentImportService(NseFileClient files, NseCsvParser parser, InstrumentRepository repository,
                                   TransactionTemplate transaction) {
        this.files = files;
        this.parser = parser;
        this.repository = repository;
        this.transaction = transaction;
    }

    public ImportResult importNow() {
        if (!running.compareAndSet(false, true)) {
            throw new ImportInProgressException();
        }
        try {
            // Network and parsing happen BEFORE the transaction, so no database connection waits on NSE
            List<ListedInstrument> listed = parser.parse(files.equityList(), files.nifty500List());

            ImportResult result = transaction.execute(status -> {
                int before = repository.count(EXCHANGE);
                repository.upsertAll(EXCHANGE, listed);
                int added = repository.count(EXCHANGE) - before;
                int deactivated = repository.deactivateNotUpdated(EXCHANGE);
                return new ImportResult(listed.size(), added, deactivated);
            });
            log.info("Instrument import done: {}", result);
            return result;
        } finally {
            running.set(false);
        }
    }

    public boolean isOlderThan(Duration age) {
        return repository.lastUpdated(EXCHANGE)
                .map(last -> last.isBefore(OffsetDateTime.now().minus(age)))
                .orElse(true);
    }
}
