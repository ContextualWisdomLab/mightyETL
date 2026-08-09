package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.sql.ResultSet;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Proves that the lease repository rejects unsafe arguments and impossible claim transitions.
 */
class EtlJobLeaseRepositoryValidationTest {

    @Test
    void rejectsLeaseDurationsAboveTheOperationalSafetyCeilingBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        EtlJobLeaseRepository repository = new EtlJobLeaseRepository(
                jdbcTemplate,
                transactionManager
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.claimNext(
                        "worker-alpha",
                        Duration.ofSeconds(
                                EtlJobWorkerProperties.MAXIMUM_LEASE_DURATION_SECONDS + 1L
                        ),
                        3
                )
        );
        verifyNoInteractions(jdbcTemplate, transactionManager);
    }

    @Test
    void failsClosedWhenALockedCandidateCannotBeUpdated() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        ResultSet resultSet = mock(ResultSet.class);
        UUID jobRecordId = UUID.randomUUID();

        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        when(resultSet.getObject("job_record_id", UUID.class)).thenReturn(jobRecordId);
        when(resultSet.getString("principal_scope_hash")).thenReturn("a".repeat(64));
        when(resultSet.getString("submission_key_hash")).thenReturn("b".repeat(64));
        when(resultSet.getString("request_digest")).thenReturn("c".repeat(64));
        when(resultSet.getString("request_payload"))
                .thenReturn("[{\"id\":\"record_alpha\"}]");
        when(resultSet.getInt("attempt_count")).thenReturn(0);
        when(resultSet.getObject("database_now", OffsetDateTime.class))
                .thenReturn(OffsetDateTime.of(2026, 8, 5, 0, 0, 0, 0, ZoneOffset.UTC));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> rowMapper = (RowMapper<Object>) invocation.getArgument(1);
            return List.of(rowMapper.mapRow(resultSet, 0));
        }).when(jdbcTemplate).query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class)
        );

        EtlJobLeaseRepository repository = new EtlJobLeaseRepository(
                jdbcTemplate,
                transactionManager
        );

        assertThrows(
                IllegalStateException.class,
                () -> repository.claimNext("worker-alpha", Duration.ofMinutes(5), 3)
        );
    }
}
