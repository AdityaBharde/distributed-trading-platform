package com.trading.market_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

/** Starts imports on their own: after startup if the data is old, and every day. Off in tests. */
@Configuration
@EnableAsync
@EnableScheduling
@ConditionalOnProperty(name = "market.import.enabled", havingValue = "true", matchIfMissing = true)
public class InstrumentImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(InstrumentImportScheduler.class);
    private static final Duration MAX_AGE = Duration.ofHours(24);

    private final InstrumentImportService imports;

    public InstrumentImportScheduler(InstrumentImportService imports) {
        this.imports = imports;
    }

    /** @Async: a slow or unreachable NSE must not delay startup. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (imports.isOlderThan(MAX_AGE)) {
            run("startup");
        }
    }

    @Scheduled(cron = "${market.import.cron}", zone = "Asia/Kolkata")
    public void daily() {
        run("daily");
    }

    private void run(String trigger) {
        try {
            imports.importNow();
        } catch (RuntimeException e) {
            // The old data stays; the next trigger tries again
            log.error("Instrument import ({}) failed", trigger, e);
        }
    }
}
