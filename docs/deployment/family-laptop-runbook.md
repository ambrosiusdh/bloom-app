# Bloom backend: family Windows laptop runbook

This runbook installs and operates the Bloom Spring Boot backend directly on one
Windows laptop. It intentionally uses no Docker and no cloud services. The
deployment profile listens only on the laptop by default.

## Noob-friendly TL;DR

1. Install Java 21 and PostgreSQL 15 or newer.
2. Create one empty PostgreSQL database named `bloom_app` and one non-admin
   database user named `bloom_app`.
3. Open PowerShell, set the three database environment variables shown below,
   and start the JAR with the `deployment` profile.
4. On the first start, Flyway creates/upgrades the database. Hibernate only
   checks the finished schema; it cannot change it.
5. Open `http://127.0.0.1:8080/actuator/health`. `{"status":"UP"}` means the
   backend and its database connection are ready.
6. Stop the app with **Ctrl+C**. Back up the database with `pg_dump` before every
   upgrade. Never edit a migration that has already been released.

The database password is not in Git or in the JAR. It must be supplied on the
laptop. The old `postgres/admin` development connection is no longer a fallback;
if it was ever used outside development, rotate that PostgreSQL password.

## Deployment design and safety boundaries

- Required Java version: 21.
- Tested database family: PostgreSQL 15. Use a currently supported PostgreSQL
  version that has first been rehearsed with this release.
- Build entry point on Windows: `mvnw.cmd`. It pins Maven 3.9.10 and now quotes
  Maven's resolved path, so both the Windows username and repository path may
  contain spaces.
- Runtime profile: `deployment`.
- Default bind address: `127.0.0.1`. Other computers cannot connect unless an
  operator deliberately changes `BLOOM_SERVER_ADDRESS` and configures Windows
  Firewall. That broader network deployment is outside this runbook.
- Schema owner: Flyway only. Hibernate uses `ddl-auto=validate`.
- Flyway validates names and checksums before migrating. Missing locations,
  validation errors, or migration failures prevent application startup.
- Flyway `clean` is disabled. Do not turn it on against a real database.
- The health response is public but exposes only `UP` or `DOWN`; other API routes
  retain their existing authentication requirements.
- The deployment profile disables Swagger and TRACE/DEBUG SQL output, including
  Hibernate bind values.

The historical V2 migration includes bootstrap/sample application data and a
BCrypt application-user password hash. It is not a PostgreSQL credential, but a
fresh installation must still review the sample records and replace the
bootstrap application user through an approved operator workflow before real
use. Do not rewrite V2: doing so would break Flyway checksums for existing
databases.

## Required and optional environment variables

| Variable | Required | Example or default | Purpose |
|---|---:|---|---|
| `SPRING_PROFILES_ACTIVE` | Yes | `deployment` | Activates the safe laptop profile. The command-line profile option may be used instead. |
| `BLOOM_DB_URL` | Yes | `jdbc:postgresql://127.0.0.1:5432/bloom_app` | PostgreSQL JDBC address. |
| `BLOOM_DB_USERNAME` | Yes | `bloom_app` | Non-superuser database login. |
| `BLOOM_DB_PASSWORD` | Yes | No default | Database password; never commit it or put it in a command checked into Git. |
| `BLOOM_LOG_FILE` | No | `logs/bloom-app.log` | Log file location, relative to the process working directory unless absolute. |
| `BLOOM_SERVER_ADDRESS` | No | `127.0.0.1` | Network interface to bind. Keep the default for a single laptop. |
| `BLOOM_CORS_ALLOWED_ORIGINS` | No | `http://localhost:5173` | Exact web-client origin. Comma-separate multiple origins only when required. |

Use a PowerShell credential prompt so the database password is not saved in
shell history:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'deployment'
$env:BLOOM_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/bloom_app'
$env:BLOOM_DB_USERNAME = 'bloom_app'
$databaseCredential = Get-Credential -UserName 'bloom_app' -Message 'Bloom PostgreSQL password'
$env:BLOOM_DB_PASSWORD = $databaseCredential.GetNetworkCredential().Password
```

These values live only in that PowerShell process and its child Java process.
Avoid `setx` for the password: it stores a reusable secret in the user's
environment. Clear the secret after shutdown:

```powershell
Remove-Item Env:\BLOOM_DB_PASSWORD -ErrorAction SilentlyContinue
```

## One-time PostgreSQL database creation

Install PostgreSQL using the normal Windows installer. Keep PostgreSQL bound to
localhost unless remote access has been separately reviewed. The following
example assumes PostgreSQL 15; change the version in the path if necessary:

```powershell
$postgresBin = 'C:\Program Files\PostgreSQL\15\bin'
& "$postgresBin\pg_isready.exe" --host 127.0.0.1 --port 5432
& "$postgresBin\psql.exe" --host 127.0.0.1 --port 5432 --username postgres --dbname postgres --password
```

At the `psql` prompt, run the following. `\password` prompts securely, so the new
password is not recorded in SQL history:

```sql
\set ON_ERROR_STOP on
CREATE ROLE bloom_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
\password bloom_app
CREATE DATABASE bloom_app OWNER bloom_app ENCODING 'UTF8' TEMPLATE template0;
REVOKE ALL ON DATABASE bloom_app FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE bloom_app TO bloom_app;
\connect bloom_app
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO bloom_app;
\quit
```

If the role or database already exists, stop and inspect it instead of deleting
or replacing it. Confirm the new login before starting Bloom:

```powershell
& "$postgresBin\psql.exe" --host 127.0.0.1 --port 5432 --username bloom_app --dbname bloom_app --password --command 'select current_database(), current_user;'
```

The application login must not be PostgreSQL's `postgres` superuser.

## Build and release artifact

From any PowerShell location, use `-LiteralPath` and the call operator so spaces
remain safe:

```powershell
$repository = 'E:\Project\Bloom App\bloom-app'
Set-Location -LiteralPath $repository
& (Join-Path $repository 'mvnw.cmd') --batch-mode --no-transfer-progress clean verify
```

`verify` is the release gate. It compiles every Maven module and runs the full
unit and PostgreSQL integration suite. The PostgreSQL integration tests use
Testcontainers by default, so a developer/CI machine needs a working Docker
engine for tests only. Docker is not part of the family-laptop runtime.

After a successful build, the executable artifact is:

```text
bloom-app-boot\target\bloom-app-boot-0.0.1-SNAPSHOT.jar
```

Prefer an artifact produced by the green CI workflow. Copy each release into a
new versioned directory such as `C:\Bloom\releases\0.0.1`; do not overwrite the
previous known-good JAR. A clean family laptop only needs the release JAR, Java,
PostgreSQL, and this runbook. It does not need Maven or Docker.

## Start and verify

Open PowerShell, set the required environment variables, then keep the backend
in the foreground so shutdown is graceful and obvious:

```powershell
$releaseDirectory = 'C:\Bloom\releases\0.0.1'
$jar = Join-Path $releaseDirectory 'bloom-app-boot-0.0.1-SNAPSHOT.jar'
Set-Location -LiteralPath $releaseDirectory
& java -jar $jar
```

If `SPRING_PROFILES_ACTIVE` was not set, use the explicit alternative:

```powershell
& java -jar $jar --spring.profiles.active=deployment
```

Do not use both methods with different values. Startup is successful only when:

1. the log says Flyway validation/migration succeeded;
2. Hibernate schema validation reports no mismatch;
3. the embedded server reports it started on port 8080; and
4. this command returns `status` equal to `UP`:

```powershell
$health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 10
if ($health.status -ne 'UP') { throw "Bloom health check failed: $($health.status)" }
$health
```

The default log is `logs\bloom-app.log` under the release directory. Logs roll
at 10 MB, retain at most 14 files, and use a 200 MB total cap. They do not include
SQL bind values in the deployment profile.

## Graceful shutdown

In the PowerShell window running Java, press **Ctrl+C** once and wait for the
prompt to return. Spring allows up to 30 seconds for a graceful shutdown. Then
confirm the port has been released:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
```

An empty result is expected. Do not power off the laptop, kill `java.exe`, or
stop PostgreSQL while Bloom is processing a sale or migration. Use
`Stop-Process -Force` only as last-resort incident recovery, and inspect the
database and logs before restarting.

## Backup

Create a backup directory outside the release directory and include the date in
each filename. The custom format supports validation and selective inspection:

```powershell
$postgresBin = 'C:\Program Files\PostgreSQL\15\bin'
$backupDirectory = 'C:\Bloom\backups'
New-Item -ItemType Directory -Force -Path $backupDirectory | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupFile = Join-Path $backupDirectory "bloom_app-$stamp.dump"
& "$postgresBin\pg_dump.exe" --host 127.0.0.1 --port 5432 --username bloom_app --password --format custom --file $backupFile bloom_app
if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed' }
& "$postgresBin\pg_restore.exe" --list $backupFile | Select-Object -First 20
if ($LASTEXITCODE -ne 0) { throw 'Backup validation failed' }
```

Back up at least daily while the app is in use and immediately before every
upgrade. Copy backups to a second physical device. Periodically rehearse a
restore; a dump that has never been restored is not a proven backup.

## Restore without overwriting the original

Stop Bloom first. Restore into a new database so the damaged/original database
remains available for investigation:

```powershell
$postgresBin = 'C:\Program Files\PostgreSQL\15\bin'
$backupFile = 'C:\Bloom\backups\bloom_app-YYYYMMDD-HHMMSS.dump'
& "$postgresBin\createdb.exe" --host 127.0.0.1 --port 5432 --username postgres --password --owner bloom_app bloom_app_restore
if ($LASTEXITCODE -ne 0) { throw 'Could not create restore database' }
& "$postgresBin\pg_restore.exe" --host 127.0.0.1 --port 5432 --username bloom_app --password --exit-on-error --no-owner --dbname bloom_app_restore $backupFile
if ($LASTEXITCODE -ne 0) { throw 'pg_restore failed; keep the original database untouched' }
```

Point only a verification start at the restored copy:

```powershell
$env:BLOOM_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/bloom_app_restore'
& java -jar $jar --spring.profiles.active=deployment
```

Check health and representative read-only screens. Then stop the app. For the
safest cutover, retain the original database under its existing name and use the
restored database name in `BLOOM_DB_URL`. Database rename/drop operations are
intentionally not included because they are destructive; a database operator
should approve them after the restored copy is verified.

## Upgrade with versioned Flyway migrations

1. Confirm the CI `verify` job is green and preserve its exact JAR plus Git tag
   or commit ID.
2. Read the release notes and every new `V<number>__description.sql` file. Never
   edit or renumber an already released migration.
3. If upgrading through V21, complete
   [`docs/operations/v21-release-1-contract-cleanup.md`](../operations/v21-release-1-contract-cleanup.md)
   against a recent database copy before touching the live database.
4. Rehearse the upgrade against a restored recent backup and require an `UP`
   health result.
5. Gracefully stop the old JAR.
6. Take and validate a final `pg_dump` backup.
7. Put the new JAR in a new versioned release directory. Keep the old JAR.
8. Start the new JAR with the same deployment environment. Flyway validates the
   complete history, applies pending migrations in order, and then Hibernate
   validates the mapped schema. Any failure prevents startup.
9. Check health, logs, and a small set of read-only business screens before
   allowing new transactions.
10. Record the applied versions:

```powershell
& "$postgresBin\psql.exe" --host 127.0.0.1 --port 5432 --username bloom_app --dbname bloom_app --password --command 'SELECT installed_rank, version, description, script, success FROM flyway_schema_history ORDER BY installed_rank;'
```

An application rollback after a schema change is not just “run the old JAR.”
Restore the pre-upgrade database into a separate database and run the matching
old JAR against that restored database. Forward-only corrective migrations are
preferred when the new migration committed successfully and production data has
already been written.

## Failed startup or migration recovery

Keep the app stopped while diagnosing. Work from the first error in the log, not
the later dependency-failure messages.

### Database cannot be reached

```powershell
& "$postgresBin\pg_isready.exe" --host 127.0.0.1 --port 5432
Get-Service -Name 'postgresql*'
```

Check `BLOOM_DB_URL`, username, password, PostgreSQL service status, and port.
Do not replace the application database user with a superuser to bypass an
authentication or permission error.

### Flyway validation or checksum failure

- Confirm the JAR is the exact reviewed release and all V1–V22 migrations are
  unchanged.
- Query `flyway_schema_history` and compare the failing version and script name.
- Restore the correct released migration artifact. Do not edit history rows,
  delete migrations, or enable `baseline-on-migrate`/`out-of-order` to force a
  start.
- `flyway repair` is not a normal retry command. Use it only after a documented
  root-cause review proves the schema state is correct and a database backup is
  available.

### A new migration fails

PostgreSQL normally rolls back transactional DDL, but do not assume recovery.
Inspect the error and history table. Correct bad configuration or data from
authoritative evidence. If the migration was released or committed anywhere,
make the correction in a new versioned migration. If the database state is
ambiguous or partially changed, preserve it for diagnosis and restore the
pre-upgrade backup into a new database.

### Hibernate schema validation fails

This means the Flyway schema and Java mappings disagree. Do not change
`ddl-auto` to `update`, `create`, or `none`. Verify the JAR/database pairing and
pending migration history; deploy a reviewed forward migration or restore the
matching pre-upgrade database and JAR.

### Port 8080 is already in use

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object LocalAddress,LocalPort,OwningProcess
Get-Process -Id (Get-NetTCPConnection -LocalPort 8080 -State Listen).OwningProcess
```

Stop the unintended process gracefully. Do not start two Bloom instances against
the same laptop database.

### Health reports `DOWN`

Keep the app unavailable for transaction entry. Read
`logs\bloom-app.log`, verify PostgreSQL with `pg_isready`, and resolve the failing
dependency. The deployment health endpoint deliberately hides internal details;
the local log is the diagnostic source.

## Clean-machine installation rehearsal checklist

Complete this on a disposable or newly prepared Windows account before the first
live installation and for material platform upgrades. Record the operator, date,
release commit, JAR checksum, Windows version, Java version, and PostgreSQL
version.

- [ ] No existing Maven cache, Bloom environment variables, Bloom database, or
  release directory is being reused unintentionally.
- [ ] Java 21 is installed and `java -version` succeeds from a path containing
  spaces.
- [ ] PostgreSQL is installed, starts after reboot, and listens only where
  intended.
- [ ] The `bloom_app` role is not a superuser and owns only the Bloom database.
- [ ] A strong, unique database password is supplied without placing it in Git,
  scripts, screenshots, tickets, or shell history.
- [ ] The release JAR checksum matches the CI artifact.
- [ ] On a build machine, `mvnw.cmd clean verify` passes from a repository path
  and Maven cache path that both contain spaces.
- [ ] A new empty database migrates through the latest version and the
  application reaches health `UP` with `ddl-auto=validate`.
- [ ] `flyway_schema_history` contains one successful row per expected migration
  and no failed row.
- [ ] Swagger is unavailable in the deployment profile, while the health route
  is available without a login and reveals no internal details.
- [ ] The backend binds to `127.0.0.1` unless broader access was separately
  approved.
- [ ] The expected React web origin can log in and ordinary authenticated API
  routes still reject anonymous access.
- [ ] Bootstrap/sample records and application-user access have been reviewed
  before real business data is entered.
- [ ] Ctrl+C performs a graceful shutdown and releases port 8080.
- [ ] A custom-format backup completes, `pg_restore --list` succeeds, and a
  side-by-side restore reaches health `UP`.
- [ ] An upgrade rehearsal on a restored recent backup succeeds, including any
  migration-specific preflight such as V21.
- [ ] A deliberately wrong database password, checksum mismatch in a disposable
  copy, and schema mismatch each prevent startup without schema auto-repair.
- [ ] Logs rotate at the expected limits and do not contain database passwords or
  SQL bind values.
- [ ] After reboot, the documented manual start, health check, and graceful stop
  can be completed by the intended family operator.
