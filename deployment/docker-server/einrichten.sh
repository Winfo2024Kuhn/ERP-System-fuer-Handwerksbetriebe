#!/usr/bin/env bash
# =============================================================================
# ERP Handwerk - Ersteinrichtung auf einem neuen Firmenserver (Linux + Docker)
# =============================================================================
# Einmalig als root ausfuehren. Kann gefahrlos wiederholt werden.
#   1. prueft, ob alles Noetige installiert ist
#   2. legt .env an (beim ersten Mal) und prueft, ob sie ausgefuellt ist
#   3. holt die aktuelle Version, startet die Datenbank und spielt bei Bedarf
#      eine bestehende Datenbank ein (Umzug vom alten Server)
#   4. startet die App und traegt den Cronjob fuer das Nachtupdate um 3 Uhr ein
#
# Aufruf:  sudo ./einrichten.sh [--import <sicherung.sql oder .sql.gz>]
#
# WICHTIG: Eine LEERE Datenbank kann das ERP derzeit nicht selbst aufbauen
# (die Flyway-Migrationen beginnen erst bei V208, das Grundschema davor fehlt).
# Fuer einen Umzug deshalb die Sicherung des bisherigen Servers mit --import
# einspielen.
# =============================================================================

set -Eeuo pipefail
umask 077

SKRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SKRIPT_DIR"
COMPOSE=(docker compose --project-directory "$SKRIPT_DIR" -f "$SKRIPT_DIR/docker-compose.yml")
CRON_DATEI="/etc/cron.d/erp-nachtupdate"

fehler() {
    echo "FEHLER: $*" >&2
    exit 1
}

IMPORT_DATEI=""
if [[ "${1-}" == "--import" ]]; then
    IMPORT_DATEI="${2:?Bitte die Sicherungsdatei angeben: --import <datei.sql[.gz]>}"
    [[ -f "$IMPORT_DATEI" ]] || fehler "Sicherungsdatei nicht gefunden: $IMPORT_DATEI"
    IMPORT_DATEI="$(cd "$(dirname "$IMPORT_DATEI")" && pwd)/$(basename "$IMPORT_DATEI")"
fi

# --- 1. Voraussetzungen ---
[[ "$(id -u)" -eq 0 ]] || fehler "Bitte als root ausfuehren (sudo ./einrichten.sh)."
for befehl in docker curl flock gzip; do
    command -v "$befehl" >/dev/null || fehler "'$befehl' ist nicht installiert."
done
docker compose version >/dev/null 2>&1 || fehler "Docker Compose (Plugin 'docker compose') fehlt."
docker info >/dev/null 2>&1 || fehler "Docker laeuft nicht."

# --- 2. Einstellungen ---
if [[ ! -f .env ]]; then
    cp .env.example .env
    chmod 600 .env
    echo "Die Datei .env wurde angelegt: $SKRIPT_DIR/.env"
    echo "Bitte Passwoerter, KUNDE_NAME und WEBHOOK_URL eintragen und dieses Skript erneut starten."
    exit 1
fi
chmod 600 .env
if grep -q "CHANGE_ME" .env; then
    fehler "In .env stehen noch Platzhalter-Passwoerter (CHANGE_ME). Bitte ersetzen."
fi
erp_image="$(grep -E '^ERP_IMAGE=' .env | tail -n 1 | cut -d= -f2- | tr -d '\r"')"
[[ -n "$erp_image" ]] || fehler "ERP_IMAGE ist in .env nicht gesetzt."
app_port="$(grep -E '^APP_PORT=' .env | tail -n 1 | cut -d= -f2- | tr -d '\r"')"
app_port="${app_port:-8080}"

# --- 3. Version holen und starten ---
echo "Hole $erp_image ..."
if ! docker pull "$erp_image"; then
    cat >&2 <<'HINWEIS'
FEHLER: Download fehlgeschlagen. Ist das Image privat, einmalig an der Registry anmelden:
  1. GitHub -> Settings -> Developer settings -> Personal access tokens (classic)
     -> neues Token NUR mit "read:packages" (pro Kunde ein eigenes Token)
  2. echo <TOKEN> | docker login ghcr.io -u <github-benutzer> --password-stdin
Danach dieses Skript erneut starten.
HINWEIS
    exit 1
fi

if docker image inspect erp-app:aktiv >/dev/null 2>&1; then
    echo "Es ist bereits eine Version installiert - Updates uebernimmt ab jetzt das Nachtupdate."
else
    docker tag "$erp_image" erp-app:aktiv
fi

mkdir -p sicherungen logs .status
chmod +x nachtupdate.sh

echo "Starte Datenbank ..."
"${COMPOSE[@]}" up -d --wait mysql

# Passwort bleibt im MySQL-Container, siehe nachtupdate.sh
# shellcheck disable=SC2016
tabellen="$("${COMPOSE[@]}" exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = \"$MYSQL_DATABASE\""' | tr -d '[:space:]')"

if [[ -n "$IMPORT_DATEI" ]]; then
    [[ "$tabellen" == "0" ]] || fehler "Die Datenbank ist nicht leer ($tabellen Tabellen) - Import abgebrochen, damit nichts ueberschrieben wird."
    echo "Spiele $IMPORT_DATEI ein ..."
    entpacken=(cat)
    [[ "$IMPORT_DATEI" == *.gz ]] && entpacken=(gzip -cd)
    # shellcheck disable=SC2016
    "${entpacken[@]}" "$IMPORT_DATEI" | "${COMPOSE[@]}" exec -T mysql sh -c \
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 "$MYSQL_DATABASE"' \
        || fehler "Import fehlgeschlagen."
    echo "Import fertig."
elif [[ "$tabellen" == "0" ]]; then
    fehler "Die Datenbank ist leer. Das ERP kann sie derzeit nicht selbst aufbauen - bitte die Sicherung des bisherigen Servers einspielen: sudo ./einrichten.sh --import <sicherung.sql.gz>"
fi

echo "Starte das ERP ..."
"${COMPOSE[@]}" up -d app

echo "Warte, bis das ERP antwortet (beim ersten Start laufen ggf. Migrationen) ..."
gesund=nein
for _ in $(seq 1 600); do
    if [[ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://127.0.0.1:$app_port/actuator/health" || true)" == "200" ]]; then
        gesund=ja
        break
    fi
    sleep 1
done
if [[ "$gesund" != "ja" ]]; then
    fehler "Das ERP ist nicht hochgefahren. Fehler ansehen mit: docker compose logs app  (Nachtupdate wurde NICHT eingeplant)"
fi
echo "ERP laeuft: http://$(hostname -I 2>/dev/null | awk '{print $1}'):$app_port"

# --- 4. Nachtupdate einplanen ---
cat > "$CRON_DATEI" <<CRON
# ERP Handwerk: jede Nacht um 3 Uhr (Ortszeit des Servers) nach Updates schauen.
# Log: $SKRIPT_DIR/logs/
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
0 3 * * * root "$SKRIPT_DIR/nachtupdate.sh" >/dev/null 2>&1
CRON
chmod 644 "$CRON_DATEI"
echo "Nachtupdate eingeplant: $CRON_DATEI (taeglich 03:00, Zeitzone: $(cat /etc/timezone 2>/dev/null || date +%Z))"
echo "Fertig. Testlauf jederzeit mit: sudo $SKRIPT_DIR/nachtupdate.sh"
