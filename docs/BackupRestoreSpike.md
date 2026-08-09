# Backup/Restore Technical Spike

## Scope

This spike proves early technical feasibility for PostgreSQL `pg_dump` and `pg_restore` before Phase 2 starts. It is not the Week 16 administrator backup screen, scheduling service, restore workflow, maintenance-mode guard, or final audit implementation.

## Environment Tested

- Date: 2026-08-09
- Host OS: Windows development workstation
- Shell: Windows PowerShell 5.1.26100.8875
- Java: 21.0.10 LTS
- Docker: 29.6.1
- Automated database environment: Testcontainers PostgreSQL image `postgres:16-alpine`
- PostgreSQL server version in image: 16.14
- `pg_dump` version in image: 16.14
- `pg_restore` version in image: 16.14
- Maven verification command: `mvn verify -pl bizco-server -am`

This spike follows the finalized `docs/DevelopmentPlan.md` and the existing Phase 1 alignment report.

## Tool Path Strategy

The host workstation did not have `pg_dump` or `pg_restore` discoverable on `PATH` through PowerShell. For this spike, the automated restore proof uses the PostgreSQL client tools inside the supported Testcontainers PostgreSQL image.

A small test-scope utility was added to prove the intended safe path-resolution pattern:

- `BIZCO_PG_DUMP_PATH` can configure an explicit `pg_dump` executable path.
- `BIZCO_PG_RESTORE_PATH` can configure an explicit `pg_restore` executable path.
- If explicit paths are absent, `PATH` is searched.
- Invalid configured paths fail before command execution.
- Diagnostics do not include passwords.

This code lives under `bizco-server/src/test/java/.../support/backup` and is not production backup infrastructure.

## Credential Strategy

No database password is hardcoded in source.

For production Week 16 implementation, credentials should come from configuration/environment, matching the server datasource strategy:

- `BIZCO_DATASOURCE_URL`
- `BIZCO_DATASOURCE_USERNAME`
- `BIZCO_DATASOURCE_PASSWORD`
- future backup-tool path settings such as `BIZCO_PG_DUMP_PATH` and `BIZCO_PG_RESTORE_PATH`

Command arguments must not include passwords. If an external process is used, pass the password through the process environment, for example `PGPASSWORD`, or preferably use a secured `.pgpass`/service-account strategy where operationally suitable. Logs must redact commands and never print `PGPASSWORD`, datasource passwords, raw session tokens, or backup secrets.

## Commands/Strategy Used

The automated spike uses PostgreSQL custom-format dumps:

```text
pg_dump --format=custom --file=/tmp/bizco-spike.dump --username=bizco --dbname=bizco
dropdb --if-exists --username=bizco bizco_restore_spike
createdb --username=bizco bizco_restore_spike
pg_restore --no-owner --no-privileges --username=bizco --dbname=bizco_restore_spike /tmp/bizco-spike.dump
```

The test then copies the dump file from the container to a host temporary directory and verifies:

- output file exists;
- file size is greater than zero;
- restore command succeeds;
- `flyway_schema_history` exists and latest successful version is `021`;
- important Phase 1 data is readable from restored DB:
  - `roles`;
  - `permissions`;
  - `role_permissions`;
  - `tax_configuration`.

## Restore Result

PASS.

`PgDumpRestoreSpikeIT` successfully dumped the migrated Bizco Phase 1 database, restored it into a clean temporary PostgreSQL database in the same container, and verified schema/data readability.

`mvn verify -pl bizco-server -am` passed after the spike:

```text
Server PostgreSQL integration tests: 20 run, 0 failures, 0 errors, 0 skipped
Build: SUCCESS
```

## Failure Cases Investigated

Automated/semi-automated coverage now includes:

- `pg_dump` unavailable: path resolver returns empty when no configured path and no PATH match exist.
- `pg_restore` unavailable: path resolver returns empty when no configured path and no PATH match exist.
- invalid configured path: path resolver fails with a safe diagnostic.
- invalid backup: `pg_restore` returns non-zero for a non-backup file.
- destination unavailable/missing: `pg_restore` returns non-zero when the target database does not exist.
- credential safety: command specs keep passwords out of argument lists.

Week 16 should expand these into full service-level failure records, audit events, and user-facing diagnostics.

## Windows Considerations

- PostgreSQL client tools are often not on `PATH`; they may live under a PostgreSQL install directory such as `C:\Program Files\PostgreSQL\<version>\bin`.
- Explicit configured paths are safer than assuming PATH.
- Paths may contain spaces; use `ProcessBuilder` argument lists rather than string-built shell commands.
- Avoid logging full command environments.
- Testcontainers provides a repeatable fallback proof environment, but a packaged single-PC install must decide whether to bundle PostgreSQL tools, require a local PostgreSQL installation, or run backup tooling server-side only.

## Linux Considerations

- `pg_dump` and `pg_restore` are commonly available through PostgreSQL client packages, but version compatibility still matters.
- Prefer the same major PostgreSQL client version as the server, or a newer compatible client.
- Use least-privilege database roles where practical:
  - schema owner/migration role;
  - runtime app role;
  - backup role where operationally suitable.
- Avoid putting credentials in shell history, process arguments, or logs.

## Risks For Week 16

- Restore into the active production database is dangerous without maintenance mode, active-session guards, clear confirmation, and verified backup status.
- Files outside PostgreSQL, such as logos or generated retained exports, are not covered by database-only dumps.
- Large databases will need progress reporting, timeouts, disk-space checks, checksum verification, and backup retention policy.
- Windows path discovery and permissions need installer-level decisions.
- `pg_dump`/`pg_restore` version mismatch can cause avoidable failures.
- Final implementation must write `backup_records`, `restore_records`, and audit events with clear `STARTED`, `VERIFIED`, and `FAILED` states.

## Recommended Production Implementation

For Week 16, implement a server-side backup/restore service, not JavaFX-side database access:

1. Keep PostgreSQL credentials server-only.
2. Resolve `pg_dump`/`pg_restore` from explicit configuration first, then PATH as fallback.
3. Preflight tool existence, version, destination writeability, available disk space, and database connectivity.
4. Use custom-format dumps (`pg_dump -Fc`) plus SHA-256 checksums.
5. Store backup metadata in `backup_records`; store every restore attempt in `restore_records`.
6. Require `system.backup.create` and `system.backup.restore`.
7. Require maintenance-mode/active-session guard before restore.
8. Restore only verified backups.
9. Verify restored DB by checking PostgreSQL readability, Flyway schema version, and application smoke checks.
10. Redact secrets in all logs and API errors.
