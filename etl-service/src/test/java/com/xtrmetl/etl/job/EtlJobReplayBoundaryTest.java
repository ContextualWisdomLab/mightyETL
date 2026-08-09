package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Captures the first immutable replay-result contract on the repaired durable-job stack.
 */
class EtlJobReplayBoundaryTest {

    private static final UUID SOURCE_ID = UUID.fromString(
            "cf4f083f-8c90-4f34-a8b6-b53761de44ef"
    );

    @Test
    void replayResultRequiresIdentityAndStatusButPreservesCurrentState() {
        EtlJobReplay pending = new EtlJobReplay(SOURCE_ID, EtlJobStatus.PENDING, false);
        EtlJobReplay terminalReplay = new EtlJobReplay(
                SOURCE_ID,
                EtlJobStatus.SUCCEEDED,
                true
        );

        assertEquals(EtlJobStatus.PENDING, pending.jobStatus());
        assertEquals(EtlJobStatus.SUCCEEDED, terminalReplay.jobStatus());
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobReplay(null, EtlJobStatus.PENDING, false)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobReplay(SOURCE_ID, null, false)
        );
    }
}
