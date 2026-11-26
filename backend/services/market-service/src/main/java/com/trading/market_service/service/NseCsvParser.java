package com.trading.market_service.service;

import com.trading.market_service.exception.ImportFailedException;
import com.trading.market_service.instrument.ListedInstrument;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns NSE's two CSV files into instruments. Strict on what identifies a stock (a bad value rejects the whole
 * file), lenient on descriptive fields (a bad value becomes null).
 */
@Component
public class NseCsvParser {

    // Headers have stray leading spaces (" SERIES") and values may be padded, so both are trimmed
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreSurroundingSpaces(true)
            .setIgnoreEmptyLines(true)
            .get();

    private static final DateTimeFormatter LISTING_DATE = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()   // the file says 06-OCT-2008
            .appendPattern("dd-MMM-yyyy")
            .toFormatter(Locale.ENGLISH);

    private final int minEquityRows;
    private final int minNifty500Rows;

    public NseCsvParser(@Value("${market.import.min-equity-rows}") int minEquityRows,
                        @Value("${market.import.min-nifty500-rows}") int minNifty500Rows) {
        this.minEquityRows = minEquityRows;
        this.minNifty500Rows = minNifty500Rows;
    }

    /** Joins the equity list with the NIFTY 500 industries by ISIN. */
    public List<ListedInstrument> parse(String equityCsv, String nifty500Csv) {
        Map<String, String> industries = parseNifty500(nifty500Csv);

        List<CSVRecord> rows = read(equityCsv, "equity list", minEquityRows,
                "SYMBOL", "NAME OF COMPANY", "SERIES", "DATE OF LISTING", "MARKET LOT", "ISIN NUMBER", "FACE VALUE");

        List<ListedInstrument> result = new ArrayList<>(rows.size());
        Set<String> seen = new HashSet<>();
        for (CSVRecord row : rows) {
            String isin = required(row, "ISIN NUMBER", 12);
            if (isin.length() != 12 || !seen.add(isin)) {
                throw new ImportFailedException("Invalid or repeated ISIN at row " + row.getRecordNumber());
            }
            result.add(new ListedInstrument(
                    isin,
                    required(row, "SYMBOL", 32),
                    required(row, "NAME OF COMPANY", 200),
                    required(row, "SERIES", 4),
                    date(row.get("DATE OF LISTING")),
                    decimal(row.get("FACE VALUE")),
                    integer(row.get("MARKET LOT")),
                    industries.get(isin),
                    industries.containsKey(isin)));
        }
        return result;
    }

    private Map<String, String> parseNifty500(String csv) {
        Map<String, String> industryByIsin = new HashMap<>();
        for (CSVRecord row : read(csv, "NIFTY 500 list", minNifty500Rows, "ISIN Code", "Industry")) {
            industryByIsin.put(required(row, "ISIN Code", 12), required(row, "Industry", 100));
        }
        return industryByIsin;
    }

    /** Reads the whole file and checks its shape: expected columns, complete rows, enough of them. */
    private static List<CSVRecord> read(String csv, String file, int minRows, String... columns) {
        String text = csv.startsWith("﻿") ? csv.substring(1) : csv;   // byte order mark
        try (CSVParser parser = FORMAT.parse(new StringReader(text))) {
            for (String column : columns) {
                if (!parser.getHeaderMap().containsKey(column)) {
                    throw new ImportFailedException("The " + file + " has no column '" + column + "'");
                }
            }
            List<CSVRecord> rows = parser.getRecords();
            if (rows.stream().anyMatch(row -> !row.isConsistent())) {   // e.g. a download cut off mid-line
                throw new ImportFailedException("The " + file + " has incomplete rows");
            }
            if (rows.size() < minRows) {
                throw new ImportFailedException(
                        "The " + file + " has " + rows.size() + " rows, expected at least " + minRows);
            }
            return rows;
        } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
            throw new ImportFailedException("The " + file + " is not readable CSV", e);
        }
    }

    private static String required(CSVRecord row, String column, int maxLength) {
        String value = row.get(column);
        if (value.isEmpty() || value.length() > maxLength) {
            throw new ImportFailedException("Bad '" + column + "' at row " + row.getRecordNumber());
        }
        return value;
    }

    private static LocalDate date(String value) {
        try {
            return LocalDate.parse(value, LISTING_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer integer(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
