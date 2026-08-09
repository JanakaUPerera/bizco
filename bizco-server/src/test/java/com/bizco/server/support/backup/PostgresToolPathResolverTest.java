package com.bizco.server.support.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostgresToolPathResolverTest {

    @TempDir
    private Path tempDir;

    @Test
    void configuredPgDumpPathWinsOverPathLookup() throws Exception {
        final Path configured = Files.createFile(tempDir.resolve("configured-pg-dump"));
        final Path pathDir = Files.createDirectory(tempDir.resolve("bin"));
        Files.createFile(pathDir.resolve("pg_dump"));

        assertThat(PostgresToolPathResolver.resolvePgDump(Map.of(
                PostgresToolPathResolver.PG_DUMP_ENV, configured.toString(),
                "PATH", pathDir.toString())))
                .contains(configured.toAbsolutePath().normalize());
    }

    @Test
    void missingConfiguredPgRestorePathFailsWithSafeDiagnostic() {
        assertThatThrownBy(() -> PostgresToolPathResolver.resolvePgRestore(Map.of(
                PostgresToolPathResolver.PG_RESTORE_ENV, tempDir.resolve("missing").toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(PostgresToolPathResolver.PG_RESTORE_ENV)
                .hasMessageNotContaining("password");
    }

    @Test
    void pathLookupReturnsEmptyWhenToolIsUnavailable() {
        assertThat(PostgresToolPathResolver.resolvePgDump(Map.of("PATH", tempDir.toString()))).isEmpty();
    }

    @Test
    void commandSpecsKeepPasswordsOutOfArguments() {
        final var database = new PgDumpRestoreTestUtility.DatabaseTarget(
                "localhost", 5432, "bizco", "bizco", "secret-password");

        assertThat(PgDumpRestoreTestUtility.dumpCommand(Path.of("pg_dump"), database,
                tempDir.resolve("bizco.dump")).exposesPasswordInCommand()).isFalse();
        assertThat(PgDumpRestoreTestUtility.restoreCommand(Path.of("pg_restore"), database,
                tempDir.resolve("bizco.dump")).exposesPasswordInCommand()).isFalse();
    }
}
