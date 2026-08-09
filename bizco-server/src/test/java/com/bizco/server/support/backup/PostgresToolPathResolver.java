package com.bizco.server.support.backup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class PostgresToolPathResolver {

    public static final String PG_DUMP_ENV = "BIZCO_PG_DUMP_PATH";
    public static final String PG_RESTORE_ENV = "BIZCO_PG_RESTORE_PATH";

    private PostgresToolPathResolver() {
    }

    public static Optional<Path> resolvePgDump(final Map<String, String> env) {
        return resolve("pg_dump", PG_DUMP_ENV, env);
    }

    public static Optional<Path> resolvePgRestore(final Map<String, String> env) {
        return resolve("pg_restore", PG_RESTORE_ENV, env);
    }

    public static Optional<Path> resolve(final String toolName, final String envVariable,
                                         final Map<String, String> env) {
        final String configured = env.get(envVariable);
        if (configured != null && !configured.isBlank()) {
            final Path path = Path.of(configured.trim()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException(envVariable + " does not point to an existing file");
            }
            return Optional.of(path);
        }
        final String pathValue = env.getOrDefault("PATH", "");
        for (final String entry : pathValue.split(java.io.File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            for (final String executableName : executableNames(toolName)) {
                final Path candidate = Path.of(entry).resolve(executableName).toAbsolutePath().normalize();
                if (Files.isRegularFile(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private static List<String> executableNames(final String toolName) {
        final List<String> names = new ArrayList<>();
        names.add(toolName);
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            names.add(toolName + ".exe");
            names.add(toolName + ".cmd");
            names.add(toolName + ".bat");
        }
        return names;
    }
}
