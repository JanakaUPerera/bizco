package com.bizco.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bizco.server.support.PostgresIntegrationTest;
import java.sql.DriverManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

class DatabaseBootstrapInitializerIT extends PostgresIntegrationTest {

    private static final String DATABASE = "bizco_first_run_test";
    private static final String USERNAME = "bizco_first_run_user";
    private static final String PASSWORD = "first-run-test-password";

    @Test
    void createsMissingApplicationRoleAndDatabase() throws Exception {
        final String targetUrl = replaceDatabase(POSTGRES.getJdbcUrl(), DATABASE);
        final GenericApplicationContext context = new GenericApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("bootstrap-test", Map.of(
                "spring.datasource.url", targetUrl,
                "spring.datasource.username", USERNAME,
                "spring.datasource.password", PASSWORD,
                "bizco.database-bootstrap.enabled", "true",
                "bizco.database-bootstrap.admin-url", replaceDatabase(POSTGRES.getJdbcUrl(), "postgres"),
                "bizco.database-bootstrap.admin-username", POSTGRES.getUsername(),
                "bizco.database-bootstrap.admin-password", POSTGRES.getPassword())));

        final DatabaseBootstrapInitializer initializer = new DatabaseBootstrapInitializer();
        initializer.initialize(context);
        initializer.initialize(context);

        try (var connection = DriverManager.getConnection(targetUrl, USERNAME, PASSWORD);
                var statement = connection.createStatement();
                var result = statement.executeQuery("select current_database(), current_user")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo(DATABASE);
            assertThat(result.getString(2)).isEqualTo(USERNAME);
        }
    }

    private String replaceDatabase(final String jdbcUrl, final String database) {
        final int queryStart = jdbcUrl.indexOf('?');
        final String query = queryStart < 0 ? "" : jdbcUrl.substring(queryStart);
        final String withoutQuery = queryStart < 0 ? jdbcUrl : jdbcUrl.substring(0, queryStart);
        return withoutQuery.substring(0, withoutQuery.lastIndexOf('/') + 1) + database + query;
    }
}
