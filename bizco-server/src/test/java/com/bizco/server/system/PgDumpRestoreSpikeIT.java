package com.bizco.server.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.bizco.server.support.PostgresIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.Container;

class PgDumpRestoreSpikeIT extends PostgresIntegrationTest {

    private static final String DUMP_PATH_IN_CONTAINER = "/tmp/bizco-spike.dump";
    private static final String RESTORE_DATABASE = "bizco_restore_spike";

    @TempDir
    private Path tempDir;

    @Test
    void pgDumpAndPgRestoreCanRoundTripPhase1Database() throws Exception {
        assertSuccessful(POSTGRES.execInContainer("sh", "-c", "command -v pg_dump"));
        assertSuccessful(POSTGRES.execInContainer("sh", "-c", "command -v pg_restore"));

        final Container.ExecResult dump = POSTGRES.execInContainer("pg_dump",
                "--format=custom",
                "--file=" + DUMP_PATH_IN_CONTAINER,
                "--username=" + POSTGRES.getUsername(),
                "--dbname=" + POSTGRES.getDatabaseName());
        assertSuccessful(dump);

        final Path hostDump = tempDir.resolve("bizco-spike.dump");
        POSTGRES.copyFileFromContainer(DUMP_PATH_IN_CONTAINER, hostDump.toString());
        assertThat(hostDump).exists().isRegularFile();
        assertThat(Files.size(hostDump)).isGreaterThan(0);

        recreateRestoreDatabase();
        final Container.ExecResult restore = POSTGRES.execInContainer("pg_restore",
                "--no-owner",
                "--no-privileges",
                "--username=" + POSTGRES.getUsername(),
                "--dbname=" + RESTORE_DATABASE,
                DUMP_PATH_IN_CONTAINER);
        assertSuccessful(restore);

        assertRestoredDatabaseReadable();
    }

    @Test
    void pgRestoreFailureCasesAreDetectable() throws Exception {
        POSTGRES.execInContainer("sh", "-c", "printf 'not a postgres custom backup' > /tmp/invalid-bizco.dump");
        recreateRestoreDatabase();

        final Container.ExecResult invalidBackup = POSTGRES.execInContainer("pg_restore",
                "--username=" + POSTGRES.getUsername(),
                "--dbname=" + RESTORE_DATABASE,
                "/tmp/invalid-bizco.dump");
        assertThat(invalidBackup.getExitCode()).isNotZero();
        assertThat(invalidBackup.getStderr()).containsIgnoringCase("input file");

        final Container.ExecResult missingDestination = POSTGRES.execInContainer("pg_restore",
                "--username=" + POSTGRES.getUsername(),
                "--dbname=missing_destination_db",
                "/tmp/invalid-bizco.dump");
        assertThat(missingDestination.getExitCode()).isNotZero();
        assertThat(missingDestination.getStderr()).isNotBlank();
    }

    private void recreateRestoreDatabase() throws Exception {
        assertSuccessful(POSTGRES.execInContainer("dropdb",
                "--if-exists",
                "--username=" + POSTGRES.getUsername(),
                RESTORE_DATABASE));
        assertSuccessful(POSTGRES.execInContainer("createdb",
                "--username=" + POSTGRES.getUsername(),
                RESTORE_DATABASE));
    }

    private void assertRestoredDatabaseReadable() throws Exception {
        try (var connection = DriverManager.getConnection(restoredJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            assertThat(queryString(statement, """
                    select version
                    from flyway_schema_history
                    where success = true
                    order by installed_rank desc
                    limit 1
                    """)).isEqualTo("021");
            assertThat(queryLong(statement, "select count(*) from roles")).isGreaterThanOrEqualTo(8);
            assertThat(queryLong(statement, "select count(*) from permissions")).isGreaterThan(40);
            assertThat(queryLong(statement, "select count(*) from role_permissions")).isGreaterThan(0);
            assertThat(queryLong(statement, "select count(*) from tax_configuration")).isEqualTo(1);
        }
    }

    private String restoredJdbcUrl() {
        return POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + RESTORE_DATABASE);
    }

    private String queryString(final java.sql.Statement statement, final String sql) throws Exception {
        try (var resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private long queryLong(final java.sql.Statement statement, final String sql) throws Exception {
        try (var resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private void assertSuccessful(final Container.ExecResult result) {
        assertThat(result.getExitCode())
                .as("stdout=%s stderr=%s", result.getStdout(), result.getStderr())
                .isZero();
    }
}
