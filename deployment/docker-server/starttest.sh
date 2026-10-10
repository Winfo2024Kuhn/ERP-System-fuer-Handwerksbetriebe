#!/usr/bin/env bash
# =============================================================================
# Starttest fuer ein neues ERP-Image (laeuft in der GitHub Action, geht auch lokal)
# =============================================================================
# Frage: Faehrt die neue Version auf einer Datenbank hoch, wie sie die Kunden
# gerade haben - inklusive aller NEUEN Flyway-Migrationen?
#
# Warum nicht einfach gegen eine leere Datenbank? Die Migrationen beginnen erst
# bei V208; das Grundschema davor hat frueher Hibernate angelegt. Ab null kann
# Flyway die Datenbank deshalb nicht aufbauen. Stattdessen:
#   1. Schema mit der Version erzeugen, die gerade bei den Kunden laeuft
#      (vorheriges Image, Hibernate ddl-auto=update, Flyway aus).
#   2. Flyway-Stand auf deren hoechste Migration setzen (Baseline).
#   3. Neue Version starten -> sie fuehrt genau die neu hinzugekommenen
#      Migrationen aus. /actuator/health muss 200 liefern.
# Das ist eine Annaeherung (Hibernate-Schema statt echtem Kundenschema, keine
# Daten). Die eigentliche Absicherung pro Kunde bleibt das Nachtupdate mit
# Sicherung + Rollback.
#
# Aufruf:  ./starttest.sh <neues-image> [<image-der-kunden>]
#          Ohne zweites Image wird das neue auch als Ausgangsstand genommen
#          (dann wird nur der Start geprueft, keine Migration).
# =============================================================================

set -Eeuo pipefail

NEU="${1:?Aufruf: starttest.sh <neues-image> [<image-der-kunden>]}"
BASIS="${2:-$NEU}"
WARTEZEIT="${STARTTEST_WARTEZEIT:-300}"
NETZ="erp-starttest"
DB="kalkulationsprogramm_db"
DB_URL="jdbc:mysql://erp-starttest-mysql:3306/$DB?useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC"
# Wegwerf-Zugangsdaten nur fuer diesen Testcontainer
TEST_PW="starttest-$RANDOM$RANDOM"

log() {
    printf '[starttest] %s\n' "$*"
}

aufraeumen() {
    docker rm -f erp-starttest-basis erp-starttest-app erp-starttest-mysql >/dev/null 2>&1 || true
    docker network rm "$NETZ" >/dev/null 2>&1 || true
}
trap aufraeumen EXIT

# migrationen <image>: alle Versionsnummern unter db/migration im JAR, sortiert
migrationen() {
    local tmp container
    tmp="$(mktemp -d)"
    container="$(docker create "$1")"
    docker cp "$container:/app/app.jar" "$tmp/app.jar" >/dev/null
    docker rm "$container" >/dev/null
    unzip -Z1 "$tmp/app.jar" 'BOOT-INF/classes/db/migration/*' \
        | sed -nE 's#^BOOT-INF/classes/db/migration/V([0-9]+)__.*#\1#p' \
        | sort -n
    rm -rf "$tmp"
}

# app_laeuft <container> <port>: wartet auf /actuator/health = 200
app_laeuft() {
    local name="$1" port="$2" ende=$((SECONDS + WARTEZEIT))
    while (( SECONDS < ende )); do
        if [[ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://127.0.0.1:$port/actuator/health" || true)" == "200" ]]; then
            return 0
        fi
        if [[ "$(docker inspect -f '{{.State.Running}}' "$name" 2>/dev/null)" != "true" ]]; then
            log "$name ist abgestuerzt."
            return 1
        fi
        sleep 1
    done
    log "$name antwortet nach $WARTEZEIT s nicht mit 200."
    return 1
}

zeige_fehler() {
    docker logs --tail 400 "$1" 2>&1 | grep -E "ERROR|Caused by|Message    :|Statement  :|Location   :|APPLICATION FAILED" | head -n 40 || true
}

aufraeumen
docker network create "$NETZ" >/dev/null

log "Starte MySQL 8.0 ..."
docker run -d --name erp-starttest-mysql --network "$NETZ" \
    -e MYSQL_ROOT_PASSWORD="$TEST_PW" -e MYSQL_DATABASE="$DB" \
    -e MYSQL_USER=erp_user -e MYSQL_PASSWORD="$TEST_PW" \
    mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null
for _ in $(seq 1 90); do
    if docker exec -e MYSQL_PWD="$TEST_PW" erp-starttest-mysql mysqladmin ping -h 127.0.0.1 -uroot --silent >/dev/null 2>&1; then
        break
    fi
    sleep 2
done

BASIS_MIGRATIONEN="$(migrationen "$BASIS")"
BASELINE="$(tail -n 1 <<<"$BASIS_MIGRATIONEN")"
[[ -n "$BASELINE" ]] || { log "Keine Migrationen im Ausgangs-Image gefunden."; exit 1; }
log "Ausgangsstand: $BASIS (hoechste Migration V$BASELINE)"

# Grenze dieses Tests: Flyway laeuft mit out-of-order=true. Neue Migrationen
# mit einer Nummer UNTER der Baseline (z.B. reservierte Nummernbloecke) laufen
# bei den Kunden, werden hier aber von der Baseline verdeckt. Deutlich melden.
NEU_MIGRATIONEN="$(migrationen "$NEU")"
[[ -n "$NEU_MIGRATIONEN" ]] || { log "Keine Migrationen im neuen Image gefunden."; exit 1; }
verdeckt="$(comm -13 <(sort <<<"$BASIS_MIGRATIONEN") <(sort <<<"$NEU_MIGRATIONEN") \
    | awk -v b="$BASELINE" '$1 <= b {printf "V%s ", $1}')"
if [[ -n "$verdeckt" ]]; then
    log "WARNUNG: Neue Migrationen unterhalb von V$BASELINE werden hier NICHT getestet: $verdeckt"
    if [[ -n "${GITHUB_ACTIONS-}" ]]; then
        echo "::warning::Starttest deckt diese neuen Migrationen nicht ab (Nummer unter V$BASELINE): $verdeckt - Absicherung nur durch Nachtupdate mit Rollback."
    fi
fi

log "Lege Schema mit dem Ausgangs-Image an ..."
docker run -d --name erp-starttest-basis --network "$NETZ" -p 127.0.0.1:18081:8080 \
    -e APP_DB_URL="$DB_URL" -e APP_DB_USER=erp_user -e APP_DB_PASS="$TEST_PW" \
    -e SPRING_FLYWAY_ENABLED=false -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
    "$BASIS" >/dev/null
if ! app_laeuft erp-starttest-basis 18081; then
    zeige_fehler erp-starttest-basis
    log "FEHLER: Ausgangs-Image startet nicht - Test nicht aussagekraeftig."
    exit 1
fi
docker rm -f erp-starttest-basis >/dev/null

log "Starte neues Image $NEU (fuehrt alle Migrationen nach V$BASELINE aus) ..."
docker run -d --name erp-starttest-app --network "$NETZ" -p 127.0.0.1:18080:8080 \
    -e APP_DB_URL="$DB_URL" -e APP_DB_USER=erp_user -e APP_DB_PASS="$TEST_PW" \
    -e SPRING_FLYWAY_BASELINE_VERSION="$BASELINE" \
    "$NEU" >/dev/null
if ! app_laeuft erp-starttest-app 18080; then
    zeige_fehler erp-starttest-app
    log "FEHLER: Neue Version faehrt nicht sauber hoch."
    exit 1
fi

neue="$(docker exec -e MYSQL_PWD="$TEST_PW" erp-starttest-mysql mysql -uroot -N -B \
    -e "SELECT CONCAT('V', version, ' ', description) FROM $DB.flyway_schema_history WHERE success = 1 AND type = 'SQL' ORDER BY installed_rank" 2>/dev/null || true)"
log "Neue Migrationen in diesem Test: ${neue:-(keine)}"
log "OK - neue Version laeuft (/actuator/health = 200)."
