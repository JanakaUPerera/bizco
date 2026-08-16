package com.bizco.server.idempotency.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Raw-JDBC (not JPA) access to {@code idempotency_records}, mirroring
 * {@code DocumentSequenceRepository}: the claim/complete split below relies on PostgreSQL's own
 * {@code INSERT ... ON CONFLICT} row-level serialization to make concurrent requests with the
 * same {@code Idempotency-Key} safe, which a plain JPA check-then-save would not guarantee.
 */
@Repository
public class IdempotencyRecordRepository {

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyRecordRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Attempts to claim {@code requestId} for this operation. PostgreSQL serializes concurrent
     * claims of the same key: only one caller ever receives {@code true} for a given key; a
     * concurrent second caller blocks until the first commits or rolls back, then sees
     * {@code false} (if the first committed) or claims it itself (if the first rolled back).
     */
    public boolean claim(final UUID requestId, final String operation, final String requestHash) {
        final int rows = jdbcTemplate.update("""
                insert into idempotency_records(request_id, operation, request_hash)
                values (?, ?, ?)
                on conflict (request_id) do nothing
                """, requestId, operation, requestHash);
        return rows == 1;
    }

    public Optional<ExistingRecord> find(final UUID requestId) {
        return jdbcTemplate.query("""
                select operation, request_hash, response_code, response_body
                from idempotency_records
                where request_id = ?
                """, (rs, rowNum) -> new ExistingRecord(
                        rs.getString("operation"),
                        rs.getString("request_hash"),
                        rs.getObject("response_code") == null ? null : rs.getInt("response_code"),
                        rs.getString("response_body")),
                requestId).stream().findFirst();
    }

    /** Fills in the result of a successfully-claimed request. Only ever called once per key. */
    public void complete(final UUID requestId, final int responseCode, final String responseBodyJson,
                         final String resourceType, final UUID resourceId) {
        jdbcTemplate.update("""
                update idempotency_records
                set response_code = ?, response_body = ?::jsonb, resource_type = ?, resource_id = ?
                where request_id = ?
                """, responseCode, responseBodyJson, resourceType, resourceId, requestId);
    }

    public record ExistingRecord(String operation, String requestHash, Integer responseCode, String responseBodyJson) {
        public boolean isComplete() {
            return responseBodyJson != null;
        }
    }
}
