package com.xtrmetl.etl.service;

import java.util.Objects;

/**
 * Describes the stable HTTP response for an idempotency-protected ETL request.
 *
 * @param responseBody newline-delimited successful ETL response body
 * @param replayed {@code true} when the response came from the durable idempotency ledger
 */
public record EtlIdempotencyResult(String responseBody, boolean replayed) {

    /**
     * Creates an immutable idempotency result.
     *
     * @param responseBody newline-delimited successful ETL response body
     * @param replayed whether a prior successful request supplied the response
     */
    public EtlIdempotencyResult {
        Objects.requireNonNull(responseBody, "responseBody must not be null");
    }
}
