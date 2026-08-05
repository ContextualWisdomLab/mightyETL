package com.xtrmetl.etl.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies deterministic lowercase SHA-256 text identities used by durable ETL persistence.
 */
class Sha256DigestTest {

    @Test
    void producesThePublishedSha256Vector() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Sha256Digest.digest("abc")
        );
    }

    @Test
    void rejectsMissingInput() {
        assertThrows(NullPointerException.class, () -> Sha256Digest.digest(null));
    }
}
