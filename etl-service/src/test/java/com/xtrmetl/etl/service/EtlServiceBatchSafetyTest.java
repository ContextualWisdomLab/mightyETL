package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Defines the resource-safety, validation, and transaction contract for ETL request batches.
 */
class EtlServiceBatchSafetyTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsPayloadLargerThanUtf8LimitBeforeJdbc() {
        EtlBatchProperties properties = properties(24, 10);
        EtlService service = new EtlService(jdbcTemplate, objectMapper, properties);
        String payload = "[{\"id\":\"1\",\"name\":\"한글데이터\"}]";

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
        String payload = "[{\"id\":\"1\"},{\"id\":\"2\"}]";

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> service.processData(payload)
        );

        assertTrue(exception.getMessage().contains("record"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void validatesWholeBatchBeforeFirstJdbcWrite() {
        EtlService service = new EtlService(
                jdbcTemplate,
                objectMapper,
                properties(4096, 10)
        );
        String payload = "[{\"id\":\"1\",\"name\":\"valid\"},{\"name\":\"missing id\"}]";

        assertThrows(RuntimeException.class, () -> service.processData(payload));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsNonObjectRecordsBeforeJdbc() {
        EtlService service = new EtlService(
                jdbcTemplate,
                objectMapper,
                properties(4096, 10)
        );

        assertThrows(RuntimeException.class, () -> service.processData("[\"not-an-object\"]"));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsBlankRecordIdentifiersBeforeJdbc() {
        EtlService service = new EtlService(
                jdbcTemplate,
                objectMapper,
                properties(4096, 10)
        );

        assertThrows(RuntimeException.class, () -> service.processData("[{\"id\":\"   \"}]"));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void preservesCommaAndColonCharactersInValues() {
        EtlService service = new EtlService(
                jdbcTemplate,
                objectMapper,
                properties(4096, 10)
        );
        String sql = "INSERT INTO processed_data (data) VALUES (?)";

        service.processData("[{\"id\":\"1\",\"name\":\"A:B,C\"}]");

        verify(jdbcTemplate).update(sql, "ID:1,NAME:A:B,C,");
    }

    @Test
    void formatsAmountsWithDecimalPrecisionIndependentOfDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            EtlService service = new EtlService(
                    jdbcTemplate,
                    objectMapper,
                    properties(4096, 10)
            );
            String sql = "INSERT INTO processed_data (data) VALUES (?)";

            service.processData("[{\"id\":\"1\",\"amount\":\"1000.005\"}]");

            verify(jdbcTemplate).update(sql, "ID:1,AMOUNT:1000.01,");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void processDataDefinesOneTransactionBoundary() throws NoSuchMethodException {
        assertTrue(
                EtlService.class.getMethod("processData", String.class)
                        .isAnnotationPresent(Transactional.class),
                "processData must be transactional so a failed record cannot leave partial writes"
        );
    }

    private static EtlBatchProperties properties(int maxPayloadBytes, int maxBatchRecords) {
        EtlBatchProperties properties = new EtlBatchProperties();
        properties.setMaxPayloadBytes(maxPayloadBytes);
        properties.setMaxBatchRecords(maxBatchRecords);
        return properties;
    }
}
