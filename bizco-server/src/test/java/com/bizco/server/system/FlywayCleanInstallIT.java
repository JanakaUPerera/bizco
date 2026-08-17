package com.bizco.server.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.bizco.server.support.PostgresIntegrationTest;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FlywayCleanInstallIT extends PostgresIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    void cleanPostgresMigratesAndJpaValidationStarts() {
        final JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("026");
        assertThat(jdbc.queryForObject("select count(*) from roles", Long.class)).isGreaterThanOrEqualTo(8);
        assertThat(jdbc.queryForObject("select count(*) from permissions", Long.class)).isGreaterThan(40);
        assertThat(jdbc.queryForObject("select count(*) from role_permissions", Long.class)).isGreaterThan(0);
        assertThat(jdbc.queryForObject("""
                select count(*)
                from permissions p
                where not exists (
                    select 1
                    from roles r
                    join role_permissions rp on rp.role_id = r.role_id
                    where r.role_name = 'SUPER_ADMIN'
                    and rp.permission_code = p.permission_code
                )
                """, Long.class)).as("SEC-RBAC-007 SUPER_ADMIN covers every registered permission").isZero();
        assertThat(jdbc.queryForObject("""
                select count(*)
                from permissions
                where permission_code in (
                    'identity.user.read', 'identity.user.write', 'identity.role.read', 'identity.role.write',
                    'settings.business.read', 'settings.business.write',
                    'sales.pos.open', 'sales.invoice.write', 'sales.invoice.void'
                )
                """, Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from tax_configuration", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from pg_extension where extname in ('pgcrypto','btree_gist','pg_trgm')",
                Long.class)).isEqualTo(3);
    }

    @Test
    void sysInstall002FlywayMigrateIsRepeatableOnExistingSchema() {
        flyway.migrate();

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("026");
        assertThat(new JdbcTemplate(dataSource).queryForObject("""
                select count(*)
                from flyway_schema_history
                where success = true
                """, Long.class)).isGreaterThan(0);
    }
}
