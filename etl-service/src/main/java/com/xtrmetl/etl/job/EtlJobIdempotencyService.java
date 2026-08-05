package com.xtrmetl.etl.job;

import com.xtrmetl.etl.service.EtlRequestLock;
import com.xtrmetl.etl.service.EtlService;
import com.xtrmetl.etl.service.Sha256Digest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;

/**
 * Reuses the durable ETL response ledger with hashed job identity only.
 *
 * <p>The accepted job stores independent hashes of its authenticated principal and normalized
 * submission key. This service domain-separates and hashes those values into one response-ledger
 * key, verifies the exact retained payload digest, serializes execution with the existing
 * transaction-level request lock, replays an existing matching response, or writes the target and
 * response ledger in the surrounding transaction. Raw principals and client keys are neither
 * required nor reconstructed.</p>
 *
 * <p>One invocation represents exactly one persisted durable attempt. It therefore calls the
 * non-retrying ETL entry point that joins the current lease transaction; transient failures escape
 * to {@link EtlJobWorker}, which owns bounded retry accounting in {@code attempt_count}.</p>
 */
@Service
public class EtlJobIdempotencyService {

    private static final String LEDGER_KEY_DOMAIN = "mightyetl:durable-job:v1:";
    private static final String SELECT_LEDGER_SQL = """
            SELECT request_digest, response_body
            FROM etl_idempotency_records
            WHERE idempotency_key_hash = ?
            """;
    private static final String INSERT_LEDGER_SQL = """
            INSERT INTO etl_idempotency_records (
                idempotency_key_hash,
                request_digest,
                response_body
            ) VALUES (?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final EtlService etlService;
    private final EtlRequestLock requestLock;

    /**
     * Creates the hashed durable-job response-ledger adapter.
     *
     * @param jdbcTemplate parameterized response-ledger database access
     * @param etlService validated ETL target writer
     * @param requestLock transaction-lifetime response-ledger lock
     */
    public EtlJobIdempotencyService(
            JdbcTemplate jdbcTemplate,
            EtlService etlService,
            EtlRequestLock requestLock
    ) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
        );
        this.etlService = Objects.requireNonNull(etlService, "etlService must not be null");
        this.requestLock = Objects.requireNonNull(requestLock, "requestLock must not be null");
    }

    /**
     * Executes or replays one durable job inside a real database transaction.
     *
     * @param lease exact live claim carrying hashed execution identity and retained payload
     * @return newly generated or replayed stable response body
     * @throws NullPointerException when the lease is {@code null}
     * @throws IllegalStateException when invoked without an actual transaction
     * @throws EtlJobIntegrityException when retained payload or ledger identity conflicts
     * @throws CannotAcquireLockException when another transaction owns the execution ledger key
     */
    @Transactional
    public String process(EtlJobLease lease) {
        EtlJobLease requiredLease = Objects.requireNonNull(lease, "lease must not be null");
        requireActiveTransaction();
        if (!Sha256Digest.digest(requiredLease.requestPayload()).equals(
                requiredLease.requestDigest()
        )) {
            throw new EtlJobIntegrityException();
        }

        String ledgerKeyHash = Sha256Digest.digest(
                LEDGER_KEY_DOMAIN
                        + requiredLease.principalScopeHash()
                        + ':'
                        + requiredLease.submissionKeyHash()
        );
        if (!requestLock.tryLock(ledgerKeyHash)) {
            throw new CannotAcquireLockException("Durable ETL execution ledger is busy");
        }

        List<StoredResponse> storedResponses = jdbcTemplate.query(
                SELECT_LEDGER_SQL,
                (resultSet, rowNumber) -> new StoredResponse(
                        resultSet.getString("request_digest"),
                        resultSet.getString("response_body")
                ),
                ledgerKeyHash
        );
        if (!storedResponses.isEmpty()) {
            StoredResponse storedResponse = storedResponses.getFirst();
            if (!storedResponse.requestDigest().equals(requiredLease.requestDigest())) {
                throw new EtlJobIntegrityException();
            }
            return storedResponse.responseBody();
        }

        String responseBody = etlService.processDataInExistingTransaction(
                requiredLease.requestPayload()
        );
        jdbcTemplate.update(
                INSERT_LEDGER_SQL,
                ledgerKeyHash,
                requiredLease.requestDigest(),
                responseBody
        );
        return responseBody;
    }

    private static void requireActiveTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Durable ETL job execution requires an active transaction"
            );
        }
    }

    private record StoredResponse(String requestDigest, String responseBody) {
        private StoredResponse {
            Objects.requireNonNull(requestDigest, "requestDigest must not be null");
            Objects.requireNonNull(responseBody, "responseBody must not be null");
        }
    }
}
