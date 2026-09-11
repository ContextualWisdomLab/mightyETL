package com.xtrmetl.etl.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.service.EtlBatchProperties;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import com.xtrmetl.etl.service.EtlRequestLock;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Exercises defensive JSON-tree outcomes that ordinary Jackson parsing cannot reliably manufacture.
 *
 * <p>The service accepts an application-provided {@link ObjectMapper}, copies it, and therefore owns
 * the boundary between parser output and durable admission. These tests keep that boundary fail-closed
 * when a compatible mapper reports end-of-input without a tree or exposes a Java {@code null} array
 * element instead of Jackson's usual {@code NullNode}. Neither case may reach transaction locks or
 * persistence.</p>
 */
class EtlJobServiceDefensiveBranchTest {

    private static final String IDEMPOTENCY_KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final String PRINCIPAL_SCOPE = "tenant_alpha";

    @Test
    void rejectsParserEndOfInputWhenMapperProducesNoTree() throws Exception {
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

    @Test
    void rejectsNullParserArrayElementBeforeTransactionOrPersistence() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlRequestLock requestLock = mock(EtlRequestLock.class);
        ObjectMapper sourceMapper = mock(ObjectMapper.class);
        ObjectMapper copiedMapper = mock(ObjectMapper.class);
        JsonNode root = mock(JsonNode.class);
        when(sourceMapper.copy()).thenReturn(copiedMapper);
        when(copiedMapper.readTree("parser-null-array-element")).thenReturn(root);
        when(root.isArray()).thenReturn(true);
        when(root.size()).thenReturn(1);
        when(root.iterator()).thenReturn(Collections.singletonList((JsonNode) null).iterator());
        EtlJobService service = new EtlJobService(
                jdbcTemplate,
                sourceMapper,
                new EtlBatchProperties(),
                requestLock
        );

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> service.submit("parser-null-array-element", IDEMPOTENCY_KEY, PRINCIPAL_SCOPE)
        );

        assertEquals(EtlRequestError.INVALID_RECORD, exception.error());
        verifyNoInteractions(requestLock, jdbcTemplate);
    }
}
