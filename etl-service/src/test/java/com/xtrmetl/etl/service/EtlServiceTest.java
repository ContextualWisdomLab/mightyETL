package com.xtrmetl.etl.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Core behavior tests for {@link EtlService} using a real Jackson parser and mocked JDBC boundary.
 *
 * <p>Parser isolation and adversarial admission cases are covered in the focused safety suites;
 * this class preserves broad transformation and compatibility coverage without mocking Jackson
 * internals that the service intentionally copies.</p>
 */
@DisplayName("EtlService Tests")
class EtlServiceTest {

    private JdbcTemplate jdbcTemplate;
    private EtlService etlService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        EtlBatchProperties properties = new EtlBatchProperties();
        properties.setMaxPayloadBytes(65_536);
        properties.setMaxBatchRecords(100);
        etlService = new EtlService(jdbcTemplate, new ObjectMapper(), properties);
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPathTests {

        @Test
        void processesSingleRecordWithExpectedTransformations() {
            String input = """
                    [{
                      "id":"record_alpha",
                      "name":"John Doe",
                      "email":"JOHN@EXAMPLE.COM",
                      "amount":"100.50"
                    }]
                    """;

            String result = etlService.processData(input);

            assertEquals("Processed: record_alpha", result);
            verify(jdbcTemplate).update(
                    "INSERT INTO processed_data (data) VALUES (?)",
                    "ID:record_alpha,NAME:JOHN DOE,EMAIL:john@example.com,AMOUNT:100.50,"
            );
        }

        @Test
        void processesMultipleRecordsAndPreservesResponseOrder() {
            String input = """
                    [
                      {"id":"record_alpha","name":"Alpha"},
                      {"id":"record_beta","name":"Beta"},
                      {"id":"record_gamma","name":"Gamma"}
                    ]
                    """;

            String result = etlService.processData(input);

            assertEquals(
                    "Processed: record_alpha\nProcessed: record_beta\nProcessed: record_gamma",
                    result
            );
            verify(jdbcTemplate, times(3)).update(anyString(), anyString());
        }

        @Test
        void acceptsEmptyArrayWithoutDatabaseWork() {
            String result = etlService.processData("[]");

            assertEquals("", result);
            verifyNoInteractions(jdbcTemplate);
        }
    }

    @Nested
    @DisplayName("Transformations")
    class TransformationTests {

        @ParameterizedTest
        @CsvSource({
                "100, 100.00",
                "100.5, 100.50",
                "100.50, 100.50",
                "0.99, 0.99",
                "1000.999, 1001.00",
                "-50, -50.00"
        })
        void formatsAmountsDeterministically(String input, String expected) {
            etlService.processData(
                    "[{\"id\":\"record_alpha\",\"amount\":\"" + input + "\"}]"
            );

            verify(jdbcTemplate).update(anyString(), contains("AMOUNT:" + expected));
        }

        @Test
        void fallsBackToZeroForInvalidAmount() {
            etlService.processData(
                    "[{\"id\":\"record_alpha\",\"amount\":\"not-a-number\"}]"
            );

            verify(jdbcTemplate).update(anyString(), contains("AMOUNT:0.00"));
        }

        @Test
        void retainsUnknownAndUnicodeFields() {
            etlService.processData("""
                    [{
                      "id":"record_alpha",
                      "custom_field":"José García 李明",
                      "another":"value"
                    }]
                    """);

            verify(jdbcTemplate).update(
                    anyString(),
                    eq("ID:record_alpha,CUSTOM_FIELD:José García 李明,ANOTHER:value,")
            );
        }

        @Test
        void preservesEmptyOptionalValues() {
            etlService.processData("""
                    [{
                      "id":"record_alpha",
                      "name":"",
                      "email":"",
                      "amount":""
                    }]
                    """);

            verify(jdbcTemplate).update(
                    anyString(),
                    eq("ID:record_alpha,NAME:,EMAIL:,AMOUNT:0.00,")
            );
        }
    }

    @Nested
    @DisplayName("Failures")
    class FailureTests {

        @Test
        void classifiesInvalidJsonAndRetainsOriginalParsingCause() {
            EtlRequestException exception = assertThrows(
                    EtlRequestException.class,
                    () -> etlService.processData("not json")
            );

            assertSame(EtlRequestError.INVALID_JSON, exception.error());
            assertEquals("etl_invalid_json", exception.getMessage());
            assertNotNull(exception.getCause());
            assertInstanceOf(JsonProcessingException.class, exception.getCause());
            verifyNoInteractions(jdbcTemplate);
        }

        @Test
        void rethrowsDataAccessExceptionForTransactionAndRetryInfrastructure() {
            DataAccessException databaseFailure = new DataAccessException("database unavailable") { };
            when(jdbcTemplate.update(anyString(), anyString())).thenThrow(databaseFailure);

            DataAccessException thrown = assertThrows(
                    DataAccessException.class,
                    () -> etlService.processData("[{\"id\":\"record_alpha\"}]")
            );

            assertEquals(databaseFailure, thrown);
        }

        @Test
        void rejectsNullAndNonArrayDocumentsBeforeJdbc() {
            EtlRequestException nullDocument = assertThrows(
                    EtlRequestException.class,
                    () -> etlService.processData("null")
            );
            EtlRequestException objectDocument = assertThrows(
                    EtlRequestException.class,
                    () -> etlService.processData("{\"id\":\"record_alpha\"}")
            );

            assertSame(EtlRequestError.INVALID_JSON, nullDocument.error());
            assertSame(EtlRequestError.INVALID_JSON, objectDocument.error());
            verifyNoInteractions(jdbcTemplate);
        }

        @Test
        void rejectsMissingIdentifierBeforeJdbc() {
            EtlRequestException exception = assertThrows(
                    EtlRequestException.class,
                    () -> etlService.processData("[{\"name\":\"missing id\"}]")
            );

            assertSame(EtlRequestError.INVALID_RECORD, exception.error());
            verifyNoInteractions(jdbcTemplate);
        }
    }

    @Nested
    @DisplayName("Contracts")
    class ContractTests {

        @Test
        void usesParameterizedInsert() {
            etlService.processData("""
                    [{
                      "id":"record_alpha",
                      "name":"Test'; DROP TABLE users; --"
                    }]
                    """);

            verify(jdbcTemplate).update(
                    eq("INSERT INTO processed_data (data) VALUES (?)"),
                    anyString()
            );
        }

        @Test
        void declaresTransactionAndTransientRetryPolicy() throws NoSuchMethodException {
            Method method = EtlService.class.getMethod("processData", String.class);

            assertTrue(method.isAnnotationPresent(Transactional.class));
            Retryable retryable = method.getAnnotation(Retryable.class);
            assertNotNull(retryable);
            assertArrayEquals(
                    new Class<?>[]{TransientDataAccessException.class},
                    retryable.retryFor()
            );
        }

        @Test
        void invalidInputNeverReachesJdbc() {
            assertThrows(EtlRequestException.class, () -> etlService.processData("invalid"));

            verify(jdbcTemplate, never()).update(anyString(), anyString());
        }
    }
}
