package com.xtrmetl.etl.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Defines the immutable successful idempotency result contract.
 */
class EtlIdempotencyResultTest {

    @Test
    void retainsTheResponseAndReplayFlag() {
        EtlIdempotencyResult result = new EtlIdempotencyResult("Processed: record_alpha", false);

        assertEquals("Processed: record_alpha", result.responseBody());
        assertFalse(result.replayed());
    }

    @Test
    void rejectsMissingResponseBody() {
        assertThrows(NullPointerException.class, () -> new EtlIdempotencyResult(null, true));
    }
}
