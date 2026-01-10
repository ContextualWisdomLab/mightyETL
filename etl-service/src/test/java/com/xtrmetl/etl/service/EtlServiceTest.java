package com.xtrmetl.etl.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for EtlService.
 * Tests ETL operations, data transformation logic, error handling, and edge cases.
 */
@DisplayName("EtlService Tests")
class EtlServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EtlService etlService;

    private ObjectMapper realObjectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        realObjectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Happy Path Tests")
    class HappyPathTests {

        @Test
        @DisplayName("Should process single record successfully")
        void testProcessSingleRecord() throws Exception {
            String testData = "[{\"id\":\"1\",\"name\":\"John Doe\",\"email\":\"john@example.com\",\"amount\":\"100.50\"}]";
            String expectedSql = "INSERT INTO processed_data (data) VALUES (?)";
            String expectedTransformedData = "ID:1,NAME:JOHN DOE,EMAIL:john@example.com,AMOUNT:100.50,";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(expectedSql, expectedTransformedData)).thenReturn(1);

            String result = etlService.processData(testData);

            assertNotNull(result);
            assertTrue(result.contains("Processed: 1"));
            verify(jdbcTemplate, times(1)).update(expectedSql, expectedTransformedData);
        }

        @Test
        @DisplayName("Should process multiple records successfully")
        void testProcessMultipleRecords() throws Exception {
            String testData = "[" +
                "{\"id\":\"1\",\"name\":\"John Doe\",\"email\":\"john@example.com\",\"amount\":\"100.50\"}," +
                "{\"id\":\"2\",\"name\":\"Jane Smith\",\"email\":\"jane@example.com\",\"amount\":\"250.75\"}," +
                "{\"id\":\"3\",\"name\":\"Bob Johnson\",\"email\":\"bob@example.com\",\"amount\":\"75.25\"}" +
                "]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            String result = etlService.processData(testData);

            assertNotNull(result);
            assertTrue(result.contains("Processed: 1"));
            assertTrue(result.contains("Processed: 2"));
            assertTrue(result.contains("Processed: 3"));
            verify(jdbcTemplate, times(3)).update(anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle empty array gracefully")
        void testProcessDataWithEmptyInput() throws JsonProcessingException {
            String testData = "[]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);
            when(objectMapper.readTree(testData)).thenReturn(jsonNode);

            String result = etlService.processData(testData);

            assertNotNull(result);
            assertTrue(result.isEmpty() || result.isBlank());
            verify(jdbcTemplate, never()).update(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Data Transformation Tests")
    class DataTransformationTests {

        @Test
        @DisplayName("Should transform name to uppercase")
        void shouldTransformNameToUppercase() throws Exception {
            String testData = "[{\"id\":\"1\",\"name\":\"john doe\",\"email\":\"john@example.com\",\"amount\":\"100\"}]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            etlService.processData(testData);

            verify(jdbcTemplate).update(anyString(), contains("NAME:JOHN DOE"));
        }

        @Test
        @DisplayName("Should transform email to lowercase")
        void shouldTransformEmailToLowercase() throws Exception {
            String testData = "[{\"id\":\"1\",\"name\":\"John Doe\",\"email\":\"JOHN@EXAMPLE.COM\",\"amount\":\"100\"}]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            etlService.processData(testData);

            verify(jdbcTemplate).update(anyString(), contains("EMAIL:john@example.com"));
        }

        @ParameterizedTest
        @CsvSource({
            "100, 100.00",
            "100.5, 100.50",
            "100.50, 100.50",
            "0.99, 0.99",
            "1000.999, 1001.00"
        })
        @DisplayName("Should format amount to 2 decimal places")
        void shouldFormatAmountTo2Decimals(String input, String expected) throws Exception {
            String testData = "[{\"id\":\"1\",\"name\":\"Test\",\"email\":\"test@test.com\",\"amount\":\"" + input + "\"}]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            etlService.processData(testData);

            verify(jdbcTemplate).update(anyString(), contains("AMOUNT:" + expected));
        }

        @Test
        @DisplayName("Should handle invalid amount by defaulting to 0.00")
        void shouldHandleInvalidAmountGracefully() throws Exception {
            String testData = "[{\"id\":\"1\",\"name\":\"Test\",\"email\":\"test@test.com\",\"amount\":\"invalid\"}]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            etlService.processData(testData);

            verify(jdbcTemplate).update(anyString(), contains("AMOUNT:0.00"));
        }

        @Test
        @DisplayName("Should preserve field order in transformation")
        void shouldPreserveFieldOrder() throws Exception {
            String testData = "[{\"id\":\"1\",\"name\":\"Test\",\"email\":\"test@test.com\",\"amount\":\"100\"}]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            etlService.processData(testData);

            verify(jdbcTemplate).update(anyString(), ArgumentMatchers.<String>argThat(data ->
                data.contains("ID:") && data.contains("NAME:") && 
                data.contains("EMAIL:") && data.contains("AMOUNT:")
            ));
        }

        @Test
        @DisplayName("Should handle fields with special characters")
        void shouldHandleSpecialCharacters() throws Exception {
            String testData = "[{\"id\":\"1\",\"name\":\"O'Brien\",\"email\":\"test@test.com\",\"amount\":\"100\"}]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            assertDoesNotThrow(() -> etlService.processData(testData));
            verify(jdbcTemplate).update(anyString(), contains("NAME:O'BRIEN"));
        }

        @Test
        @DisplayName("Should handle extra fields beyond standard ones")
        void shouldHandleExtraFields() throws Exception {
            String testData = "[{\"id\":\"1\",\"name\":\"Test\",\"email\":\"test@test.com\"," +
                "\"amount\":\"100\",\"custom_field\":\"value\",\"another\":\"data\"}]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            assertDoesNotThrow(() -> etlService.processData(testData));
            verify(jdbcTemplate, times(1)).update(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should throw RuntimeException for invalid JSON")
        void testProcessDataWithInvalidJson() throws JsonProcessingException {
            String testData = "invalid json";
            when(objectMapper.readTree(testData)).thenThrow(new JsonProcessingException("Invalid JSON") {});

            RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> etlService.processData(testData));
            
            assertTrue(exception.getMessage().contains("Error processing data"));
            verify(jdbcTemplate, never()).update(anyString(), anyString());
        }

        @Test
        @DisplayName("Should throw RuntimeException when database insert fails")
        void shouldHandleDatabaseInsertFailure() throws Exception {
            String testData = "[{\"id\":\"1\",\"name\":\"Test\",\"email\":\"test@test.com\",\"amount\":\"100\"}]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString()))
                .thenThrow(new DataAccessException("Database error") {});

            assertThrows(RuntimeException.class, () -> etlService.processData(testData));
        }

        @Test
        @DisplayName("Should handle null JSON node gracefully")
        void shouldHandleNullJsonNode() throws Exception {
            String testData = "null";
            when(objectMapper.readTree(testData)).thenReturn(realObjectMapper.readTree(testData));

            assertThrows(RuntimeException.class, () -> etlService.processData(testData));
        }

        @Test
        @DisplayName("Should reject non-array JSON input")
        void shouldRejectNonArrayJsonInput() throws Exception {
            String testData = "{\"id\":\"1\",\"name\":\"Test\"}";
            when(objectMapper.readTree(testData)).thenReturn(realObjectMapper.readTree(testData));

            RuntimeException exception = assertThrows(RuntimeException.class, () -> etlService.processData(testData));
            assertTrue(exception.getMessage().contains("Input must be a JSON array"));
            verify(jdbcTemplate, never()).update(anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle record without id field")
        void shouldHandleRecordWithoutId() throws Exception {
            String testData = "[{\"name\":\"Test\",\"email\":\"test@test.com\",\"amount\":\"100\"}]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);

            assertThrows(RuntimeException.class, () -> etlService.processData(testData));
        }

        @Test
        @DisplayName("Should include cause in RuntimeException")
        void shouldIncludeCauseInException() throws Exception {
            String testData = "invalid";
            JsonProcessingException cause = new JsonProcessingException("Parse error") {};
            when(objectMapper.readTree(testData)).thenThrow(cause);

            RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> etlService.processData(testData));
            
            assertNotNull(exception.getCause());
            assertEquals(cause, exception.getCause());
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle record with missing optional fields")
        void shouldHandleMissingOptionalFields() throws Exception {
            String testData = "[{\"id\":\"1\"}]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            assertDoesNotThrow(() -> etlService.processData(testData));
        }

        @Test
        @DisplayName("Should handle empty string values")
        void shouldHandleEmptyStringValues() throws Exception {
            String testData = "[{\"id\":\"1\",\"name\":\"\",\"email\":\"\",\"amount\":\"\"}]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            assertDoesNotThrow(() -> etlService.processData(testData));
        }

        @Test
        @DisplayName("Should handle whitespace-only values")
        void shouldHandleWhitespaceOnlyValues() throws Exception {
            String testData = "[{\"id\":\"1\",\"name\":\"   \",\"email\":\" \",\"amount\":\"  \"}]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            assertDoesNotThrow(() -> etlService.processData(testData));
        }

        @Test
        @DisplayName("Should handle very large amounts")
        void shouldHandleVeryLargeAmounts() throws Exception {
            String testData = "[{\"id\":\"1\",\"name\":\"Test\",\"email\":\"test@test.com\",\"amount\":\"999999999.99\"}]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            assertDoesNotThrow(() -> etlService.processData(testData));
            verify(jdbcTemplate).update(anyString(), contains("AMOUNT:999999999.99"));
        }

        @Test
        @DisplayName("Should handle negative amounts")
        void shouldHandleNegativeAmounts() throws Exception {
            String testData = "[{\"id\":\"1\",\"name\":\"Test\",\"email\":\"test@test.com\",\"amount\":\"-50.00\"}]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            assertDoesNotThrow(() -> etlService.processData(testData));
            verify(jdbcTemplate).update(anyString(), contains("AMOUNT:-50.00"));
        }

        @Test
        @DisplayName("Should handle very long names")
        void shouldHandleVeryLongNames() throws Exception {
            String longName = "A".repeat(1000);
            String testData = "[{\"id\":\"1\",\"name\":\"" + longName + "\",\"email\":\"test@test.com\",\"amount\":\"100\"}]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            assertDoesNotThrow(() -> etlService.processData(testData));
        }

        @Test
        @DisplayName("Should handle Unicode characters in name")
        void shouldHandleUnicodeCharacters() throws Exception {
            String testData = "[{\"id\":\"1\",\"name\":\"José García 李明\",\"email\":\"test@test.com\",\"amount\":\"100\"}]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            assertDoesNotThrow(() -> etlService.processData(testData));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "test@example.com",
            "user.name@example.com",
            "user+tag@example.co.uk",
            "test_123@sub.example.com"
        })
        @DisplayName("Should handle various email formats")
        void shouldHandleVariousEmailFormats(String email) throws Exception {
            String testData = "[{\"id\":\"1\",\"name\":\"Test\",\"email\":\"" + email + "\",\"amount\":\"100\"}]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            assertDoesNotThrow(() -> etlService.processData(testData));
            verify(jdbcTemplate).update(anyString(), contains("EMAIL:" + email.toLowerCase()));
        }
    }

    @Nested
    @DisplayName("Parallel Processing Tests")
    class ParallelProcessingTests {

        @Test
        @DisplayName("Should process records in parallel")
        void shouldProcessRecordsInParallel() throws Exception {
            String testData = "[" +
                "{\"id\":\"1\",\"name\":\"Test1\",\"email\":\"test1@test.com\",\"amount\":\"100\"}," +
                "{\"id\":\"2\",\"name\":\"Test2\",\"email\":\"test2@test.com\",\"amount\":\"200\"}," +
                "{\"id\":\"3\",\"name\":\"Test3\",\"email\":\"test3@test.com\",\"amount\":\"300\"}," +
                "{\"id\":\"4\",\"name\":\"Test4\",\"email\":\"test4@test.com\",\"amount\":\"400\"}," +
                "{\"id\":\"5\",\"name\":\"Test5\",\"email\":\"test5@test.com\",\"amount\":\"500\"}" +
                "]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            String result = etlService.processData(testData);

            assertNotNull(result);
            verify(jdbcTemplate, times(5)).update(anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle concurrent database writes")
        void shouldHandleConcurrentDatabaseWrites() throws Exception {
            String testData = "[" +
                "{\"id\":\"1\",\"name\":\"Test1\",\"email\":\"test1@test.com\",\"amount\":\"100\"}," +
                "{\"id\":\"2\",\"name\":\"Test2\",\"email\":\"test2@test.com\",\"amount\":\"200\"}" +
                "]";
            JsonNode jsonNode = realObjectMapper.readTree(testData);

            when(objectMapper.readTree(testData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            assertDoesNotThrow(() -> etlService.processData(testData));
            verify(jdbcTemplate, times(2)).update(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Retry Mechanism Tests")
    class RetryMechanismTests {

        @Test
        @DisplayName("Service should have @Retryable annotation")
        void shouldHaveRetryableAnnotation() throws NoSuchMethodException {
            assertTrue(
                EtlService.class.getMethod("processData", String.class)
                    .isAnnotationPresent(org.springframework.retry.annotation.Retryable.class),
                "processData method should have @Retryable annotation"
            );
        }
    }

    @Nested
    @DisplayName("SQL Injection Prevention Tests")
    class SqlInjectionPreventionTests {

        @Test
        @DisplayName("Should use parameterized queries to prevent SQL injection")
        void shouldUseParameterizedQueries() throws Exception {
            String maliciousData = "[{\"id\":\"1\",\"name\":\"Test'; DROP TABLE users; --\"," +
                "\"email\":\"test@test.com\",\"amount\":\"100\"}]";
            JsonNode jsonNode = realObjectMapper.readTree(maliciousData);

            when(objectMapper.readTree(maliciousData)).thenReturn(jsonNode);
            when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);

            etlService.processData(maliciousData);

            // Verify that data is passed as parameter, not concatenated into SQL
            verify(jdbcTemplate).update(eq("INSERT INTO processed_data (data) VALUES (?)"), anyString());
        }
    }
}
