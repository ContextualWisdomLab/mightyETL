package com.xtrmetl.etl.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EtlServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EtlService etlService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testProcessData() throws Exception {
        String testData = "[{\"id\":\"1\",\"name\":\"John Doe\",\"email\":\"john@example.com\",\"amount\":\"100.50\"}]";
        String expectedSql = "INSERT INTO processed_data (data) VALUES (?)";
        String expectedTransformedData = "ID:1,NAME:JOHN DOE,EMAIL:john@example.com,AMOUNT:100.50,";
	        JsonNode jsonNode = new ObjectMapper().readTree(testData);

	        when(objectMapper.readTree(testData)).thenReturn(jsonNode);
	        when(jdbcTemplate.update(expectedSql, expectedTransformedData)).thenReturn(1);

        String result = etlService.processData(testData);

	        assertNotNull(result);
	        assertTrue(result.contains("Processed: 1"));
	        verify(jdbcTemplate, times(1)).update(expectedSql, expectedTransformedData);
	    }

    @Test
    void testProcessDataWithEmptyInput() throws JsonProcessingException {
        String testData = "[]";
        when(objectMapper.readTree(testData)).thenReturn(new ObjectMapper().readTree(testData));
        assertDoesNotThrow(() -> etlService.processData(testData));
    }

    @Test
    void testProcessDataWithInvalidJson() throws JsonProcessingException {
        String testData = "invalid json";
        when(objectMapper.readTree(testData)).thenThrow(new JsonProcessingException("Invalid JSON") {});

        assertThrows(RuntimeException.class, () -> etlService.processData(testData));
    }
}
