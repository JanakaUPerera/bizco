package com.bizco.server.customer.infrastructure;

import java.sql.Date;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentSequenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentSequenceRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long nextGlobalValue(final String key, final String prefix, final int padding) {
        jdbcTemplate.update("""
                insert into document_sequences(sequence_key, prefix, sequence_date, next_value, padding)
                values (?, ?, null, 1, ?)
                on conflict (sequence_key) do nothing
                """, key, prefix, padding);
        final Long value = jdbcTemplate.queryForObject("""
                select next_value
                from document_sequences
                where sequence_key = ?
                for update
                """, Long.class, key);
        jdbcTemplate.update("""
                update document_sequences
                set next_value = next_value + 1, updated_at = current_timestamp, version = version + 1
                where sequence_key = ?
                """, key);
        return value == null ? 1 : value;
    }
}

