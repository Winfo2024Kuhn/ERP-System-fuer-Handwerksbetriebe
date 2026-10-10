#!/usr/bin/env bash
# =============================================================================
# PostgreSQL-Basis-Schema fuer Neuinstallationen neu erzeugen
# =============================================================================
# Ergebnis: src/main/resources/db/postgresql/basis/V1__basis_schema.sql
# Die App spielt sie auf einer LEEREN Datenbank ein (FlywayStartSetupConfig),
# danach laufen die Migrationen aus db/postgresql/migration.
#
# Wozu: Eine Neuinstallation spielt sonst jede Migration einzeln ein. Die neue
# Basis fasst die bisherige Basis und alle Migrationen zu einer Datei zusammen
# - inklusive Flyway-Historie, damit Flyway die Migrationen danach als
# "erledigt" kennt. Bestehende Installationen merken davon nichts: Die Basis
# laeuft nur auf leeren Datenbanken, und die Migrationsdateien bleiben liegen.
#
# Ablauf (nur Docker noetig):
#   1. Leere PostgreSQL 16, darauf Flyway (gleiche Version wie die App) genau
#      wie FlywayStartSetupConfig: erst die bisherige Basis (eigene Historien-
#      tabelle flyway_basis_history), dann alle Migrationen. Die App selbst
#      startet dabei NICHT - was sie beim Start anlegt (z. B. erzeugte
#      Schluessel), gehoert nicht in eine Datei fuer alle Neuinstallationen.
#   2. pg_dump als eine SQL-Datei (ohne Besitzer/Rechte, nur INSERTs, ohne
#      flyway_basis_history - die legt Flyway beim Einspielen selbst an).
#   3. Probe: leere PostgreSQL + App-Image mit der neuen Datei muss mit
#      ddl-auto=validate hochfahren (/actuator/health = 200).
#
# Aufruf:  scripts/basis-schema/postgres_erzeugen.sh <app-image> [--nur-pruefen]
#          Fuer Schritt 3 muss das Image die neue Datei schon enthalten - also
#          nach dem Erzeugen neu bauen und mit --nur-pruefen nachtesten.
# Danach: FlywayMigrationenTest.LETZTE_MIGRATION_IN_DER_BASIS und "Alles bis V..."
#         in db/postgresql/migration/README.md auf die letzte enthaltene Migration
#         anheben, dann FlywayMigrationenTest laufen lassen (prueft u. a. die
#         Checksummen der Historie in der neuen Basis).
# =============================================================================

set -Eeuo pipefail

IMAGE="${1:?Aufruf: postgres_erzeugen.sh <app-image> [--nur-pruefen]}"
NUR_PRUEFEN="${2-}"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BASIS_ORDNER="$REPO/src/main/resources/db/postgresql/basis"
MIGRATIONEN="$REPO/src/main/resources/db/postgresql/migration"
ZIEL="$BASIS_ORDNER/V1__basis_schema.sql"
# Gleiche Version wie flyway-core der App (spring-boot-starter-parent 3.2.x)
FLYWAY_IMAGE="flyway/flyway:9.22.3"
NETZ="erp-pgbasis"
DB="kalkulationsprogramm_db"
PW="basis-$RANDOM$RANDOM"
URL="jdbc:postgresql://erp-pgbasis-pg:5432/$DB"
ARBEIT="$(mktemp -d)"

log() {
    printf '[postgres-basis] %s\n' "$*"
}

aufraeumen() {
    docker rm -f erp-pgbasis-pg erp-pgbasis-app erp-pgbasis-flyway >/dev/null 2>&1 || true
    docker network rm "$NETZ" >/dev/null 2>&1 || true
    rm -rf "$ARBEIT"
}
trap aufraeumen EXIT

# Wie in Produktion (deployment/docker-server/postgres-init): Flyway und App arbeiten
# mit einer Rolle OHNE Superuser-Rechte, die Datenbank und Schema "public" besitzt.
neue_postgres() {
    docker rm -f erp-pgbasis-pg >/dev/null 2>&1 || true
    docker run -d --name erp-pgbasis-pg --network "$NETZ" \
        -e POSTGRES_DB="$DB" -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD="$PW" postgres:16 >/dev/null
    for _ in $(seq 1 60); do
        # Ueber TCP: der kurze Init-Server des Images lauscht nur am Unix-Socket
        if docker exec -e PGPASSWORD="$PW" erp-pgbasis-pg psql -h 127.0.0.1 -U postgres -d "$DB" -c "SELECT 1" >/dev/null 2>&1; then
            docker exec erp-pgbasis-pg psql -v ON_ERROR_STOP=1 -q -U postgres -d "$DB" \
                -c "CREATE ROLE erp_user LOGIN PASSWORD '$PW'" \
                -c "ALTER DATABASE $DB OWNER TO erp_user" \
                -c "ALTER SCHEMA public OWNER TO erp_user"
            return 0
        fi
        sleep 1
    done
    log "PostgreSQL startet nicht."
    exit 1
}

# flyway <ordner-im-repo> <weitere Flyway-Argumente...>: Dateien per docker cp
# statt Volume - funktioniert auch unter Git Bash auf Windows.
flyway() {
    local ordner="$1"
    shift
    docker rm -f erp-pgbasis-flyway >/dev/null 2>&1 || true
    docker create --name erp-pgbasis-flyway --network "$NETZ" "$FLYWAY_IMAGE" \
        -url="$URL" -user=erp_user -password="$PW" -locations=filesystem:/flyway/skripte "$@" migrate >/dev/null
    docker cp "$ordner/." erp-pgbasis-flyway:/flyway/skripte
    docker start -a erp-pgbasis-flyway
}

# app_starten: wartet auf Health 200
app_starten() {
    docker rm -f erp-pgbasis-app >/dev/null 2>&1 || true
    docker run -d --name erp-pgbasis-app --network "$NETZ" -p 127.0.0.1:18098:8080 \
        -e SPRING_PROFILES_ACTIVE=docker \
        -e APP_DB_URL="$URL" -e APP_DB_USER=erp_user -e APP_DB_PASS="$PW" "$IMAGE" >/dev/null
    for _ in $(seq 1 300); do
        if [[ "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18098/actuator/health || true)" == "200" ]]; then
            docker rm -f erp-pgbasis-app >/dev/null
            return 0
        fi
        if [[ "$(docker inspect -f '{{.State.Running}}' erp-pgbasis-app)" != "true" ]]; then
            break
        fi
        sleep 1
    done
    docker logs --tail 300 erp-pgbasis-app 2>&1 | grep -E "ERROR|Caused by|Message    :|Statement  :" | head -n 30 || true
    return 1
}

docker network create "$NETZ" >/dev/null 2>&1 || true

if [[ "$NUR_PRUEFEN" != "--nur-pruefen" ]]; then
    [[ -s "$ZIEL" ]] || { log "Bisherige Basis $ZIEL fehlt."; exit 1; }

    log "1/2 Leere PostgreSQL, bisherige Basis + alle Migrationen per Flyway ..."
    neue_postgres
    # Wie FlywayStartSetupConfig: eigene Historientabelle, Platzhalter aus (Textvorlagen enthalten ${...})
    flyway "$BASIS_ORDNER" -table=flyway_basis_history -baselineOnMigrate=false -placeholderReplacement=false
    # Wie application.properties der App
    flyway "$MIGRATIONEN" -baselineOnMigrate=true -outOfOrder=true -validateOnMigrate=false

    log "2/2 Sicherung als SQL ..."
    {
        echo "-- ============================================================================="
        echo "-- PostgreSQL-Basis-Schema fuer NEUINSTALLATIONEN - nicht von Hand bearbeiten!"
        echo "-- Erzeugt mit scripts/basis-schema/postgres_erzeugen.sh (Erklaerung dort)."
        echo "-- Schema, Stammdaten, Indizes und Flyway-Historie der enthaltenen Migrationen."
        echo "-- Keine Kunden- oder Personendaten."
        echo "-- Wird nur auf einer LEEREN Datenbank ausgefuehrt (FlywayStartSetupConfig)."
        echo "-- ============================================================================="
        # \restrict-Zeilen (psql-Befehle) und das Leeren des search_path haetten in
        # Flyway nichts verloren - letzteres wuerde die Verbindung fuer die App verstellen.
        docker exec erp-pgbasis-pg pg_dump -U erp_user -d "$DB" --no-owner --no-privileges \
            --no-comments --column-inserts --no-publications --no-subscriptions \
            --exclude-table=flyway_basis_history \
            | grep -v -e '^[\]' -e "set_config('search_path'"
    } > "$ARBEIT/basis.sql"
    mv "$ARBEIT/basis.sql" "$ZIEL"
    log "    $ZIEL ($(du -h "$ZIEL" | cut -f1), $(grep -c '^CREATE TABLE' "$ZIEL") Tabellen)"
fi

log "Probe: leere PostgreSQL + $IMAGE muss hochfahren ..."
neue_postgres
if app_starten; then
    log "OK - Neuinstallation auf PostgreSQL faehrt hoch (ddl-auto=validate, /actuator/health = 200)."
else
    log "Probe fehlgeschlagen. Enthaelt $IMAGE schon die neue Datei? (neu bauen, dann --nur-pruefen)"
    exit 1
fi
