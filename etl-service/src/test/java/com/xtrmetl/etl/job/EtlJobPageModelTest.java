package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Defines the immutable operator-safe page model used by durable job discovery.
 */
class EtlJobPageModelTest {

    @Test
    void copiesSnapshotsAndMapsOnlyOperatorSafeStatusFields() {
        EtlJobSnapshot snapshot = snapshot();
        List<EtlJobSnapshot> mutableSnapshots = new ArrayList<>();
        mutableSnapshots.add(snapshot);

        EtlJobPage page = new EtlJobPage(mutableSnapshots, "opaque_cursor");
        mutableSnapshots.clear();
        EtlJobPageResponse response = EtlJobPageResponse.from(page);

        assertEquals(List.of(snapshot), page.jobs());
        assertNotSame(mutableSnapshots, page.jobs());
        assertEquals("opaque_cursor", page.nextCursor());
        assertEquals(1, response.jobs().size());
        assertEquals(snapshot.jobRecordId(), response.jobs().getFirst().jobRecordId());
        assertEquals(snapshot.jobStatus(), response.jobs().getFirst().jobStatus());
        assertEquals(snapshot.attemptCount(), response.jobs().getFirst().attemptCount());
        assertNull(response.jobs().getFirst().failureCode());
        assertEquals(snapshot.createdAt(), response.jobs().getFirst().createdAt());
        assertEquals(snapshot.updatedAt(), response.jobs().getFirst().updatedAt());
        assertEquals("opaque_cursor", response.nextCursor());
        assertThrows(UnsupportedOperationException.class, () -> page.jobs().clear());
        assertThrows(UnsupportedOperationException.class, () -> response.jobs().clear());
    }

    @Test
    void rejectsNullCollectionsAndNullPageInputs() {
        assertThrows(NullPointerException.class, () -> new EtlJobPage(null, null));
        assertThrows(NullPointerException.class, () -> new EtlJobPageResponse(null, null));
        assertThrows(NullPointerException.class, () -> EtlJobPageResponse.from(null));
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobPage(List.of((EtlJobSnapshot) null), null)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobPageResponse(List.of((EtlJobStatusResponse) null), null)
        );
    }

    private static EtlJobSnapshot snapshot() {
        return new EtlJobSnapshot(
                UUID.fromString("cf4f083f-8c90-4f34-a8b6-b53761de44ef"),
                EtlJobStatus.SUCCEEDED,
                2,
                null,
                Instant.parse("2026-08-05T01:00:00Z"),
                Instant.parse("2026-08-05T01:00:05Z")
        );
    }
}
