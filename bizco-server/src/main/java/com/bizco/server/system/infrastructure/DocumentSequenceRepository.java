package com.bizco.server.system.infrastructure;

import java.sql.Date;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Concurrency-safe allocator over the shared {@code document_sequences} table
 * (DatabaseDesign.md &sect;10). Every allocation happens as an
 * {@code INSERT ... ON CONFLICT} followed by a row-locked {@code UPDATE} inside the
 * caller's transaction, so a rollback also rolls back the allocation and no two
 * concurrent callers can ever receive the same number. {@code MAX(number)+1} is never used.
 *
 * <p>Master-data codes (customer, supplier, ...) use {@link #nextGlobalValue} — one
 * never-reset counter per document type. Business documents that must reset daily
 * (invoices, credit notes: MVP.md &sect;5.5 "INV-YYYYMMDD-NNNN") use
 * {@link #nextDailyValue}, which scopes the counter to a given business date.
 */
@Repository
public class DocumentSequenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentSequenceRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** One never-resetting counter per {@code sequenceKey} (e.g. master-data codes). */
    public long nextGlobalValue(final String sequenceKey, final String prefix, final int padding) {
        return allocate(sequenceKey, prefix, null, padding);
    }

    /**
     * One counter per {@code documentType} per {@code businessDate}, so numbering resets
     * daily. {@code businessDate} must be the same operational date used elsewhere for the
     * document (not simply "now"), so late-night postings stay consistent with the day's
     * other documents.
     */
    public long nextDailyValue(final String documentType, final LocalDate businessDate, final String prefix,
                               final int padding) {
        return allocate(documentType + ":" + businessDate, prefix, businessDate, padding);
    }

    /** Formats an allocated value as {@code PREFIX-YYYYMMDD-NNNN} per MVP.md &sect;5.5. */
    public String formatDaily(final String prefix, final LocalDate businessDate, final long value, final int padding) {
        return prefix + "-" + businessDate.toString().replace("-", "") + "-" + pad(value, padding);
    }

    private long allocate(final String sequenceKey, final String prefix, final LocalDate businessDate,
                          final int padding) {
        jdbcTemplate.update("""
                insert into document_sequences(sequence_key, prefix, sequence_date, next_value, padding)
                values (?, ?, ?, 1, ?)
                on conflict (sequence_key) do nothing
                """, sequenceKey, prefix, businessDate == null ? null : Date.valueOf(businessDate), padding);
        final Long value = jdbcTemplate.queryForObject("""
                select next_value
                from document_sequences
                where sequence_key = ?
                for update
                """, Long.class, sequenceKey);
        jdbcTemplate.update("""
                update document_sequences
                set next_value = next_value + 1, updated_at = current_timestamp, version = version + 1
                where sequence_key = ?
                """, sequenceKey);
        return value == null ? 1 : value;
    }

    private String pad(final long value, final int padding) {
        return String.format("%0" + padding + "d", value);
    }
}
