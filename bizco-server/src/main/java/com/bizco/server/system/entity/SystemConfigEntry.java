package com.bizco.server.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A single key/value row in the generic {@code system_config} table (currency code, timezone,
 * and other non-tax business settings). Each row carries its own optimistic-lock version rather
 * than the whole table sharing one, since unrelated settings are edited independently.
 *
 * <p>{@code configValue} holds the already-JSON-encoded text of the value (e.g. {@code "LKR"}
 * with the quotes, or {@code true}), not the unwrapped Java value. Hibernate's generic
 * {@code @JdbcTypeCode(SqlTypes.JSON)} binder round-trips a {@code String}-typed field as raw
 * JSON text; a field declared as {@code Object} does not reliably (de)serialize plain scalars
 * (strings/booleans) the same way it does for structured {@code Map}/POJO values. Encoding and
 * decoding the actual value happens in {@code SystemConfigService} via Jackson.
 */
@Entity
@Table(name = "system_config")
public class SystemConfigEntry {

    @Id
    @Column(name = "config_key", length = 100)
    private String configKey;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_value", nullable = false)
    private String configValueJson;
    @Column(length = 255)
    private String description;
    private UUID updatedBy;
    private Instant updatedAt = Instant.now();
    @Version
    private long version;

    protected SystemConfigEntry() {
    }

    public SystemConfigEntry(final String configKey, final String configValueJson, final String description) {
        this.configKey = configKey;
        this.configValueJson = configValueJson;
        this.description = description;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getConfigValueJson() {
        return configValueJson;
    }

    public String getDescription() {
        return description;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void update(final String configValueJson, final String description, final UUID updatedBy) {
        this.configValueJson = configValueJson;
        this.description = description;
        this.updatedBy = updatedBy;
    }
}
