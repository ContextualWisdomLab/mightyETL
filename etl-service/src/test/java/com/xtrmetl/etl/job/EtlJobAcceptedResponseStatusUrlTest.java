package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that accepted durable-job representations expose only valid origin-relative status URLs.
 */
class EtlJobAcceptedResponseStatusUrlTest {

    private static final UUID JOB_RECORD_ID = UUID.fromString(
            "cf4f083f-8c90-4f34-a8b6-b53761de44ef"
    );

    @Test
    void retainsValidOriginRelativeStatusUrl() {
        String statusUrl = "/api/etl/jobs/" + JOB_RECORD_ID;

        EtlJobAcceptedResponse response = new EtlJobAcceptedResponse(
                JOB_RECORD_ID,
                EtlJobStatus.PENDING,
                statusUrl
        );

        assertEquals(statusUrl, response.statusUrl());
    }

    @Test
    void rejectsBlankStatusUrls() {
        for (String invalidStatusUrl : List.of("", " ", "\t\n")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new EtlJobAcceptedResponse(
                            JOB_RECORD_ID,
                            EtlJobStatus.PENDING,
                            invalidStatusUrl
                    )
            );
        }
    }

    @Test
    void rejectsNonOriginRelativeStatusUrls() {
        for (String invalidStatusUrl : List.of(
                "https://example.test/api/etl/jobs/" + JOB_RECORD_ID,
                "//example.test/api/etl/jobs/" + JOB_RECORD_ID,
                "api/etl/jobs/" + JOB_RECORD_ID
        )) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new EtlJobAcceptedResponse(
                            JOB_RECORD_ID,
                            EtlJobStatus.PENDING,
                            invalidStatusUrl
                    )
            );
        }
    }
}
