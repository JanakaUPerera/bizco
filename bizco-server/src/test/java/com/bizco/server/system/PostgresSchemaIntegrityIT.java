package com.bizco.server.system;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.server.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresSchemaIntegrityIT extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void dbUsernameUniquenessIsCaseInsensitive() {
        final long roleId = roleId("CASHIER");
        insertUser("UniqueUser", roleId);

        assertThatThrownBy(() -> insertUser("uniqueuser", roleId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rolePermissionIntegrityRejectsUnknownReferences() {
        final long roleId = roleId("CASHIER");

        assertThatThrownBy(() -> jdbc.update("""
                insert into role_permissions(role_id, permission_code)
                values (?, ?)
                """, roleId, "missing.permission"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sessionTokenHashMustBeUnique() {
        final UUID userId = insertUser("session_unique", roleId("CASHIER"));
        insertSession(userId, "same-token-hash");

        assertThatThrownBy(() -> insertSession(userId, "same-token-hash"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void activeSecondaryRoleGrantMustBeUniquePerUserAndRole() {
        final UUID userId = insertUser("secondary_unique", roleId("CASHIER"));
        final long managerRoleId = roleId("MANAGER");
        insertSecondaryRole(userId, managerRoleId, Instant.now().plusSeconds(3600));

        assertThatThrownBy(() -> insertSecondaryRole(userId, managerRoleId, Instant.now().plusSeconds(7200)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void secondaryRoleExpiryMustBeAfterGrantTime() {
        final UUID userId = insertUser("secondary_expiry", roleId("CASHIER"));

        assertThatThrownBy(() -> jdbc.update("""
                insert into user_role_assignments(user_id, role_id, granted_at, expires_at, is_active)
                values (?, ?, ?, ?, true)
                """, userId, roleId("MANAGER"), Timestamp.from(Instant.parse("2026-01-01T10:00:00Z")),
                Timestamp.from(Instant.parse("2026-01-01T09:59:59Z"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void businessProfileIsSingleton() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into business_profile(business_profile_id, business_name)
                values (2, 'Another business')
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void taxConfigurationIsSingletonAndVatRateIsConstrained() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into tax_configuration(tax_configuration_id, vat_enabled, vat_rate)
                values (2, false, ?)
                """, BigDecimal.valueOf(18)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
                update tax_configuration
                set vat_rate = ?
                where tax_configuration_id = 1
                """, BigDecimal.valueOf(101)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long roleId(final String roleName) {
        return jdbc.queryForObject("select role_id from roles where role_name = ?", Long.class, roleName);
    }

    private UUID insertUser(final String username, final long roleId) {
        return jdbc.queryForObject("""
                insert into users(username, password_hash, first_name, primary_role_id, must_change_password)
                values (?, ?, ?, ?, false)
                returning user_id
                """, UUID.class, username, "$2a$12$testhashforpostgresintegration", username, roleId);
    }

    private void insertSession(final UUID userId, final String tokenHash) {
        jdbc.update("""
                insert into user_sessions(user_id, token_hash, client_id, ip_address, expires_at)
                values (?, ?, 'test-client', '127.0.0.1', ?)
                """, userId, tokenHash, Timestamp.from(Instant.now().plusSeconds(900)));
    }

    private void insertSecondaryRole(final UUID userId, final long roleId, final Instant expiresAt) {
        jdbc.update("""
                insert into user_role_assignments(user_id, role_id, expires_at, is_active)
                values (?, ?, ?, true)
                """, userId, roleId, Timestamp.from(expiresAt));
    }
}
