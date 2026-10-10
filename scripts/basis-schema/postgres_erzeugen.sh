#!/usr/bin/env bash
# =============================================================================
# PostgreSQL-Basis-Schema fuer Neuinstallationen erzeugen
# =============================================================================
# Ergebnis: src/main/resources/db/postgresql/basis/V1__basis_schema.sql
# Die App spielt sie auf einer LEEREN PostgreSQL-Datenbank ein
# (FlywayStartSetupConfig), danach laufen die Migrationen aus
# db/postgresql/migration (Gegenstuecke der MySQL-Migrationen).
#
# Grundlage ist die MySQL-Basis (db/basis/V1__basis_schema.sql) - erst sie
# erzeugen (erzeugen.sh), dann diese hier. Ablauf (Docker + python3 noetig):
#   1. Hibernate legt das Schema auf PostgreSQL an (ddl-auto=create, Flyway aus):
#      Spalten und Typen passen damit per Definition zur App.
#   2. postgres_uebertragen.py holt aus der MySQL-Basis, was Hibernate nicht
#      kennt: Indizes/Eindeutigkeiten, Spalten-Defaults, Stammdaten, und gleicht
#      die Regeln an MySQL an (Fremdschluessel mit Loeschregeln, CHECKs,
#      NOT NULL, Textlaengen); danach Sequenzen hinter die groessten IDs.
#   3. Gibt es schon PostgreSQL-Migrationen, kommt ihre Flyway-Historie mit
#      hinein (sie stecken ja im Hibernate-Schema schon drin).
#   4. pg_dump als eine SQL-Datei (ohne Besitzer/Rechte, nur INSERTs).
#   5. Probe: leere PostgreSQL + App-Image mit der neuen Datei muss mit
#      ddl-auto=validate hochfahren (/actuator/health = 200).
#
# Aufruf:  scripts/basis-schema/postgres_erzeugen.sh <app-image> [--nur-pruefen]
#          Das Image muss aus dem aktuellen Stand gebaut sein. Fuer Schritt 5
#          muss es die neue Datei schon enthalten - also nach dem Erzeugen neu
#          bauen und mit --nur-pruefen nachtesten.
# =============================================================================

set -Eeuo pipefail

IMAGE="${1:?Aufruf: postgres_erzeugen.sh <app-image> [--nur-pruefen]}"
NUR_PRUEFEN="${2-}"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MYSQL_BASIS="$REPO/src/main/resources/db/basis/V1__basis_schema.sql"
PG_MIGRATIONEN="$REPO/src/main/resources/db/postgresql/migration"
ZIEL="$REPO/src/main/resources/db/postgresql/basis/V1__basis_schema.sql"
PYTHON_LIBS="$REPO/target/basis-python"
NETZ="erp-pgbasis"
DB="kalkulationsprogramm_db"
PW="basis-$RANDOM$RANDOM"
MYSQL_PORT=13306
PG_PORT=15433
URL="jdbc:postgresql://erp-pgbasis-pg:5432/$DB"
ARBEIT="$(mktemp -d)"

log() {
    printf '[postgres-basis] %s\n' "$*"
}

aufraeumen() {
    docker rm -f erp-pgbasis-mysql erp-pgbasis-pg erp-pgbasis-app >/dev/null 2>&1 || true
    docker network rm "$NETZ" >/dev/null 2>&1 || true
    rm -rf "$ARBEIT"
}
trap aufraeumen EXIT

neue_postgres() {
    docker rm -f erp-pgbasis-pg >/dev/null 2>&1 || true
    docker run -d --name erp-pgbasis-pg --network "$NETZ" -p "127.0.0.1:$PG_PORT:5432" \
        -e POSTGRES_DB="$DB" -e POSTGRES_USER=erp_user -e POSTGRES_PASSWORD="$PW" postgres:16 >/dev/null
    for _ in $(seq 1 60); do
        if docker exec erp-pgbasis-pg psql -U erp_user -d "$DB" -c "SELECT 1" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done
    log "PostgreSQL startet nicht."
    exit 1
}

# app_starten <docker-run-Argumente...>: wartet auf Health 200
app_starten() {
    docker rm -f erp-pgbasis-app >/dev/null 2>&1 || true
    docker run -d --name erp-pgbasis-app --network "$NETZ" -p 127.0.0.1:18098:8080 \
        -e SPRING_PROFILES_ACTIVE=docker,postgres \
        -e APP_DB_URL="$URL" -e APP_DB_USER=erp_user -e APP_DB_PASS="$PW" "$@" "$IMAGE" >/dev/null
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

psql_pg() {
    docker exec -i erp-pgbasis-pg psql -v ON_ERROR_STOP=1 -q -U erp_user -d "$DB" "$@"
}

docker network create "$NETZ" >/dev/null 2>&1 || true

# Jede MySQL-Migration nach der Basis braucht ihren PostgreSQL-Zwilling, bevor
# die Basis neu erzeugt wird: Das Hibernate-Schema enthaelt ihre Aenderungen
# schon - ohne Zwilling in der Historie liefe sie spaeter ein zweites Mal.
# Gleiche Grenze wie PostgresMigrationslinieTest.LETZTE_MIGRATION_VOR_POSTGRES.
LETZTE_MIGRATION_VOR_POSTGRES=406
zwillinge_pruefen() {
    local datei version fehlt=0
    for datei in "$REPO"/src/main/resources/db/migration/V*__*.sql; do
        version="$(basename "$datei" | sed -E 's/^V([0-9]+)__.*/\1/')"
        if (( 10#$version > LETZTE_MIGRATION_VOR_POSTGRES )) && ! compgen -G "$PG_MIGRATIONEN/V${version}__*.sql" >/dev/null; then
            log "Kein PostgreSQL-Zwilling fuer $(basename "$datei") (siehe $PG_MIGRATIONEN/README.md)"
            fehlt=1
        fi
    done
    return "$fehlt"
}

# Die Daten (Stammdaten) kommen aus der MySQL-Basis, die Flyway-Historie aus den
# PostgreSQL-Migrationen. Ist die MySQL-Basis aelter als die neueste
# PostgreSQL-Migration, stuende z. B. eine Datenmigration als "erledigt" in der
# Historie, ohne dass ihre Aenderungen in der Basis stecken.
basis_aktuell_pruefen() {
    local mysql_stand pg_stand
    mysql_stand="$(grep -oE "'V[0-9]+__[A-Za-z0-9_]+\.sql'" "$MYSQL_BASIS" | sed -E "s/'V([0-9]+)__.*/\1/" | sort -n | tail -n 1)"
    pg_stand="$(find "$PG_MIGRATIONEN" -maxdepth 1 -name 'V*__*.sql' -printf '%f\n' | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | tail -n 1)"
    if [[ -n "$pg_stand" && "${mysql_stand:-0}" -lt "$pg_stand" ]]; then
        log "MySQL-Basis ist auf V${mysql_stand:-?}, PostgreSQL-Migrationen gehen bis V$pg_stand - zuerst erzeugen.sh."
        return 1
    fi
}

if [[ "$NUR_PRUEFEN" != "--nur-pruefen" ]]; then
    [[ -s "$MYSQL_BASIS" ]] || { log "MySQL-Basis fehlt - zuerst erzeugen.sh."; exit 1; }
    zwillinge_pruefen || exit 1
    basis_aktuell_pruefen || exit 1
    if [[ ! -d "$PYTHON_LIBS/pymysql" || ! -d "$PYTHON_LIBS/psycopg" ]]; then
        log "Lade Python-Treiber nach $PYTHON_LIBS ..."
        python3 -m pip install -q --target "$PYTHON_LIBS" "pymysql==1.1.1" "psycopg[binary]==3.2.3"
    fi

    log "1/4 MySQL mit MySQL-Basis, PostgreSQL mit Hibernate-Schema ..."
    docker run -d --name erp-pgbasis-mysql --network "$NETZ" -p "127.0.0.1:$MYSQL_PORT:3306" \
        -e MYSQL_ROOT_PASSWORD="$PW" -e MYSQL_DATABASE="$DB" \
        mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null
    for _ in $(seq 1 90); do
        if docker exec -e MYSQL_PWD="$PW" erp-pgbasis-mysql mysql -h 127.0.0.1 -uroot -e "SELECT 1" >/dev/null 2>&1; then
            break
        fi
        sleep 2
    done
    docker exec -i -e MYSQL_PWD="$PW" erp-pgbasis-mysql mysql -uroot --default-character-set=utf8mb4 "$DB" < "$MYSQL_BASIS"
    neue_postgres
    app_starten -e SPRING_FLYWAY_ENABLED=false -e SPRING_JPA_HIBERNATE_DDL_AUTO=create \
        || { log "Hibernate-Schema auf PostgreSQL fehlgeschlagen."; exit 1; }
    # Was die App beim Start selbst anlegt, gehoert nicht in die Basis
    psql_pg -At -c "SELECT string_agg(format('%I', tablename), ', ') FROM pg_tables WHERE schemaname = 'public'" \
        | { read -r tabellen; psql_pg -c "TRUNCATE $tabellen RESTART IDENTITY CASCADE"; }

    log "2/4 Indizes, Defaults und Stammdaten aus der MySQL-Basis ..."
    MYSQL_PW="$PW" PG_PW="$PW" PYTHONPATH="$PYTHON_LIBS" \
        python3 "$REPO/scripts/basis-schema/postgres_uebertragen.py" "$MYSQL_PORT" "$PG_PORT"

    log "3/4 Flyway-Historie vorhandener PostgreSQL-Migrationen ..."
    if compgen -G "$PG_MIGRATIONEN/V*__*.sql" >/dev/null; then
        python3 "$REPO/scripts/basis-schema/flyway_historie.py" "$PG_MIGRATIONEN" --postgres | psql_pg
    else
        log "    (noch keine - Flyway setzt beim ersten Start eine Baseline)"
    fi

    log "4/4 Sicherung als SQL ..."
    mkdir -p "$(dirname "$ZIEL")"
    {
        echo "-- ============================================================================="
        echo "-- PostgreSQL-Basis-Schema fuer NEUINSTALLATIONEN - nicht von Hand bearbeiten!"
        echo "-- Erzeugt mit scripts/basis-schema/postgres_erzeugen.sh (Erklaerung dort)."
        echo "-- Entspricht der MySQL-Basis (db/basis): Schema, Stammdaten, Indizes."
        echo "-- Keine Kunden- oder Personendaten."
        echo "-- Wird nur auf einer LEEREN Datenbank ausgefuehrt (FlywayStartSetupConfig)."
        echo "-- ============================================================================="
        # \restrict-Zeilen (psql-Befehle) und das Leeren des search_path haetten in
        # Flyway nichts verloren - letzteres wuerde die Verbindung fuer die App verstellen.
        docker exec erp-pgbasis-pg pg_dump -U erp_user -d "$DB" --no-owner --no-privileges \
            --no-comments --column-inserts --no-publications --no-subscriptions \
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
