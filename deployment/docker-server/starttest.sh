#!/usr/bin/env bash
# =============================================================================
# Starttest fuer ein neues ERP-Image (laeuft in der GitHub Action, geht auch lokal)
# =============================================================================
# Frage: Faehrt die neue Version auf einer Datenbank hoch, wie sie die Kunden
# gerade haben - inklusive aller NEUEN Flyway-Migrationen?
#
#   1. Leere Datenbank; die Version, die gerade bei den Kunden laeuft (:stable),
#      richtet sie ein - genau wie bei einer Neuinstallation (Basis-Schema +
#      ihre Migrationen, siehe FlywayStartSetupConfig).
#   2. Die neue Version startet auf dieser Datenbank und fuehrt nur die neu
#      hinzugekommenen Migrationen aus. /actuator/health muss 200 liefern.
# Die Flyway-Historie ist dabei echt - auch neue Migrationen mit kleinerer
# Nummer (out-of-order) laufen also mit.
#
# Grenze: Die Datenbank enthaelt nur Stammdaten, keine Kundendaten. Was nur an
# echten Daten scheitert (z.B. Dubletten bei einem neuen UNIQUE), faengt erst
# das Nachtupdate ab - mit Rollback.
#
# Aufruf:  [STARTTEST_DB=postgres|mysql] ./starttest.sh <neues-image> [<image-der-kunden>]
#          Ohne zweites Image: nur Neuinstallation mit dem neuen Image.
#          STARTTEST_DB: PostgreSQL (Kunden, Standard) oder MySQL (eigener
#          Server) - die GitHub Action prueft beide.
# =============================================================================

set -Eeuo pipefail

SKRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NEU="${1:?Aufruf: starttest.sh <neues-image> [<image-der-kunden>]}"
BASIS="${2:-}"
WARTEZEIT="${STARTTEST_WARTEZEIT:-300}"
NETZ="erp-starttest"
DB="kalkulationsprogramm_db"
DB_ART="${STARTTEST_DB:-postgres}"
case "$DB_ART" in
    postgres) DB_URL="jdbc:postgresql://erp-starttest-db:5432/$DB" ;;
    mysql) DB_URL="jdbc:mysql://erp-starttest-db:3306/$DB?useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC" ;;
    *) echo "STARTTEST_DB muss postgres oder mysql sein" >&2; exit 2 ;;
esac
# Wegwerf-Zugangsdaten nur fuer diesen Testcontainer
TEST_PW="starttest-$RANDOM$RANDOM"

log() {
    printf '[starttest] %s\n' "$*"
}

aufraeumen() {
    docker rm -f erp-starttest-app erp-starttest-db >/dev/null 2>&1 || true
    docker network rm "$NETZ" >/dev/null 2>&1 || true
}
trap aufraeumen EXIT

# starten <image>: App auf der Test-Datenbank starten, wartet auf Health 200
starten() {
    docker rm -f erp-starttest-app >/dev/null 2>&1 || true
    docker run -d --name erp-starttest-app --network "$NETZ" -p 127.0.0.1:18080:8080 \
        -e SPRING_PROFILES_ACTIVE="docker,$DB_ART" \
        -e APP_DB_URL="$DB_URL" -e APP_DB_USER=erp_user -e APP_DB_PASS="$TEST_PW" \
        "$1" >/dev/null
    local ende=$((SECONDS + WARTEZEIT))
    while (( SECONDS < ende )); do
        if [[ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:18080/actuator/health || true)" == "200" ]]; then
            return 0
        fi
        if [[ "$(docker inspect -f '{{.State.Running}}' erp-starttest-app 2>/dev/null)" != "true" ]]; then
            log "$1 ist abgestuerzt."
            break
        fi
        sleep 1
    done
    docker logs --tail 400 erp-starttest-app 2>&1 \
        | grep -E "ERROR|Caused by|Message    :|Statement  :|Location   :|APPLICATION FAILED" | head -n 40 || true
    return 1
}

# sql <abfrage>: eine Abfrage in der Test-Datenbank, Ergebnis ohne Kopfzeile.
# Ueber TCP: Beide Images starten beim ersten Einrichten kurz einen Server nur
# am Unix-Socket - "bereit" heisst erst, wenn das Netz antwortet.
sql() {
    if [[ "$DB_ART" == "postgres" ]]; then
        docker exec -e PGPASSWORD="$TEST_PW" erp-starttest-db psql -h 127.0.0.1 -U erp_user -d "$DB" -At -c "$1"
    else
        docker exec -e MYSQL_PWD="$TEST_PW" erp-starttest-db mysql -h 127.0.0.1 -uroot -N -B "$DB" -e "$1"
    fi
}

stand() {
    sql "SELECT COALESCE(MAX(installed_rank), 0) FROM flyway_schema_history" 2>/dev/null || echo 0
}

aufraeumen
docker network create "$NETZ" >/dev/null

if [[ "$DB_ART" == "postgres" ]]; then
    log "Starte PostgreSQL 16 ..."
    # Wie beim Kunden (docker-compose.yml): Verwalter "postgres", die App als
    # Rolle OHNE Superuser-Rechte - eine Migration, die mehr braucht, muss hier
    # scheitern und nicht erst nachts beim Kunden.
    docker run -d --name erp-starttest-db --network "$NETZ" \
        -e POSTGRES_DB="$DB" -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD="$TEST_PW" \
        -e ERP_DB_USER=erp_user -e ERP_DB_PASSWORD="$TEST_PW" \
        -v "$SKRIPT_DIR/postgres-init:/docker-entrypoint-initdb.d:ro" \
        postgres:16 >/dev/null
else
    log "Starte MySQL 8.0 ..."
    docker run -d --name erp-starttest-db --network "$NETZ" \
        -e MYSQL_ROOT_PASSWORD="$TEST_PW" -e MYSQL_DATABASE="$DB" \
        -e MYSQL_USER=erp_user -e MYSQL_PASSWORD="$TEST_PW" \
        mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null
fi
for _ in $(seq 1 90); do
    if sql "SELECT 1" >/dev/null 2>&1; then
        break
    fi
    sleep 2
done

# Kundenstaende ohne Startsetup (vor FlywayStartSetupConfig) koennen eine leere
# Datenbank nicht einrichten - dann bleibt nur der Test der Neuinstallation.
# Dasselbe fuer Staende, die PostgreSQL noch nicht kannten.
BASIS_ORDNER="BOOT-INF/classes/db/basis/*"
[[ "$DB_ART" == "postgres" ]] && BASIS_ORDNER="BOOT-INF/classes/db/postgresql/basis/*"
if [[ -n "$BASIS" ]]; then
    container="$(docker create "$BASIS")"
    tmp="$(mktemp -d)"
    docker cp "$container:/app/app.jar" "$tmp/app.jar" >/dev/null
    docker rm "$container" >/dev/null
    if ! unzip -Z1 "$tmp/app.jar" "$BASIS_ORDNER" 2>/dev/null | grep -q '\.sql$'; then
        log "WARNUNG: $BASIS hat noch kein Startsetup fuer $DB_ART - pruefe nur die Neuinstallation."
        [[ -n "${GITHUB_ACTIONS-}" ]] && echo "::warning::Kundenstand ohne Startsetup ($DB_ART) - nur Neuinstallation getestet, kein Upgrade."
        BASIS=""
    fi
    rm -rf "$tmp"
fi

if [[ -n "$BASIS" ]]; then
    log "Kundenstand: $BASIS richtet die leere Datenbank ein ..."
    if ! starten "$BASIS"; then
        log "FEHLER: Der Kundenstand selbst startet nicht - Test nicht aussagekraeftig."
        exit 1
    fi
fi
vorher="$(stand)"

log "Neue Version $NEU ..."
if ! starten "$NEU"; then
    log "FEHLER: Neue Version faehrt nicht sauber hoch."
    exit 1
fi

neue="$(sql "SELECT CONCAT('V', version, ' ', description) FROM flyway_schema_history WHERE installed_rank > $vorher AND success ORDER BY installed_rank" 2>/dev/null | paste -sd ',' || true)"
log "Neue Migrationen in diesem Test: ${neue:-(keine)}"
log "OK - neue Version laeuft auf $DB_ART (/actuator/health = 200)."
