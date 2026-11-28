#!/usr/bin/env bash
#
# Upgrade rehearsal on SQL Server.
#
# It boots an older build (FROM_REF) on an empty SQL Server database, so the database gets that
# build's schema. It then adds rows that the newer build (TO_REF) cannot index as they are:
# duplicates under each unique index the newer build adds to an existing table, and rows in each
# existing table that gets a new NOT NULL column. Finally it boots the newer build on that database
# and checks that it starts and that it created every unique index it declares.
#
# The list of indexes and columns is not written by hand. The newer build is first booted on a
# second, empty database. Comparing that schema with the older one gives the list.
#
# The rehearsal passes when the newer build answers on its root endpoint, its log has no schema
# error, and the upgraded database has every unique index the empty one has.
#
# Usage: development/upgrade-rehearsal/run.sh [FROM_REF] [TO_REF]
# See development/upgrade-rehearsal/README.md for the settings and how to read the result.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="${REPO:-$(git -C "$HERE" rev-parse --show-toplevel)}"

FROM_REF="${1:-${FROM_REF:-b6c783b3d99a6d48697eb8ef986f8948f646e63e}}"
TO_REF="${2:-${TO_REF:-HEAD}}"

WORK_DIR="${WORK_DIR:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}/obp-upgrade-rehearsal}"
WORK_DIR="${WORK_DIR%/}"
CACHE_DIR="${CACHE_DIR:-$WORK_DIR/cache}"
LOG_DIR="${LOG_DIR:-$WORK_DIR/logs}"

MSSQL_IMAGE="${MSSQL_IMAGE:-mcr.microsoft.com/mssql/server:2017-CU20-ubuntu}"
REDIS_IMAGE="${REDIS_IMAGE:-redis:7-alpine}"
MSSQL_PORT="${MSSQL_PORT:-14334}"
REDIS_PORT="${REDIS_PORT:-16380}"
API_PORT="${API_PORT:-18086}"
BOOT_TIMEOUT="${BOOT_TIMEOUT:-900}"
KEEP_CONTAINERS="${KEEP_CONTAINERS:-false}"
JETTY_RUNNER_VERSION="${JETTY_RUNNER_VERSION:-9.4.58.v20250814}"

CONTAINER_PREFIX="${CONTAINER_PREFIX:-obp-upgrade-rehearsal}"
MSSQL_CONTAINER="$CONTAINER_PREFIX-mssql"
REDIS_CONTAINER="$CONTAINER_PREFIX-redis"
UPGRADE_DB="obp_upgrade"
REFERENCE_DB="obp_reference"

# A throwaway password for a container that is removed at the end. It is kept out of the logs.
SA_PASSWORD="${SA_PASSWORD:-Rh1!$(openssl rand -hex 12)}"

START_SECONDS=$SECONDS
APP_PID=""

log() { printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*" | tee -a "$LOG_DIR/rehearsal.log"; }
fail() { log "FAIL: $*"; echo "FAIL: $*" > "$LOG_DIR/result.txt"; exit 1; }

java_home_for() {
  # $1 is the major version. GitHub's setup-java exports JAVA_HOME_<v>_X64; macOS has java_home.
  local version="$1" from_env
  from_env="$(printenv "JAVA_HOME_${version}_X64" || true)"
  if [ -n "$from_env" ]; then echo "$from_env"; return; fi
  from_env="$(printenv "JAVA_HOME_${version}_ARM64" || true)"
  if [ -n "$from_env" ]; then echo "$from_env"; return; fi
  if [ -x /usr/libexec/java_home ]; then /usr/libexec/java_home -v "$version"; return; fi
  echo "No JDK $version found. Set JAVA_HOME_FROM or JAVA_HOME_TO." >&2
  return 1
}

sqlcmd() {
  # Runs sqlcmd inside the SQL Server container. The 2017 and 2019 images have mssql-tools,
  # the 2022 image has mssql-tools18, which also needs -C to accept the self-signed certificate.
  if docker exec "$MSSQL_CONTAINER" test -x /opt/mssql-tools18/bin/sqlcmd; then
    docker exec "$MSSQL_CONTAINER" /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$SA_PASSWORD" -b "$@"
  else
    docker exec "$MSSQL_CONTAINER" /opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P "$SA_PASSWORD" -b "$@"
  fi
}

run_sql_file() {
  # $1 database, $2 SQL file; the rest is passed to sqlcmd (e.g. -v NAME=value).
  local db="$1" file="$2"; shift 2
  # unique-keys.sql is included by other files with :r, so it goes into the container as well.
  docker cp "$HERE/unique-keys.sql" "$MSSQL_CONTAINER:/tmp/unique-keys.sql" >/dev/null
  docker cp "$file" "$MSSQL_CONTAINER:/tmp/$(basename "$file")" >/dev/null
  sqlcmd -d "$db" -I -W -s '|' "$@" -i "/tmp/$(basename "$file")"
}

stop_app() {
  if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
    kill "$APP_PID" 2>/dev/null || true
    for _ in $(seq 1 60); do kill -0 "$APP_PID" 2>/dev/null || break; sleep 1; done
    kill -9 "$APP_PID" 2>/dev/null || true
  fi
  APP_PID=""
}

redact_logs() {
  # The database URL holds the throwaway password; keep it out of anything that gets uploaded.
  local f
  for f in "$LOG_DIR"/*; do
    [ -f "$f" ] || continue
    sed -i.bak "s/$(printf '%s' "$SA_PASSWORD" | sed 's/[][\/.*^$!]/\\&/g')/***/g" "$f" && rm -f "$f.bak"
  done
}

cleanup() {
  local status=$?
  stop_app
  if [ "$KEEP_CONTAINERS" != "true" ]; then
    docker rm -f "$MSSQL_CONTAINER" "$REDIS_CONTAINER" >/dev/null 2>&1 || true
  fi
  rm -rf "$WORK_DIR/src-from" "$WORK_DIR/src-to"
  [ -d "$LOG_DIR" ] && redact_logs
  log "Finished in $((SECONDS - START_SECONDS))s with exit code $status. Logs: $LOG_DIR"
  exit $status
}

mkdir -p "$WORK_DIR" "$CACHE_DIR" "$LOG_DIR"
rm -f "$LOG_DIR"/*.log "$LOG_DIR"/*.txt
trap cleanup EXIT

FROM_SHA="$(git -C "$REPO" rev-parse --verify "$FROM_REF^{commit}")"
TO_SHA="$(git -C "$REPO" rev-parse --verify "$TO_REF^{commit}")"
JAVA_HOME_FROM="${JAVA_HOME_FROM:-$(java_home_for 11)}"
JAVA_HOME_TO="${JAVA_HOME_TO:-$(java_home_for 25)}"
log "FROM $FROM_REF = $FROM_SHA (JDK $JAVA_HOME_FROM)"
log "TO   $TO_REF = $TO_SHA (JDK $JAVA_HOME_TO)"
log "SQL Server image $MSSQL_IMAGE"

# ---------------------------------------------------------------------------------------------
# Build both refs. Each build is cached by commit, so a second run with the same refs skips it.
# ---------------------------------------------------------------------------------------------

checkout() {
  # $1 commit, $2 directory. A clone that shares this repository's objects, not a linked worktree:
  # the git plugins of the builds use JGit, which cannot read a linked worktree's .git file.
  rm -rf "$2"
  git clone -q --shared --no-checkout "$REPO" "$2"
  git -C "$2" checkout -q --detach "$1"
}

build() {
  # $1 source directory, $2 JDK home, $3 log file. A failed dependency download leaves a marker
  # in the local Maven repository, and Maven does not try that dependency again until -U says so,
  # so a failed build is tried once more with -U.
  local attempt
  for attempt in 1 2; do
    ( cd "$1" && JAVA_HOME="$2" PATH="$2/bin:$PATH" MAVEN_OPTS="-Xmx3G -Xss2m -XX:MaxMetaspaceSize=1G" \
        mvn -B $([ "$attempt" = 2 ] && echo -U) clean package -Pprod -DskipTests -Dmaven.test.skip=true ) \
      >> "$3" 2>&1 && return 0
    [ "$attempt" = 1 ] && log "Build in $1 failed; trying once more with -U"
  done
  return 1
}

FROM_WAR="$CACHE_DIR/from-$FROM_SHA/obp-api.war"
if [ ! -f "$FROM_WAR" ]; then
  log "Building FROM (older build, a WAR file)"
  checkout "$FROM_SHA" "$WORK_DIR/src-from"
  t=$SECONDS
  build "$WORK_DIR/src-from" "$JAVA_HOME_FROM" "$LOG_DIR/build-from.log" \
    || fail "the FROM build failed, see build-from.log"
  mkdir -p "$(dirname "$FROM_WAR")"
  cp "$(ls "$WORK_DIR"/src-from/obp-api/target/obp-api-*.war | head -1)" "$FROM_WAR"
  log "FROM built in $((SECONDS - t))s"
else
  log "FROM build found in the cache"
fi

TO_JAR_DIR="$CACHE_DIR/to-$TO_SHA"
if [ ! -f "$TO_JAR_DIR/obp-api.jar" ]; then
  log "Building TO (newer build, a jar with its lib directory)"
  checkout "$TO_SHA" "$WORK_DIR/src-to"
  t=$SECONDS
  build "$WORK_DIR/src-to" "$JAVA_HOME_TO" "$LOG_DIR/build-to.log" \
    || fail "the TO build failed, see build-to.log"
  rm -rf "$TO_JAR_DIR" && mkdir -p "$TO_JAR_DIR"
  cp "$WORK_DIR/src-to/obp-api/target/obp-api.jar" "$TO_JAR_DIR/"
  cp -R "$WORK_DIR/src-to/obp-api/target/lib" "$TO_JAR_DIR/"
  log "TO built in $((SECONDS - t))s"
else
  log "TO build found in the cache"
fi

JETTY_RUNNER="$CACHE_DIR/jetty-runner-$JETTY_RUNNER_VERSION.jar"
if [ ! -f "$JETTY_RUNNER" ]; then
  curl -fsSL --proto '=https' --tlsv1.2 -o "$JETTY_RUNNER" \
    "https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-runner/$JETTY_RUNNER_VERSION/jetty-runner-$JETTY_RUNNER_VERSION.jar"
fi

# ---------------------------------------------------------------------------------------------
# Databases
# ---------------------------------------------------------------------------------------------

log "Starting SQL Server and Redis"
docker rm -f "$MSSQL_CONTAINER" "$REDIS_CONTAINER" >/dev/null 2>&1 || true
# The SQL Server images are built for amd64 only; on an arm64 Mac Docker emulates them.
docker run -d --name "$MSSQL_CONTAINER" --platform linux/amd64 -e ACCEPT_EULA=Y \
  -e "SA_PASSWORD=$SA_PASSWORD" -e "MSSQL_SA_PASSWORD=$SA_PASSWORD" \
  -p "$MSSQL_PORT:1433" "$MSSQL_IMAGE" >/dev/null
docker run -d --name "$REDIS_CONTAINER" -p "$REDIS_PORT:6379" "$REDIS_IMAGE" >/dev/null
for i in $(seq 1 90); do
  sqlcmd -Q "SELECT 1" >/dev/null 2>&1 && break
  [ "$i" = 90 ] && fail "SQL Server did not start"
  sleep 2
done
sqlcmd -Q "CREATE DATABASE $UPGRADE_DB; CREATE DATABASE $REFERENCE_DB;" >/dev/null
sqlcmd -h -1 -Q "SET NOCOUNT ON; SELECT @@VERSION" | head -1 | tee -a "$LOG_DIR/rehearsal.log"

# ---------------------------------------------------------------------------------------------
# Booting a build
# ---------------------------------------------------------------------------------------------

write_props() {
  # $1 props directory, $2 database, $3 whether migration scripts run
  mkdir -p "$1"
  cat > "$1/production.default.props" <<EOF
connector=mapped
hostname=http://localhost:$API_PORT
dev.port=$API_PORT
db.driver=com.microsoft.sqlserver.jdbc.SQLServerDriver
db.url=jdbc:sqlserver://localhost:$MSSQL_PORT;databaseName=$2;user=sa;password=$SA_PASSWORD;encrypt=false
cache.redis.url=localhost
cache.redis.port=$REDIS_PORT
migration_scripts.enabled=$3
migration_scripts.execute_all=$3
EOF
  chmod 600 "$1/production.default.props"
  # The same settings as OBP_* environment variables, which every build reads before its props
  # file: the newer build reads hostname before Boot has found the props directory.
  grep -v '^#' "$1/production.default.props" | grep '=' | while IFS='=' read -r key value; do
    printf 'OBP_%s=%s\n' "$(printf '%s' "$key" | tr '.' '_' | tr '[:lower:]' '[:upper:]')" "$value"
  done > "$1/env"
  chmod 600 "$1/env"
}

props_env() {
  # $1 props directory. Prints the environment variables written by write_props, one per line.
  cat "$1/env"
}

wait_for_root() {
  # $1 label, $2 log file. Succeeds when the root endpoint answers 200.
  local started=$SECONDS code
  while [ $((SECONDS - started)) -lt "$BOOT_TIMEOUT" ]; do
    if ! kill -0 "$APP_PID" 2>/dev/null; then
      log "$1 exited before it answered on its root endpoint"
      return 1
    fi
    # A failed Boot can leave the JVM running without a server; do not wait for the timeout.
    if grep -qE 'Exception in thread "main"' "$2"; then
      log "$1 failed during Boot: $(grep -m1 -E 'SQLServerException|Caused by' "$2" | cut -c1-300)"
      return 1
    fi
    code="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$API_PORT/obp/v4.0.0/root" || true)"
    if [ "$code" = 200 ]; then
      log "$1 answered 200 on /obp/v4.0.0/root after $((SECONDS - started))s"
      return 0
    fi
    sleep 5
  done
  log "$1 did not answer 200 within ${BOOT_TIMEOUT}s"
  return 1
}

boot_from() {
  # $1 database
  local props="$WORK_DIR/props-from"
  write_props "$props" "$1" false
  local -a vars=(); local line   # a read loop, not mapfile: macOS ships bash 3.2
  while IFS= read -r line; do vars+=("$line"); done < <(props_env "$props")
  ( cd "$WORK_DIR" && exec env "${vars[@]}" "$JAVA_HOME_FROM/bin/java" -Xmx2G -Xss8m \
      -Drun.mode=production -Dprops.resource.dir="$props/" \
      -jar "$JETTY_RUNNER" --port "$API_PORT" "$FROM_WAR" ) > "$LOG_DIR/boot-from.log" 2>&1 &
  APP_PID=$!
}

boot_to() {
  # $1 database, $2 log file name
  local props="$WORK_DIR/props-to-$1"
  write_props "$props" "$1" true
  local -a vars=(); local line   # a read loop, not mapfile: macOS ships bash 3.2
  while IFS= read -r line; do vars+=("$line"); done < <(props_env "$props")
  ( cd "$TO_JAR_DIR" && exec env "${vars[@]}" "$JAVA_HOME_TO/bin/java" -Xmx2G -Xss8m \
      --add-opens java.base/java.lang=ALL-UNNAMED \
      --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
      --add-opens java.base/java.io=ALL-UNNAMED \
      --add-opens java.base/java.util=ALL-UNNAMED \
      --add-opens java.base/java.util.concurrent=ALL-UNNAMED \
      -Drun.mode=production -Dprops.resource.dir="$props/" \
      -jar obp-api.jar ) > "$LOG_DIR/$2" 2>&1 &
  APP_PID=$!
}

# 1. The older build creates its schema.
log "Booting FROM on the empty $UPGRADE_DB database"
boot_from "$UPGRADE_DB"
wait_for_root FROM "$LOG_DIR/boot-from.log" || fail "the FROM build did not start, see boot-from.log"
stop_app

# 2. The newer build on an empty database: its schema is the reference.
log "Booting TO on the empty $REFERENCE_DB database, to get the schema it creates from scratch"
boot_to "$REFERENCE_DB" boot-to-reference.log
wait_for_root "TO (reference)" "$LOG_DIR/boot-to-reference.log" \
  || fail "the TO build did not start on an empty database, see boot-to-reference.log"
stop_app

# 3. Rows the newer build cannot index as they are.
log "Adding rows the TO schema cannot take as they are"
run_sql_file "$UPGRADE_DB" "$HERE/seed-dirty-data.sql" -v REFERENCE_DB="$REFERENCE_DB" \
  > "$LOG_DIR/seed.txt" 2>&1 || { cat "$LOG_DIR/seed.txt"; fail "seeding failed, see seed.txt"; }
grep -E '^(CANDIDATE|SEEDED|SKIPPED)' "$LOG_DIR/seed.txt" | tee -a "$LOG_DIR/rehearsal.log" || true
grep -q '^SEEDED' "$LOG_DIR/seed.txt" || fail "nothing was seeded, so the rehearsal would test nothing"

# Rows for migrations that rewrite stored values, which the schema comparison cannot find.
log "Adding the rows of the known cases"
run_sql_file "$UPGRADE_DB" "$HERE/seed-known-cases.sql" \
  > "$LOG_DIR/seed-known-cases.txt" 2>&1 || { cat "$LOG_DIR/seed-known-cases.txt"; fail "seeding the known cases failed, see seed-known-cases.txt"; }
grep -E '^(SEEDED|SKIPPED)' "$LOG_DIR/seed-known-cases.txt" | tee -a "$LOG_DIR/rehearsal.log" || true

# 4. The upgrade.
log "Booting TO on $UPGRADE_DB, with migration scripts enabled"
boot_to "$UPGRADE_DB" boot-to-upgrade.log
booted=true
wait_for_root "TO (upgrade)" "$LOG_DIR/boot-to-upgrade.log" || booted=false
stop_app

# Schemifier logs a failed index or column and, depending on the build, may go on booting, so
# the log is read even when the root endpoint answered.
SCHEMA_ERRORS='CREATE UNIQUE INDEX statement terminated|duplicate key was found|Cannot insert the value NULL|SQLServerException'
grep -nE "$SCHEMA_ERRORS" "$LOG_DIR/boot-to-upgrade.log" > "$LOG_DIR/schema-errors.txt" || true

log "Comparing the unique indexes of $UPGRADE_DB with $REFERENCE_DB"
schema_ok=true
run_sql_file "$UPGRADE_DB" "$HERE/check-schema.sql" -v REFERENCE_DB="$REFERENCE_DB" \
  > "$LOG_DIR/check-schema.txt" 2>&1 || schema_ok=false
grep -E '^(MISSING|OK)' "$LOG_DIR/check-schema.txt" | tee -a "$LOG_DIR/rehearsal.log" || true

problems=()
[ "$booted" = true ] || problems+=("TO did not start on the upgraded database (boot-to-upgrade.log)")
[ -s "$LOG_DIR/schema-errors.txt" ] && problems+=("TO logged schema errors (schema-errors.txt)")
[ "$schema_ok" = true ] || problems+=("the upgraded database lacks unique indexes or NOT NULL columns the TO build declares (check-schema.txt)")

if [ ${#problems[@]} -gt 0 ]; then
  head -20 "$LOG_DIR/schema-errors.txt" | tee -a "$LOG_DIR/rehearsal.log" || true
  fail "$(IFS='; '; echo "${problems[*]}")"
fi

log "PASS: TO $TO_SHA upgraded the FROM $FROM_SHA schema with the seeded rows"
echo "PASS" > "$LOG_DIR/result.txt"
