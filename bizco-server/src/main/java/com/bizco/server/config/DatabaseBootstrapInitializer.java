package com.bizco.server.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/** Creates the configured PostgreSQL role and database on a first development installation. */
public final class DatabaseBootstrapInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseBootstrapInitializer.class);
    private static final String DEFAULT_ADMIN_DATABASE = "postgres";

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
        final Environment environment = applicationContext.getEnvironment();
        if (!environment.getProperty("bizco.database-bootstrap.enabled", Boolean.class, true)) {
            return;
        }

        final BootstrapSettings settings = BootstrapSettings.from(environment);
        try {
            if (canConnect(settings.targetUrl(), settings.applicationUsername(), settings.applicationPassword())) {
                return;
            }
            bootstrap(settings);
            verifyApplicationConnection(settings);
        } catch (SQLException exception) {
            throw bootstrapFailure(exception);
        }
    }

    private boolean canConnect(final String url, final String username, final String password) throws SQLException {
        try (Connection ignored = DriverManager.getConnection(url, username, password)) {
            return true;
        } catch (SQLException exception) {
            if (isMissingDatabaseOrRole(exception)) {
                return false;
            }
            throw exception;
        }
    }

    private void bootstrap(final BootstrapSettings settings) throws SQLException {
        try (Connection admin = DriverManager.getConnection(
                settings.adminUrl(), settings.adminUsername(), settings.adminPassword())) {
            final boolean roleCreated = createRoleIfMissing(admin, settings);
            final boolean databaseCreated = createDatabaseIfMissing(admin, settings);
            grantDatabasePrivileges(admin, settings);
            grantSchemaPrivileges(settings);

            if (roleCreated) {
                LOGGER.info("Created the configured PostgreSQL application role");
            }
            if (databaseCreated) {
                LOGGER.info("Created the configured Bizco PostgreSQL database");
            }
        }
    }

    private boolean createRoleIfMissing(final Connection admin, final BootstrapSettings settings) throws SQLException {
        if (exists(admin, "select 1 from pg_roles where rolname = ?", settings.applicationUsername())) {
            return false;
        }
        if (settings.applicationPassword().isBlank()) {
            throw new IllegalStateException(
                    "BIZCO_DATASOURCE_PASSWORD is required when creating the PostgreSQL application role");
        }

        try (Statement statement = admin.createStatement()) {
            statement.executeUpdate("CREATE ROLE " + quoteIdentifier(settings.applicationUsername())
                    + " LOGIN PASSWORD " + quoteLiteral(settings.applicationPassword()));
        }
        return true;
    }

    private boolean createDatabaseIfMissing(final Connection admin, final BootstrapSettings settings)
            throws SQLException {
        if (exists(admin, "select 1 from pg_database where datname = ?", settings.databaseName())) {
            return false;
        }

        try (Statement statement = admin.createStatement()) {
            statement.executeUpdate("CREATE DATABASE " + quoteIdentifier(settings.databaseName())
                    + " OWNER " + quoteIdentifier(settings.applicationUsername()));
        }
        return true;
    }

    private void grantDatabasePrivileges(final Connection admin, final BootstrapSettings settings) throws SQLException {
        try (Statement statement = admin.createStatement()) {
            statement.executeUpdate("GRANT CONNECT, CREATE, TEMPORARY ON DATABASE "
                    + quoteIdentifier(settings.databaseName()) + " TO "
                    + quoteIdentifier(settings.applicationUsername()));
        }
    }

    private void grantSchemaPrivileges(final BootstrapSettings settings) throws SQLException {
        try (Connection targetAdmin = DriverManager.getConnection(
                settings.targetAdminUrl(), settings.adminUsername(), settings.adminPassword());
                Statement statement = targetAdmin.createStatement()) {
            final String role = quoteIdentifier(settings.applicationUsername());
            statement.executeUpdate("GRANT USAGE, CREATE ON SCHEMA public TO " + role);
            statement.executeUpdate("GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO " + role);
            statement.executeUpdate("GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO " + role);
            statement.executeUpdate("GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO " + role);
        }
    }

    private boolean exists(final Connection connection, final String sql, final String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void verifyApplicationConnection(final BootstrapSettings settings) throws SQLException {
        try (Connection ignored = DriverManager.getConnection(
                settings.targetUrl(), settings.applicationUsername(), settings.applicationPassword())) {
            LOGGER.info("PostgreSQL first-run bootstrap completed");
        }
    }

    private boolean isMissingDatabaseOrRole(final SQLException exception) {
        final String state = exception.getSQLState();
        return "3D000".equals(state) || state != null && state.startsWith("28");
    }

    private IllegalStateException bootstrapFailure(final SQLException exception) {
        return new IllegalStateException(
                "PostgreSQL first-run bootstrap failed (SQL state " + exception.getSQLState()
                        + "). Check the datasource and BIZCO_DATABASE_BOOTSTRAP_ADMIN_* settings.",
                exception);
    }

    private String quoteIdentifier(final String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private String quoteLiteral(final String value) {
        if (value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("PostgreSQL passwords cannot contain a null character");
        }
        return '\'' + value.replace("'", "''") + '\'';
    }

    private record BootstrapSettings(
            String targetUrl,
            String targetAdminUrl,
            String databaseName,
            String applicationUsername,
            String applicationPassword,
            String adminUrl,
            String adminUsername,
            String adminPassword) {

        private static BootstrapSettings from(final Environment environment) {
            final String targetUrl = required(environment, "spring.datasource.url");
            final String databaseName = databaseName(targetUrl);
            final String applicationUsername = required(environment, "spring.datasource.username");
            final String applicationPassword = environment.getProperty("spring.datasource.password", "");
            final String configuredAdminUrl = environment.getProperty("bizco.database-bootstrap.admin-url", "");
            final String adminUrl = configuredAdminUrl.isBlank()
                    ? replaceDatabase(targetUrl, DEFAULT_ADMIN_DATABASE)
                    : configuredAdminUrl;
            final String configuredAdminUsername = environment.getProperty(
                    "bizco.database-bootstrap.admin-username", "");
            final boolean dedicatedAdminConfigured = !configuredAdminUsername.isBlank();

            return new BootstrapSettings(
                    targetUrl,
                    replaceDatabase(adminUrl, databaseName),
                    databaseName,
                    applicationUsername,
                    applicationPassword,
                    adminUrl,
                    dedicatedAdminConfigured ? configuredAdminUsername : applicationUsername,
                    dedicatedAdminConfigured
                            ? environment.getProperty("bizco.database-bootstrap.admin-password", "")
                            : applicationPassword);
        }

        private static String required(final Environment environment, final String property) {
            final String value = environment.getProperty(property);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Required configuration property is missing: " + property);
            }
            return value;
        }

        private static String databaseName(final String jdbcUrl) {
            final int queryStart = jdbcUrl.indexOf('?');
            final String withoutQuery = queryStart < 0 ? jdbcUrl : jdbcUrl.substring(0, queryStart);
            final int slash = withoutQuery.lastIndexOf('/');
            if (!jdbcUrl.startsWith("jdbc:postgresql://") || slash < "jdbc:postgresql://".length()
                    || slash == withoutQuery.length() - 1) {
                throw new IllegalStateException(
                        "First-run bootstrap requires a standard jdbc:postgresql://host:port/database URL");
            }
            return withoutQuery.substring(slash + 1);
        }

        private static String replaceDatabase(final String jdbcUrl, final String database) {
            final int queryStart = jdbcUrl.indexOf('?');
            final String query = queryStart < 0 ? "" : jdbcUrl.substring(queryStart);
            final String withoutQuery = queryStart < 0 ? jdbcUrl : jdbcUrl.substring(0, queryStart);
            final int slash = withoutQuery.lastIndexOf('/');
            if (!jdbcUrl.startsWith("jdbc:postgresql://") || slash < "jdbc:postgresql://".length()) {
                throw new IllegalStateException(
                        "First-run bootstrap requires a standard jdbc:postgresql://host:port/database URL");
            }
            return withoutQuery.substring(0, slash + 1) + database + query;
        }
    }
}
