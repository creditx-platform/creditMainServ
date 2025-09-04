package com.creditx.main.tracing;

import org.springframework.stereotype.Component;

import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Minimal helper to attach the business transactionId as a span tag so that
 * Zipkin queries (tagQuery: transactionId=123) can correlate asynchronous work
 * across services without scattering Tracer usage everywhere.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionSpanTagger {

    private final Tracer tracer;

    /**
     * Tag the current span with the provided transactionId (if a span exists).
     * Safe no-op when there is no active span or id is null.
     */
    public void tagTransactionId(Long transactionId) {
        if (transactionId == null) {
            return;
        }
        try {
            var span = tracer.currentSpan();
            if (span != null) {
                span.tag("transactionId", String.valueOf(transactionId));
                if (log.isDebugEnabled()) {
                    log.debug("Tagged span with transactionId={}", transactionId);
                }
            }
        } catch (Exception e) {
            // Swallow any tracing issues to avoid impacting business flow
            log.trace("Failed to tag span with transactionId {}: {}", transactionId, e.getMessage());
        }
    }
}
