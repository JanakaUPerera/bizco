package com.bizco.server.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.dto.system.SystemRequests.SystemConfigUpsertRequest;
import com.bizco.common.dto.system.SystemResponses.SystemConfigEntryResponse;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.support.PostgresIntegrationTest;
import com.bizco.server.system.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the {@code system_config.config_value JSONB} column round-trips through Hibernate's
 * generic {@code Object}-typed mapping against real PostgreSQL, including the plain JSON string
 * scalars ({@code "LKR"}, {@code "Asia/Colombo"}) seeded by V009 — a plain map-shaped JSON type
 * mapping would not have covered this correctly.
 */
@Transactional
@Rollback
class SystemConfigServicePostgresIT extends PostgresIntegrationTest {

    @Autowired
    private SystemConfigService service;

    @Test
    void seededScalarJsonValuesRoundTrip() {
        final SystemConfigEntryResponse currency = service.get("business.currency_code");
        final SystemConfigEntryResponse timezone = service.get("business.timezone");

        assertThat(currency.configValue()).isEqualTo("LKR");
        assertThat(timezone.configValue()).isEqualTo("Asia/Colombo");
    }

    @Test
    void newKeyCanBeCreatedAndUpdated() {
        final SystemConfigEntryResponse created = service.upsert("test.feature_flag",
                new SystemConfigUpsertRequest(true, "Test-only flag", 0L), null);
        assertThat(created.configValue()).isEqualTo(true);

        final SystemConfigEntryResponse updated = service.upsert("test.feature_flag",
                new SystemConfigUpsertRequest(false, "Test-only flag", created.version()), null);

        assertThat(updated.configValue()).isEqualTo(false);
        assertThat(service.get("test.feature_flag").configValue()).isEqualTo(false);
    }

    @Test
    void staleVersionIsRejectedAgainstRealDatabase() {
        final SystemConfigEntryResponse currency = service.get("business.currency_code");

        assertThatThrownBy(() -> service.upsert("business.currency_code",
                new SystemConfigUpsertRequest("USD", "Default currency", currency.version() + 1), null))
                .isInstanceOf(IdentityException.class);
    }
}
