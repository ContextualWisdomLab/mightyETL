package com.xtrmetl.etl.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.service.EtlBatchProperties;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import com.xtrmetl.etl.service.EtlRequestLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers validation, transaction, and nonblocking-lock boundaries before durable job persistence.
 */
class EtlJobServiceBoundaryTest {

    private static final String IDEMPOTENCY_KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final String VALID_PAYLOAD = "[{\"id\":\"record_alpha\"}]";

    @AfterEach
    void clearSyntheticTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void refusesValidSubmissionWithoutAnActualTransactionBeforeDatabaseWork() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlRequestLock requestLock = mock(EtlRequestLock.class);
        EtlJobService service = service(jdbcTemplate, requestLock, new EtlBatchProperties());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.submit(VALID_PAYLOAD, IDEMPOTENCY_KEY, "tenant_alpha")
        );

        assertEquals(
                "Durable ETL job submission requires an active transaction",
                exception.getMessage()
        );
        verifyNoInteractions(requestLock, jdbcTemplate);
    }

    @Test
    void returnsImmediateConflictWhenAnotherSubmissionOwnsTheTryLock() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlRequestLock requestLock = mock(EtlRequestLock.class);
        EtlJobService service = service(jdbcTemplate, requestLock, new EtlBatchProperties());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(requestLock.tryLock(anyString())).thenReturn(false);

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> service.submit(VALID_PAYLOAD, IDEMPOTENCY_KEY, "tenant_alpha")
        );

        assertEquals(EtlRequestError.JOB_SUBMISSION_IN_PROGRESS, exception.error());
        verify(requestLock).tryLock(anyString());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void reportsPlatformFailureWhenRequiredSha256DigestIsUnavailable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlRequestLock requestLock = mock(EtlRequestLock.class);
        EtlJobService service = service(jdbcTemplate, requestLock, new EtlBatchProperties());
        Provider[] originalProviders = Security.getProviders();
        Provider[] sha256Providers = Security.getProviders("MessageDigest.SHA-256");
        assertNotNull(sha256Providers);
        assertTrue(sha256Providers.length > 0);

        Map<String, Integer> originalPositions = new HashMap<>();
        for (int index = 0; index < originalProviders.length; index++) {
            originalPositions.put(originalProviders[index].getName(), index + 1);
        }

        try {
            for (Provider provider : sha256Providers) {
                Security.removeProvider(provider.getName());
            }
            assertThrows(NoSuchAlgorithmException.class, () -> MessageDigest.getInstance("SHA-256"));

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> service.findOwned(UUID.randomUUID(), "tenant_alpha")
            );

            assertEquals("SHA-256 is required by the Java platform", exception.getMessage());
            assertEquals(NoSuchAlgorithmException.class, exception.getCause().getClass());
        } finally {
            for (Provider provider : originalProviders) {
                if (Security.getProvider(provider.getName()) == null) {
                    Security.insertProviderAt(provider, originalPositions.get(provider.getName()));
                }
            }
        }
        verifyNoInteractions(requestLock, jdbcTemplate);
    }

    @Test
    void rejectsInvalidKeysAndPrincipalScopesBeforeTransactionOrDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlRequestLock requestLock = mock(EtlRequestLock.class);
        EtlJobService service = service(jdbcTemplate, requestLock, new EtlBatchProperties());

        assertError(
                EtlRequestError.INVALID_IDEMPOTENCY_KEY,
                () -> service.submit(VALID_PAYLOAD, null, "tenant_alpha")
        );
        assertError(
                EtlRequestError.INVALID_IDEMPOTENCY_KEY,
                () -> service.submit(VALID_PAYLOAD, "unsafe key value", "tenant_alpha")
        );
        assertError(
                EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED,
                () -> service.submit(VALID_PAYLOAD, IDEMPOTENCY_KEY, null)
        );
        assertError(
                EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED,
                () -> service.submit(VALID_PAYLOAD, IDEMPOTENCY_KEY, "   ")
        );
        assertError(
                EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED,
                () -> service.submit(VALID_PAYLOAD, IDEMPOTENCY_KEY, "x".repeat(513))
        );
        verifyNoInteractions(requestLock, jdbcTemplate);
    }

    @Test
    void rejectsEveryCoveredPayloadAdmissionFailureBeforePersistence() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlRequestLock requestLock = mock(EtlRequestLock.class);
        EtlBatchProperties properties = new EtlBatchProperties();
        properties.setMaxBatchRecords(1);
        EtlJobService service = service(jdbcTemplate, requestLock, properties);

        assertError(
                EtlRequestError.INVALID_JSON,
                () -> service.submit(null, IDEMPOTENCY_KEY, "tenant_alpha")
        );
        assertError(
                EtlRequestError.INVALID_JSON,
                () -> service.submit("   ", IDEMPOTENCY_KEY, "tenant_alpha")
        );
        assertError(
                EtlRequestError.INVALID_JSON,
                () -> service.submit("null", IDEMPOTENCY_KEY, "tenant_alpha")
        );
        assertError(
                EtlRequestError.INVALID_JSON,
                () -> service.submit("{}", IDEMPOTENCY_KEY, "tenant_alpha")
        );
        assertError(
                EtlRequestError.INVALID_JSON,
                () -> service.submit(
                        "[{\"id\":\"record_alpha\",\"id\":\"record_beta\"}]",
                        IDEMPOTENCY_KEY,
                        "tenant_alpha"
                )
        );
        assertError(
                EtlRequestError.BATCH_TOO_LARGE,
                () -> service.submit(
                        "[{\"id\":\"record_alpha\"},{\"id\":\"record_beta\"}]",
                        IDEMPOTENCY_KEY,
                        "tenant_alpha"
                )
        );
        assertError(
                EtlRequestError.INVALID_RECORD,
                () -> service.submit("[null]", IDEMPOTENCY_KEY, "tenant_alpha")
        );
        assertError(
                EtlRequestError.INVALID_RECORD,
                () -> service.submit("[{}]", IDEMPOTENCY_KEY, "tenant_alpha")
        );
        assertError(
                EtlRequestError.INVALID_RECORD,
                () -> service.submit("[{\"id\":1}]", IDEMPOTENCY_KEY, "tenant_alpha")
        );
        assertError(
                EtlRequestError.INVALID_RECORD,
                () -> service.submit("[{\"id\":\" \"}]", IDEMPOTENCY_KEY, "tenant_alpha")
        );
        assertError(
                EtlRequestError.INVALID_RECORD,
                () -> service.submit("[{\"id\":\" record_alpha\"}]", IDEMPOTENCY_KEY, "tenant_alpha")
        );
        assertError(
                EtlRequestError.INVALID_RECORD,
                () -> service.submit(
                        payloadWithIdentifier("\u00a0record_alpha"),
                        IDEMPOTENCY_KEY,
                        "tenant_alpha"
                )
        );
        assertError(
                EtlRequestError.INVALID_RECORD,
                () -> service.submit(
                        payloadWithIdentifier("x".repeat(257)),
                        IDEMPOTENCY_KEY,
                        "tenant_alpha"
                )
        );
        assertError(
                EtlRequestError.INVALID_RECORD,
                () -> service.submit("[{\"id\":\"record\\u0000alpha\"}]", IDEMPOTENCY_KEY, "tenant_alpha")
        );
        assertError(
                EtlRequestError.INVALID_RECORD,
                () -> service.submit(
                        payloadWithIdentifier("record" + Character.toString(0x200e) + "alpha"),
                        IDEMPOTENCY_KEY,
                        "tenant_alpha"
                )
        );
        assertError(
                EtlRequestError.INVALID_RECORD,
                () -> service.submit(
                        payloadWithIdentifier("record" + Character.toString(0x2028) + "alpha"),
                        IDEMPOTENCY_KEY,
                        "tenant_alpha"
                )
        );
        assertError(
                EtlRequestError.INVALID_RECORD,
                () -> service.submit(
                        payloadWithIdentifier("record" + Character.toString(0x2029) + "alpha"),
                        IDEMPOTENCY_KEY,
                        "tenant_alpha"
                )
        );
        assertError(
                EtlRequestError.INVALID_RECORD,
                () -> service.submit(
                        "[{\"id\":\"record_alpha\",\"name\":1,\"NAME\":2}]",
                        IDEMPOTENCY_KEY,
                        "tenant_alpha"
                )
        );
        verifyNoInteractions(requestLock, jdbcTemplate);
    }

    @Test
    void validatesConstructorAndLookupRequiredValues() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        EtlBatchProperties properties = new EtlBatchProperties();
        EtlRequestLock requestLock = mock(EtlRequestLock.class);

        new EtlJobService(jdbcTemplate, objectMapper, properties);
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobService(null, objectMapper, properties, requestLock)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobService(jdbcTemplate, null, properties, requestLock)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobService(jdbcTemplate, objectMapper, null, requestLock)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobService(jdbcTemplate, objectMapper, properties, null)
        );

        EtlJobService service = service(jdbcTemplate, requestLock, properties);
        assertThrows(NullPointerException.class, () -> service.findOwned(null, "tenant_alpha"));
        assertError(
                EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED,
                () -> service.findOwned(UUID.randomUUID(), null)
        );
        verifyNoInteractions(requestLock, jdbcTemplate);
    }

    private static String payloadWithIdentifier(String identifier) {
        return "[{\"id\":\"" + identifier + "\"}]";
    }

    private static EtlJobService service(
            JdbcTemplate jdbcTemplate,
            EtlRequestLock requestLock,
            EtlBatchProperties properties
    ) {
        return new EtlJobService(
                jdbcTemplate,
                new ObjectMapper(),
                properties,
                requestLock
        );
    }

    private static void assertError(EtlRequestError expected, Runnable invocation) {
        EtlRequestException exception = assertThrows(EtlRequestException.class, invocation::run);
        assertEquals(expected, exception.error());
    }
}
