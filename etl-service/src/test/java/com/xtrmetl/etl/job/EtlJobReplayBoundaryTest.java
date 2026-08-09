package com.xtrmetl.etl.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.service.EtlBatchProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Captures immutable replay-result and collaborator-construction contracts on the repaired stack.
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

    @Test
    void constructorsRejectMissingCollaborators() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ObjectMapper mapper = new ObjectMapper();
        EtlBatchProperties properties = new EtlBatchProperties();

        assertThrows(
                NullPointerException.class,
                () -> new EtlJobReplayService(null, mapper, properties, hash -> true)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobReplayService(jdbcTemplate, null, properties, hash -> true)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobReplayService(jdbcTemplate, mapper, null, hash -> true)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobReplayService(jdbcTemplate, mapper, properties, null)
        );
        new EtlJobReplayService(jdbcTemplate, mapper, properties);
    }
}
