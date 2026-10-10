#!/usr/bin/env bash
# =============================================================================
# Basis-Schema fuer Neuinstallationen erzeugen
# =============================================================================
# Ergebnis: src/main/resources/db/basis/V1__basis_schema.sql
# Diese Datei spielt die App bei einer LEEREN Datenbank ein (siehe
# FlywayStartSetupConfig), danach laufen die normalen Migrationen weiter.
#
# Warum ueberhaupt? Die Migrationen beginnen bei V208, das Grundschema davor
# hat frueher Hibernate angelegt. Ab null kann Flyway nicht aufbauen.
#
# So entsteht die Datei (Docker + python3 noetig). Leitidee: alles, was die
# Migrationen selbst anlegen, soll exakt aus den Migrationen stammen (Typen,
# Defaults, Indizes, Stammdaten) - Hibernate liefert nur die Alt-Tabellen.
#   1. Hibernate-Schema (Flyway aus, ddl-auto=update) in einer Wegwerf-DB,
#      daraus nur die Tabellen als CREATE TABLE IF NOT EXISTS.
#   2. Alle Migrationen der Reihe nach auf die LEERE Datenbank ("mysql --force",
#      FOREIGN_KEY_CHECKS=0). Was Alt-Tabellen braucht, scheitert noch.
#   3. Alt-Tabellen aus Schritt 1 ergaenzen (bestehende bleiben unberuehrt).
#   4. Die in 2 gescheiterten Migrationen erneut; dann auf den Alt-Tabellen die
#      DEFAULTs aus den Migrationen nachziehen (spalten_nachziehen.py) - die
#      Migrationen haben die Spalten dort als "schon vorhanden" uebersprungen;
#      dann ein letzter Durchgang fuer Stammdaten, die einen Default brauchten.
#   5. flyway_schema_history mit allen Migrationen als "ausgefuehrt"
#      (Flyway-genaue Checksummen, flyway_historie.py).
#   6. mysqldump ohne DEFINER und ohne Prozeduren/Funktionen (die sind nur
#      Hilfsmittel einzelner Migrationen, die App ruft keine auf; anlegen
#      duerfte sie der App-Benutzer bei aktivem Binlog ohnehin nicht).
#   7. Probe: frische MySQL + App-Image mit der neuen Datei muss mit
#      ddl-auto=validate hochfahren (/actuator/health = 200).
#
# Aufruf:  scripts/basis-schema/erzeugen.sh <app-image>
#          Das Image muss aus dem aktuellen Stand gebaut sein (docker build .).
#          Fuer Schritt 7 muss es FlywayStartSetupConfig bereits enthalten -
#          also nach dem Erzeugen neu bauen und mit --nur-pruefen nachtesten.
#
# Wann neu erzeugen? Nicht bei jeder Migration - neue Migrationen laufen auf
# der Basis ganz normal. Nur wenn die Liste der Migrationen sehr lang wird
# oder sich das Hibernate-Schema grundlegend aendert.
# =============================================================================

set -Eeuo pipefail

IMAGE="${1:?Aufruf: erzeugen.sh <app-image> [--nur-pruefen]}"
NUR_PRUEFEN="${2-}"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MIGRATIONEN="$REPO/src/main/resources/db/migration"
ZIEL="$REPO/src/main/resources/db/basis/V1__basis_schema.sql"
NETZ="erp-basis"
DB="kalkulationsprogramm_db"
PW="basis-$RANDOM$RANDOM"
URL="jdbc:mysql://erp-basis-mysql:3306/$DB?useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC"
ARBEIT="$(mktemp -d)"

log() {
    printf '[basis-schema] %s\n' "$*"
}

aufraeumen() {
    docker rm -f erp-basis-mysql erp-basis-app >/dev/null 2>&1 || true
    docker network rm "$NETZ" >/dev/null 2>&1 || true
    rm -rf "$ARBEIT"
}
trap aufraeumen EXIT

mysql_root() {
    docker exec -i -e MYSQL_PWD="$PW" erp-basis-mysql mysql -uroot --default-character-set=utf8mb4 "$@"
}

neue_mysql() {
    docker rm -f erp-basis-mysql >/dev/null 2>&1 || true
    docker run -d --name erp-basis-mysql --network "$NETZ" \
        -e MYSQL_ROOT_PASSWORD="$PW" -e MYSQL_DATABASE="$DB" \
        -e MYSQL_USER=erp_user -e MYSQL_PASSWORD="$PW" \
        mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null
    for _ in $(seq 1 90); do
        # Erst wenn der Init-Lauf fertig ist, antwortet der Server ueber TCP
        if docker exec -e MYSQL_PWD="$PW" erp-basis-mysql mysql -h 127.0.0.1 -uroot -e "SELECT 1" >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
    done
    log "MySQL startet nicht."
    exit 1
}

# app_starten <zusaetzliche docker-run-Argumente...>: wartet auf Health 200
app_starten() {
    docker rm -f erp-basis-app >/dev/null 2>&1 || true
    docker run -d --name erp-basis-app --network "$NETZ" -p 127.0.0.1:18099:8080 \
        -e APP_DB_URL="$URL" -e APP_DB_USER=erp_user -e APP_DB_PASS="$PW" "$@" "$IMAGE" >/dev/null
    for _ in $(seq 1 300); do
        if [[ "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18099/actuator/health || true)" == "200" ]]; then
            docker rm -f erp-basis-app >/dev/null
            return 0
        fi
        if [[ "$(docker inspect -f '{{.State.Running}}' erp-basis-app)" != "true" ]]; then
            break
        fi
        sleep 1
    done
    docker logs --tail 300 erp-basis-app 2>&1 | grep -E "ERROR|Caused by|Message    :|Statement  :" | head -n 30 || true
    return 1
}

docker network create "$NETZ" >/dev/null 2>&1 || true

if [[ "$NUR_PRUEFEN" != "--nur-pruefen" ]]; then
    neue_mysql
    FEHLER_LOG="$REPO/target/basis-schema-fehler.log"
    mkdir -p "$REPO/target"
    : > "$FEHLER_LOG"

    log "1/6 Hibernate-Schema der Alt-Tabellen (Wegwerf-Datenbank) ..."
    mysql_root -e "CREATE DATABASE hibernate_tmp; GRANT ALL ON hibernate_tmp.* TO 'erp_user'@'%';"
    URL_DB="$URL"
    URL="${URL/\/$DB?//hibernate_tmp?}"
    app_starten -e SPRING_FLYWAY_ENABLED=false -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
        || { log "Hibernate-Schema konnte nicht angelegt werden."; exit 1; }
    URL="$URL_DB"
    docker exec -e MYSQL_PWD="$PW" erp-basis-mysql mysqldump -uroot --no-data --compact \
        --no-tablespaces --set-gtid-purged=OFF hibernate_tmp \
        | sed -E 's/^CREATE TABLE /CREATE TABLE IF NOT EXISTS /' > "$ARBEIT/hibernate.sql"
    mysql_root -e "DROP DATABASE hibernate_tmp;"

    log "2/6 Alle Migrationen auf die leere Datenbank ..."
    migrationen_laufen() {  # migrationen_laufen <dateiliste> <durchgang>; gibt Dateien mit Fehlern aus
        local datei
        while IFS= read -r datei; do
            [[ -n "$datei" ]] || continue
            { echo "SET FOREIGN_KEY_CHECKS=0;"; cat "$datei"; } \
                | mysql_root --force "$DB" 2>"$ARBEIT/einzel.log" >/dev/null || true
            if grep -q 'ERROR' "$ARBEIT/einzel.log"; then
                sed "s#^#[$2] $(basename "$datei"): #" "$ARBEIT/einzel.log" >> "$FEHLER_LOG"
                echo "$datei"
            fi
        done < "$1"
    }
    find "$MIGRATIONEN" -name 'V*__*.sql' | sort -V > "$ARBEIT/alle.txt"
    migrationen_laufen "$ARBEIT/alle.txt" 1 > "$ARBEIT/fehler1.txt"
    # Diese Tabellen stammen exakt aus den Migrationen (Schritt 4 laesst sie in Ruhe)
    mysql_root -N -B -e "SELECT table_name FROM information_schema.tables WHERE table_schema='$DB'" > "$ARBEIT/tabellen-aus-migrationen.txt"
    log "    $(wc -l < "$ARBEIT/fehler1.txt") Migrationen brauchen die Alt-Tabellen"

    log "3/6 Alt-Tabellen aus Hibernate ergaenzen ..."
    { echo "SET FOREIGN_KEY_CHECKS=0;"; cat "$ARBEIT/hibernate.sql"; echo "SET FOREIGN_KEY_CHECKS=1;"; } | mysql_root "$DB"

    log "4/6 Diese Migrationen erneut, dann Spalten-Defaults der Alt-Tabellen nachziehen ..."
    migrationen_laufen "$ARBEIT/fehler1.txt" 2 > "$ARBEIT/fehler2.txt"
    python3 "$REPO/scripts/basis-schema/spalten_nachziehen.py" "$MIGRATIONEN" "$ARBEIT/tabellen-aus-migrationen.txt" \
        | { echo "SET FOREIGN_KEY_CHECKS=0;"; cat; } \
        | mysql_root --force "$DB" 2> >(sed 's#^#[spalten] #' >> "$FEHLER_LOG") || true
    # Was jetzt noch scheiterte (z.B. Stammdaten, denen ein Default fehlte), ein letztes Mal
    migrationen_laufen "$ARBEIT/fehler2.txt" 3 > "$ARBEIT/fehler3.txt"
    log "    Restfehler (erwartet: schon vorhandene Spalten, spaeter entfernte Tabellen): $FEHLER_LOG"
    log "    Migrationen mit Fehlern im letzten Durchgang:"
    sed 's#.*/#      #' "$ARBEIT/fehler3.txt"

    log "5/6 Flyway-Historie ..."
    python3 "$REPO/scripts/basis-schema/flyway_historie.py" "$MIGRATIONEN" | mysql_root "$DB"

    log "6/6 Sicherung als SQL ..."
    mkdir -p "$(dirname "$ZIEL")"
    # SC2016: die Backticks im sed-Ausdruck sind MySQL-Bezeichner, keine Shell
    # shellcheck disable=SC2016
    {
        echo "-- ============================================================================="
        echo "-- Basis-Schema fuer NEUINSTALLATIONEN - nicht von Hand bearbeiten!"
        echo "-- Erzeugt mit scripts/basis-schema/erzeugen.sh (Erklaerung dort)."
        echo "-- Enthaelt: komplettes Schema, Stammdaten aus den Migrationen und die"
        echo "-- Flyway-Historie bis V$(find "$MIGRATIONEN" -name 'V*__*.sql' | sed -E 's#.*/V([0-9]+)__.*#\1#' | sort -n | tail -n 1)."
        echo "-- Keine Kunden- oder Personendaten."
        echo "-- Wird nur auf einer LEEREN Datenbank ausgefuehrt (FlywayStartSetupConfig)."
        echo "-- ============================================================================="
        echo "SET NAMES utf8mb4;"
        echo "SET FOREIGN_KEY_CHECKS=0;"
        docker exec -e MYSQL_PWD="$PW" erp-basis-mysql mysqldump -uroot --compact \
            --skip-routines --triggers --events --hex-blob --no-tablespaces --set-gtid-purged=OFF \
            --default-character-set=utf8mb4 "$DB" \
            | sed -E 's/ DEFINER=`[^`]*`@`[^`]*`//g'
        echo "SET FOREIGN_KEY_CHECKS=1;"
    } > "$ARBEIT/basis.sql"
    mv "$ARBEIT/basis.sql" "$ZIEL"
    log "    $ZIEL ($(du -h "$ZIEL" | cut -f1), $(grep -c '^CREATE TABLE' "$ZIEL") Tabellen)"
fi

log "Probe: leere Datenbank + $IMAGE muss hochfahren ..."
neue_mysql
if app_starten; then
    log "OK - Neuinstallation faehrt hoch (ddl-auto=validate, /actuator/health = 200)."
else
    log "Probe fehlgeschlagen. Enthaelt $IMAGE schon FlywayStartSetupConfig und die neue Datei? (neu bauen, dann --nur-pruefen)"
    exit 1
fi
