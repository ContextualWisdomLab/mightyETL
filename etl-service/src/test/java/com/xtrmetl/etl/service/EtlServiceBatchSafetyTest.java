package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Defines the resource-safety, validation, retry, and transaction contract for ETL batches.
 */
class EtlServiceBatchSafetyTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void usesSafeDefaultsWhenLegacyConstructionProvidesNoProperties() {
        EtlService service = new EtlService(jdbcTemplate, objectMapper, null);

        String result = service.processData("[{\"id\":\"record_alpha\"}]");

        assertEquals("Processed: record_alpha", result);
        verify(jdbcTemplate).update(
                "INSERT INTO processed_data (data) VALUES (?)",
                "ID:record_alpha,"
        );
    }

    @Test
    void rejectsPayloadLargerThanUtf8LimitBeforeJdbc() {
        EtlBatchProperties properties = properties(24, 10);
        EtlService service = new EtlService(jdbcTemplate, objectMapper, properties);
        String payload = "[{\"id\":\"record_alpha\",\"name\":\"한글데이터\"}]";

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> service.processData(payload)
        );

        assertTrue(exception.getMessage().contains("payload"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsRecordCountOverLimitBeforeJdbc() {
        EtlBatchProperties properties = properties(4096, 1);
        EtlService service = new EtlService(jdbcTemplate, objectMapper, properties);
        String payload = "[{\"id\":\"record_alpha\"},{\"id\":\"record_beta\"}]";

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> service.processData(payload)
        );

        assertTrue(exception.getMessage().contains("record"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsDuplicateJsonFieldsBeforeJdbc() {
        EtlService service = service();
        String payload = """
                [{
                  "id":"record_alpha",
                  "name":"first",
                  "name":"second"
                }]
                """;

        assertThrows(RuntimeException.class, () -> service.processData(payload));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void validatesWholeBatchBeforeFirstJdbcWrite() {
        EtlService service = new EtlService(
                jdbcTemplate,
                objectMapper,
                properties(4096, 10)
        );
        String payload = "[{\"id\":\"record_alpha\",\"name\":\"valid\"},{\"name\":\"missing id\"}]";

        assertThrows(RuntimeException.class, () -> service.processData(payload));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsNonObjectRecordsBeforeJdbc() {
        EtlService service = service();

        assertThrows(RuntimeException.class, () -> service.processData("[\"not-an-object\"]"));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsBlankRecordIdentifiersBeforeJdbc() {
        EtlService service = service();

        assertThrows(RuntimeException.class, () -> service.processData("[{\"id\":\"   \"}]"));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsNumericRecordIdentifierTypesBeforeJdbc() {
        EtlService service = service();

        assertThrows(RuntimeException.class, () -> service.processData("[{\"id\":123}]"));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsIdentifiersWithLeadingOrTrailingWhitespaceBeforeJdbc() {
        EtlService service = service();

        assertThrows(RuntimeException.class,
                () -> service.processData("[{\"id\":\" record_alpha \"}]"));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsIdentifiersContainingControlCharactersBeforeJdbc() {
        EtlService service = service();

        assertThrows(RuntimeException.class,
                () -> service.processData("[{\"id\":\"record_alpha\\nforged_line\"}]"));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsIdentifiersContainingUnicodeLineSeparatorsBeforeJdbc() {
        EtlService service = service();

        assertThrows(RuntimeException.class,
                () -> service.processData("[{\"id\":\"record_alpha\\u2028forged_line\"}]"));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsIdentifiersLongerThanTheSupportedResponseBoundary() {
        EtlService service = service();
        String identifier = "record_" + "a".repeat(250);
        String payload = "[{\"id\":\"" + identifier + "\"}]";

        assertThrows(RuntimeException.class, () -> service.processData(payload));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void preservesCommaAndColonCharactersInValues() {
        EtlService service = service();
        String sql = "INSERT INTO processed_data (data) VALUES (?)";

        service.processData("[{\"id\":\"record_alpha\",\"name\":\"A:B,C\"}]");

        verify(jdbcTemplate).update(sql, "ID:record_alpha,NAME:A:B,C,");
    }

    @Test
    void preservesNestedJsonValuesInsteadOfCollapsingThemToEmptyText() {
        EtlService service = service();
        String sql = "INSERT INTO processed_data (data) VALUES (?)";

        service.processData("""
                [{
                  "id":"record_alpha",
                  "metadata":{"region":"east:one","tags":["a,b","c:d"]}
                }]
                """);

        verify(jdbcTemplate).update(
                sql,
                "ID:record_alpha,METADATA:{\"region\":\"east:one\",\"tags\":[\"a,b\",\"c:d\"]},"
        );
    }

    @Test
    void usesLocaleIndependentTextAndDecimalTransformations() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            EtlService service = service();
            String sql = "INSERT INTO processed_data (data) VALUES (?)";

            service.processData("""
                    [{
                      "id":"record_alpha",
                      "name":"indigo",
                      "email":"USER@EXAMPLE.COM",
                      "amount":"1000.005"
                    }]
                    """);

            verify(jdbcTemplate).update(
                    sql,
                    "ID:record_alpha,NAME:INDIGO,EMAIL:user@example.com,AMOUNT:1000.01,"
            );
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void boundsExtremeDecimalInputsWithoutHugePlainStringExpansion() {
        EtlService service = service();

        service.processData("[{\"id\":\"record_alpha\",\"amount\":\"1E+1000000\"}]");

        verify(jdbcTemplate).update(
                "INSERT INTO processed_data (data) VALUES (?)",
                "ID:record_alpha,AMOUNT:0.00,"
        );
    }

    @Test
    void processDataDefinesTransactionAndTransientRetryBoundaries() throws NoSuchMethodException {
        Method method = EtlService.class.getMethod("processData", String.class);
        assertTrue(
                method.isAnnotationPresent(Transactional.class),
                "processData must be transactional so a failed record cannot leave partial writes"
        );

        Retryable retryable = method.getAnnotation(Retryable.class);
        assertArrayEquals(
                new Class<?>[]{TransientDataAccessException.class},
                retryable.retryFor(),
                "only transient data-access failures should be retried"
        );
    }

    private EtlService service() {
        return new EtlService(jdbcTemplate, objectMapper, properties(4096, 10));
    }

    private static EtlBatchProperties properties(int maxPayloadBytes, int maxBatchRecords) {
        EtlBatchProperties properties = new EtlBatchProperties();
        properties.setMaxPayloadBytes(maxPayloadBytes);
        properties.setMaxBatchRecords(maxBatchRecords);
        return properties;
    }
}
