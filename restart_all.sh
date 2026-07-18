#!/bin/bash
# Restart the whole OBP dev stack after a container reset.
# Build artifacts / node_modules / venvs / Postgres data all persist on disk;
# only the running processes die. This just re-launches everything.
#
# Actual paths/credentials for THIS container (filled in from the real deploy,
# not the generic template):
#   OBP-API      /home/user/OBP-API      (jdbc:postgresql://localhost:5432/sandbox, user obp / daniel.says)
#   OBP-OIDC     /workspace/obp-oidc     (reads DB creds from its own .env: oidc_user/oidc_admin)
#   API-Explorer-II  /workspace/api-explorer-ii
#   OBP-Frontend     /workspace/obp-frontend   (Portal :5174 + API-Manager :3003)
#   OGCR-App         /workspace/ogcr-app
#   OBP-MCP          /workspace/obp-mcp

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

# Postgres role OBP-API connects as (created in Step 1 of the setup doc)
export PGHOST=localhost
export PGPORT=5432
export PGUSER=obp
export PGPASSWORD=daniel.says
export PGDATABASE=sandbox

echo "== services =="
service postgresql start
service redis-server start
sleep 2

echo "== verifying Postgres connectivity (obp/sandbox) =="
if psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -c '\q' 2>/tmp/pg-check.log; then
  echo "Postgres OK: connected as $PGUSER to $PGDATABASE"
else
  echo "Postgres connection FAILED — check /tmp/pg-check.log (role/db may need recreating, see setup doc Step 1)"
  cat /tmp/pg-check.log
fi

echo "== OBP-API :8080 =="
cd /home/user/OBP-API
nohup java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang.invoke=ALL-UNNAMED \
  --add-opens java.base/java.util.jar=ALL-UNNAMED --add-opens java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED \
  --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.util.concurrent=ALL-UNNAMED \
  --add-opens java.base/java.security=ALL-UNNAMED \
  -cp "obp-api/src/main/resources:obp-api/target/obp-api.jar" bootstrap.http4s.Http4sServer \
  > /tmp/obp-api.log 2>&1 &
disown

echo "== OBP-OIDC :9000 =="
cd /workspace/obp-oidc
nohup ./run-server.sh > /tmp/obp-oidc.log 2>&1 &
disown

echo "waiting for OBP-API + OBP-OIDC..."
for i in $(seq 1 40); do
  A=$(curl -sS -m 2 -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/obp/v7.0.0/root 2>/dev/null || echo 000)
  O=$(curl -sS -m 2 -o /dev/null -w "%{http_code}" http://localhost:9000/health 2>/dev/null || echo 000)
  [ "$A" = "200" ] && [ "$O" = "200" ] && { echo "OBP-API + OBP-OIDC up"; break; }
  sleep 3
done

echo "== API-Explorer-II :5173/:8085 =="
cd /workspace/api-explorer-ii
nohup npm run dev > /tmp/api-explorer.log 2>&1 &
disown

echo "== OBP-Frontend Portal :5174 + API Manager :3003 =="
cd /workspace/obp-frontend
nohup ./start_dev_all.sh > /tmp/obp-frontend-launcher.log 2>&1 &
disown

echo "== OGCR-App :5200 =="
cd /workspace/ogcr-app
nohup npm run dev > /tmp/ogcr-app.log 2>&1 &
disown

echo "== OBP-MCP :9100 =="
cd /workspace/obp-mcp
nohup ./run_server.sh > /tmp/obp-mcp.log 2>&1 &
disown

echo "waiting for frontends..."
for i in $(seq 1 30); do
  E=$(curl -sS -m 2 -o /dev/null -w "%{http_code}" http://localhost:5173/ 2>/dev/null || echo 000)
  P=$(curl -sS -m 2 -o /dev/null -w "%{http_code}" http://localhost:5174/ 2>/dev/null || echo 000)
  M=$(curl -sS -m 2 -o /dev/null -w "%{http_code}" http://localhost:3003/ 2>/dev/null || echo 000)
  G=$(curl -sS -m 2 -o /dev/null -w "%{http_code}" http://localhost:5200/ 2>/dev/null || echo 000)
  C=$(curl -sS -m 2 -o /dev/null -w "%{http_code}" http://127.0.0.1:9100/mcp 2>/dev/null || echo 000)
  echo "  explorer=$E portal=$P api-manager=$M ogcr=$G mcp=$C"
  [ "$E" != "000" ] && [ "$P" != "000" ] && [ "$M" != "000" ] && [ "$G" != "000" ] && [ "$C" != "000" ] && { echo "ALL UP"; break; }
  sleep 4
done

echo "== done =="
echo "test login: oidctestuser / Sup3rSecret!2026"
