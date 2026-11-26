package com.trading.market_service.service;

import com.trading.market_service.exception.ImportFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Downloads NSE's CSV files. Only fetches; checking the content is the parser's job. */
@Component
public class NseFileClient {

    private final RestClient http;
    private final String equityListUrl;
    private final String nifty500Url;

    public NseFileClient(RestClient.Builder builder,
                         @Value("${market.import.equity-list-url}") String equityListUrl,
                         @Value("${market.import.nifty500-url}") String nifty500Url,
                         @Value("${market.import.user-agent}") String userAgent) {
        // Without timeouts a stalled server would hang the import (and its "already running" flag) for ever
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.http = builder.requestFactory(factory).defaultHeader(HttpHeaders.USER_AGENT, userAgent).build();
        this.equityListUrl = equityListUrl;
        this.nifty500Url = nifty500Url;
    }

    public String equityList() {
        return download(equityListUrl);
    }

    public String nifty500List() {
        return download(nifty500Url);
    }

    private String download(String url) {
        try {
            byte[] body = http.get().uri(url).retrieve().body(byte[].class);
            if (body == null || body.length == 0) {
                throw new ImportFailedException("Empty response from " + url);
            }
            return new String(body, StandardCharsets.UTF_8);
        } catch (RestClientException e) {
            throw new ImportFailedException("Could not download " + url, e);
        }
    }
}
