package com.xtrmetl.etl.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Defines shared normalization and hashing behavior for synchronous requests and durable jobs.
 */
class EtlIdempotencyKeyTest {

    private static final String KEY = "550e8400-e29b-41d4-a716-446655440000";

    @Test
    void normalizesQuotedAndLegacyRepresentationsToTheSameSemanticKey() {
        assertEquals(KEY, EtlIdempotencyKey.normalize(KEY));
        assertEquals(KEY, EtlIdempotencyKey.normalize("\"" + KEY + "\""));
    }

    @Test
    void rejectsMissingOrUnsafeRepresentationsWithTheStableRequestClassification() {
        for (String invalidKey : new String[]{null, "short", "key with spaces 123456"}) {
            EtlRequestException exception = assertThrows(
                    EtlRequestException.class,
                    () -> EtlIdempotencyKey.normalize(invalidKey)
            );
            assertEquals(EtlRequestError.INVALID_IDEMPOTENCY_KEY, exception.error());
        }
    }

    @Test
    void calculatesDeterministicUtf8Sha256() {
        assertEquals(
                "50d858e0985ecc7f60418aaf0cc5ab587f42c2570a884095a9e8ccacd0f6545c",
                EtlIdempotencyKey.sha256("example")
        );
        assertThrows(NullPointerException.class, () -> EtlIdempotencyKey.sha256(null));
    }

    @Test
    void preservesTheExistingPrincipalScopedLedgerHashContract() {
        assertEquals(
                "6acb780c12ff9e80c23b832f3a033cfab014b06cfb5023e6fa457f2e47ba3338",
                EtlIdempotencyKey.scopedHash("tenant_alpha", KEY)
        );
    }

    @Test
    void separatesJobHashDomains() {
        String principalHash = EtlIdempotencyKey.domainScopedHash(
                "job_principal",
                "tenant_alpha",
                KEY
        );
        String submissionHash = EtlIdempotencyKey.domainScopedHash(
                "job_submission",
                "tenant_alpha",
                KEY
        );

        assertNotEquals(principalHash, submissionHash);
        assertEquals(principalHash, EtlIdempotencyKey.domainScopedHash(
                "job_principal",
                "tenant_alpha",
                KEY
        ));
        assertThrows(
                NullPointerException.class,
                () -> EtlIdempotencyKey.domainScopedHash(null, "tenant_alpha", KEY)
        );
        assertThrows(
                NullPointerException.class,
                () -> EtlIdempotencyKey.domainScopedHash("job_principal", null, KEY)
        );
        assertThrows(
                NullPointerException.class,
                () -> EtlIdempotencyKey.domainScopedHash("job_principal", "tenant_alpha", null)
        );
    }
}
