package com.example.sheetsexport.service;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Petit utilitaire de retry avec backoff exponentiel + jitter, ciblé sur les
 * codes HTTP transitoires renvoyés par les APIs Google (429 / 500 / 503).
 */
@Component
public class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);
    private static final Set<Integer> RETRYABLE_STATUS = Set.of(429, 500, 503);

    private final int maxRetries;
    private final long initialBackoffMs;

    public RetryExecutor(
            @Value("${sheets-export.max-retries:4}") int maxRetries,
            @Value("${sheets-export.initial-backoff-ms:500}") long initialBackoffMs) {
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
    }

    @FunctionalInterface
    public interface GoogleCall<T> {
        T execute() throws IOException;
    }

    public <T> T execute(String operationName, GoogleCall<T> call) throws IOException {
        long backoff = initialBackoffMs;
        for (int attempt = 1; ; attempt++) {
            try {
                return call.execute();
            } catch (GoogleJsonResponseException e) {
                int status = e.getStatusCode();
                if (!RETRYABLE_STATUS.contains(status) || attempt > maxRetries) {
                    throw e;
                }
                long sleep = backoff + ThreadLocalRandom.current().nextLong(0, 250);
                log.warn("Google API '{}' -> HTTP {} (tentative {}/{}), retry dans {} ms",
                        operationName, status, attempt, maxRetries, sleep);
                sleep(sleep);
                backoff *= 2;
            }
        }
    }

    private void sleep(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Interruption pendant le backoff de retry", ie);
        }
    }
}
