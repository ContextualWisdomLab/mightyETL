package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers immutable cancellation-result validation and replay semantics.
 */
class EtlJobCancellationTest {

    @Test
    void acceptsOnlyCancelledOperatorSafeSnapshots() {
        EtlJobSnapshot cancelledSnapshot = snapshot(EtlJobStatus.CANCELLED);

        EtlJobCancellation first = new EtlJobCancellation(cancelledSnapshot, false);
        EtlJobCancellation replay = new EtlJobCancellation(cancelledSnapshot, true);

        assertEquals(cancelledSnapshot, first.snapshot());
        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertThrows(NullPointerException.class, () -> new EtlJobCancellation(null, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobCancellation(snapshot(EtlJobStatus.PENDING), false)
        );
    }

    private static EtlJobSnapshot snapshot(EtlJobStatus status) {
        return new EtlJobSnapshot(
                UUID.fromString("75f61ec2-4f96-49e4-bf8e-66e2b75fb175"),
                status,
                1,
                null,
                Instant.parse("2026-08-06T00:00:00Z"),
                Instant.parse("2026-08-06T00:00:01Z")
        );
    }
}
