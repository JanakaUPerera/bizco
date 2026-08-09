package com.bizco.server.support.backup;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PgDumpRestoreTestUtility {

    private PgDumpRestoreTestUtility() {
    }

    public record DatabaseTarget(String host, int port, String database, String username, String password) {
    }

    public record CommandSpec(List<String> command, String passwordEnvironmentValue) {
        public boolean exposesPasswordInCommand() {
            if (passwordEnvironmentValue == null || passwordEnvironmentValue.isBlank()) {
                return false;
            }
            return command.stream().anyMatch(part -> part.contains(passwordEnvironmentValue));
        }
    }

    public static CommandSpec dumpCommand(final Path pgDump, final DatabaseTarget database,
                                          final Path outputFile) {
        final List<String> command = new ArrayList<>();
        command.add(pgDump.toString());
        command.add("--format=custom");
        command.add("--file=" + outputFile);
        addConnectionArgs(command, database);
        return new CommandSpec(List.copyOf(command), database.password());
    }

    public static CommandSpec restoreCommand(final Path pgRestore, final DatabaseTarget database,
                                             final Path backupFile) {
        final List<String> command = new ArrayList<>();
        command.add(pgRestore.toString());
        command.add("--no-owner");
        command.add("--no-privileges");
        addConnectionArgs(command, database);
        command.add(backupFile.toString());
        return new CommandSpec(List.copyOf(command), database.password());
    }

    private static void addConnectionArgs(final List<String> command, final DatabaseTarget database) {
        command.add("--host=" + database.host());
        command.add("--port=" + database.port());
        command.add("--username=" + database.username());
        command.add("--dbname=" + database.database());
    }
}
