# Upgrade rehearsal on SQL Server

This rehearsal upgrades a SQL Server database written by an older build of OBP-API to a newer
build, and checks that the newer build starts.

The test suites start from an empty database. On an empty database Schemifier simply creates every
table and index. On an existing database it adds the new columns and indexes to tables that
already hold rows, and that can fail. For example, SQL Server refuses a unique index over rows that
repeat its key, and it allows only one NULL in a unique index. These failures only show up when an
existing deployment is upgraded. The rehearsal finds them before then.

## What it does

`run.sh` takes two refs: `FROM_REF` (the older build) and `TO_REF` (the newer build).

1. It builds both. The older build is a WAR file, built with JDK 11 and run with Jetty Runner. The
   newer build is a jar, built with JDK 25. Each build is cached by its commit.
2. It starts SQL Server (by default 2017 CU20) and Redis in Docker.
3. It boots the older build on an empty database, `obp_upgrade`, which gets the older schema.
4. It boots the newer build on a second empty database, `obp_reference`, which gets the schema the
   newer build creates from scratch.
5. `seed-dirty-data.sql` compares the two schemas. For each table that exists in the older schema:
   - If the table gets a new unique index, it adds two rows with the same key. A key column that
     the newer build adds is NULL in both rows, as in every row an older build wrote, and SQL
     Server counts two NULLs as a duplicate.
   - If the table gets a new NOT NULL column, it adds two rows.

   The other columns get the values Lift Mapper writes when it is not given one: `''` for a
   string, `0` for a number or a boolean, and NULL for a date. So the rows look like rows the
   older build wrote, and a failure points at the newer build rather than at the seed.

   The list comes from the schemas, not from a list written by hand, so a unique index added by a
   later change is covered without editing the rehearsal.
6. It boots the newer build on `obp_upgrade` with `migration_scripts.enabled=true` and
   `migration_scripts.execute_all=true`.
7. The rehearsal passes when all of these hold:
   - the newer build answers `200` on `/obp/v4.0.0/root`;
   - its log has no schema error;
   - `check-schema.sql` finds every unique index and NOT NULL column of `obp_reference` in
     `obp_upgrade`.

The rows the rehearsal adds stand in for what an older deployment may hold. They are the smallest
rows that break each new index, not a copy of real data.

## Running it on a Mac

You need Docker, Maven, JDK 11 and JDK 25. The script finds each JDK with `/usr/libexec/java_home`;
you can also set `JAVA_HOME_FROM` and `JAVA_HOME_TO`. The SQL Server images are built for amd64
only, so Docker emulates them on Apple silicon, which makes the boots slower.

```bash
development/upgrade-rehearsal/run.sh                       # from the default older build to HEAD
development/upgrade-rehearsal/run.sh <from-ref> <to-ref>
```

The exit code is 0 when the rehearsal passes. `result.txt` in the log directory says `PASS` or
gives the reason it failed.

Settings, all optional:

| Variable | Default | Meaning |
|---|---|---|
| `WORK_DIR` | `$TMPDIR/obp-upgrade-rehearsal` | Sources, builds and props |
| `CACHE_DIR` | `$WORK_DIR/cache` | Builds, kept by commit |
| `LOG_DIR` | `$WORK_DIR/logs` | Logs; the password is removed from them at the end |
| `MSSQL_IMAGE` | `mcr.microsoft.com/mssql/server:2017-CU20-ubuntu` | SQL Server version |
| `MSSQL_PORT`, `REDIS_PORT`, `API_PORT` | `14334`, `16380`, `18086` | Local ports |
| `BOOT_TIMEOUT` | `900` | Seconds each boot may take to answer |
| `KEEP_CONTAINERS` | `false` | `true` keeps SQL Server running afterwards, to look at the data |
| `MAVEN_ARGS` | | Passed to Maven, e.g. `-Dmaven.repo.local=...` |

## Reading the logs

| File | Content |
|---|---|
| `rehearsal.log` | What was done, with timings, the seeded tables and the result |
| `seed.txt` | `CANDIDATE`, `SEEDED` and `SKIPPED` lines from the seeding |
| `boot-from.log`, `boot-to-reference.log`, `boot-to-upgrade.log` | Output of the three boots |
| `schema-errors.txt` | Schema errors found in `boot-to-upgrade.log` |
| `check-schema.txt` | `MISSING` lines, or a single `OK` line |
| `build-from.log`, `build-to.log` | Maven output, when a build ran |

A `SKIPPED` line means that the older schema itself refused the duplicate rows, for example
because it already had a unique index that is narrower than the new one. Such a table cannot hold
duplicates for the new index either.

## In GitHub Actions

`.github/workflows/upgrade_rehearsal_sqlserver.yml` runs the same script. Start it from the Actions
tab with `from_ref`, `to_ref` (empty means the commit it runs on) and `mssql_image`. With
`expect: fail` it checks the opposite: it passes when the rehearsal catches a build that is known
to break the upgrade. A pull request that changes the migrations, `Boot.scala` or the rehearsal
also starts it. A unique index added in a model file does not, so start it by hand for such a
change. The logs are uploaded as the `rehearsal-logs-<commit>` artifact.
