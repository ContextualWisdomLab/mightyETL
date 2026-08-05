package com.xtrmetl.etl.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.service.EtlBatchProperties;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import com.xtrmetl.etl.service.EtlRequestLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Exercises realistic durable-intake validation boundaries that are easy to miss in ordinary
 * happy-path and persistence tests.
 *
 * <p>The cases model a parser that reaches end-of-input without producing a tree, intentionally
 * empty batches, visually ambiguous Unicode identifiers, identifiers that exceed the documented
 * code-point bound, and separators that can alter how an identifier appears in logs or text tools.
 * Every rejection must happen before transaction, lock, or database work so an invalid client
 * request cannot consume shared persistence capacity.</p>
 */
class EtlJobServiceCoverageCompletionTest {

    private static final String IDEMPOTENCY_KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final String PRINCIPAL_SCOPE = "tenant_alpha";

    /**
     * Verifies the defensive parser-end-of-input boundary independently of Jackson version-specific
     * empty-string behavior. A copied application mapper is allowed to return no tree, and the
     * service must classify that result as invalid JSON before transaction, lock, or database work.
     *
     * @throws Exception when Mockito cannot configure the checked parser method
     */
    @Test
    void rejectsParserEndOfInputWithoutAJsonTreeBeforePersistence() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlRequestLock requestLock = mock(EtlRequestLock.class);
        ObjectMapper sourceMapper = mock(ObjectMapper.class);
        ObjectMapper copiedMapper = mock(ObjectMapper.class);
        when(sourceMapper.copy()).thenReturn(copiedMapper);
        when(copiedMapper.readTree("parser-end-of-input")).thenReturn(null);
        EtlJobService service = new EtlJobService(
                jdbcTemplate,
                sourceMapper,
                new EtlBatchProperties(),
                requestLock
        );

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> service.submit("parser-end-of-input", IDEMPOTENCY_KEY, PRINCIPAL_SCOPE)
        );

        assertEquals(EtlRequestError.INVALID_JSON, exception.error());
        verifyNoInteractions(requestLock, jdbcTemplate);
    }

    /**
     * Verifies that a valid empty JSON batch completes record validation and reaches the transaction
     * boundary without attempting a lock or database operation. Empty batches are distinct from
     * empty request bodies: they are well-formed arrays containing zero records and therefore
     * exercise the no-iteration path of whole-batch prevalidation.
     */
    @Test
    void acceptsEmptyBatchThroughValidationBeforeRequiringTransaction() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlRequestLock requestLock = mock(EtlRequestLock.class);
        EtlJobService service = service(jdbcTemplate, requestLock);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.submit("[]", IDEMPOTENCY_KEY, PRINCIPAL_SCOPE)
        );

        assertEquals(
                "Durable ETL job submission requires an active transaction",
                exception.getMessage()
        );
        verifyNoInteractions(requestLock, jdbcTemplate);
    }

    /**
     * Verifies identifier rejection for Unicode boundary whitespace, format controls, line and
     * paragraph separators, and the documented 256-code-point maximum.
     *
     * @param identifier identifier embedded in an otherwise valid one-record JSON batch
     * @param scenario beginner-readable reason that the case is unsafe
     */
    @ParameterizedTest(name = "{1}")
    @MethodSource("unsafeIdentifiers")
    void rejectsUnsafeOrOverlongIdentifiersBeforeTransactionOrPersistence(
            String identifier,
            String scenario
    ) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlRequestLock requestLock = mock(EtlRequestLock.class);
        EtlJobService service = service(jdbcTemplate, requestLock);
        String payload = "[{\"id\":" + new ObjectMapper().valueToTree(identifier) + "}]";

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> service.submit(payload, IDEMPOTENCY_KEY, PRINCIPAL_SCOPE),
                scenario
        );

        assertEquals(EtlRequestError.INVALID_RECORD, exception.error());
        verifyNoInteractions(requestLock, jdbcTemplate);
    }

    /**
     * Supplies concrete identifier attacks and admission-bound violations.
     *
     * @return parameter stream containing identifier text and its operational threat
     */
    private static Stream<Arguments> unsafeIdentifiers() {
        return Stream.of(
                Arguments.of("\u00a0record_alpha", "non-breaking boundary whitespace"),
                Arguments.of("x".repeat(257), "identifier longer than 256 code points"),
                Arguments.of("record\u200balpha", "zero-width format control"),
                Arguments.of("record\u2028alpha", "Unicode line separator"),
                Arguments.of("record\u2029alpha", "Unicode paragraph separator")
        );
    }

    /**
     * Creates a service whose lock and database collaborators reveal any premature side effect.
     *
     * @param jdbcTemplate mocked database collaborator
     * @param requestLock mocked transaction-lifetime lock collaborator
     * @return service configured with normal production admission limits
     */
    private static EtlJobService service(
            JdbcTemplate jdbcTemplate,
            EtlRequestLock requestLock
    ) {
        return new EtlJobService(
                jdbcTemplate,
                new ObjectMapper(),
                new EtlBatchProperties(),
                requestLock
        );
    }
}
