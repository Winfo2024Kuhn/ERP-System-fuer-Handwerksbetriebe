#!/usr/bin/env bash
# =============================================================================
# ERP Handwerk - Einrichtung auf einem Linux-Server (Docker)
# =============================================================================
# Einmalig als root ausfuehren. Kann gefahrlos wiederholt werden.
#   1. prueft Docker + Compose und laesst Docker beim Systemstart mitstarten
#   2. legt .env mit zufaelligen Passwoertern an (nur beim ersten Mal)
#   3. startet alles mit "docker compose up -d" und wartet, bis das ERP laeuft
#   4. optional: uebernimmt eine bestehende Datenbank (Umzug vom alten Server)
#
# Aufruf:  sudo ./einrichten.sh [--import <sicherung.sql oder .sql.gz>]
#
# Eine leere Datenbank richtet die App beim ersten Start selbst ein. Das
# Nachtupdate um 3 Uhr uebernimmt der Updater-Container - kein cron noetig.
# Windows-PC: stattdessen Einrichten.cmd doppelklicken.
# =============================================================================

set -Eeuo pipefail
umask 077

SKRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SKRIPT_DIR"
COMPOSE=(docker compose --project-directory "$SKRIPT_DIR" -f "$SKRIPT_DIR/docker-compose.yml")

fehler() {
    echo "FEHLER: $*" >&2
    exit 1
}

IMPORT_DATEI=""
if [[ "${1-}" == "--import" ]]; then
    IMPORT_DATEI="${2:?Bitte die Sicherungsdatei angeben: --import <datei.sql[.gz]>}"
    [[ -f "$IMPORT_DATEI" ]] || fehler "Sicherungsdatei nicht gefunden: $IMPORT_DATEI"
fi

# --- 1. Voraussetzungen ---
[[ "$(id -u)" -eq 0 ]] || fehler "Bitte als root ausfuehren (sudo ./einrichten.sh)."
command -v docker >/dev/null || fehler "Docker ist nicht installiert (https://docs.docker.com/engine/install/)."
docker compose version >/dev/null 2>&1 || fehler "Docker Compose (Plugin 'docker compose') fehlt."
command -v curl >/dev/null || fehler "'curl' ist nicht installiert."
# Host-Cronjob aus einer frueheren Einrichtung: das Nachtupdate laeuft jetzt im
# Updater-Container - zwei Laeufe mit getrennten Sperren duerfen nicht sein.
rm -f /etc/cron.d/erp-nachtupdate
if command -v systemctl >/dev/null; then
    systemctl enable --now docker >/dev/null 2>&1 || true
fi
docker info >/dev/null 2>&1 || fehler "Docker laeuft nicht."

# --- 2. Einstellungen ---
zufallspasswort() {
    tr -dc 'A-HJ-NP-Za-km-z2-9' < /dev/urandom | head -c 32 || true
}
if [[ ! -f .env ]]; then
    kunde="${KUNDE_NAME:-$(hostname)}"
    # awk statt sed, Name ueber ENVIRON (nicht -v, das wertet \n usw. aus):
    # Sonderzeichen im Firmennamen (& / \ | $) bleiben, wie sie sind
    ERP_KUNDE="$kunde" awk -v root="$(zufallspasswort)" -v db="$(zufallspasswort)" '
        /^KUNDE_NAME=/ { print "KUNDE_NAME=" ENVIRON["ERP_KUNDE"]; next }
        { sub(/CHANGE_ME_ROOT_PW/, root); sub(/CHANGE_ME_DB_PW/, db); print }
    ' .env.example > .env
    echo ".env mit zufaelligen Passwoertern angelegt (KUNDE_NAME=$kunde) - WEBHOOK_URL bei Bedarf eintragen."
fi
chmod 600 .env
if grep -q "CHANGE_ME" .env; then
    fehler "In .env stehen noch Platzhalter-Passwoerter (CHANGE_ME)."
fi
app_port="$(grep -E '^APP_PORT=' .env | tail -n 1 | cut -d= -f2- | tr -d '\r"')"
app_port="${app_port:-8080}"
mkdir -p sicherungen logs .status

# --- 3. Starten ---
echo "Starte das ERP (beim ersten Mal werden ca. 1 GB geladen) ..."
"${COMPOSE[@]}" up -d

# --- 4. Optional: bestehende Datenbank uebernehmen ---
if [[ -n "$IMPORT_DATEI" ]]; then
    ziel="$SKRIPT_DIR/.status/$(basename "$IMPORT_DATEI")"
    cp "$IMPORT_DATEI" "$ziel"
    if ! "${COMPOSE[@]}" exec -T updater bash /erp/nachtupdate.sh --importieren ".status/$(basename "$IMPORT_DATEI")"; then
        rm -f "$ziel"
        fehler "Import fehlgeschlagen - siehe logs/."
    fi
    rm -f "$ziel"
fi

echo "Warte, bis das ERP antwortet (der erste Start richtet die Datenbank ein) ..."
for _ in $(seq 1 900); do
    if [[ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://127.0.0.1:$app_port/actuator/health" || true)" == "200" ]]; then
        echo "ERP laeuft: http://$(hostname -I 2>/dev/null | awk '{print $1}'):$app_port"
        echo "Nachtupdate: taeglich 03:00 (Updater-Container). Testlauf: docker compose exec updater bash /erp/nachtupdate.sh"
        exit 0
    fi
    sleep 1
done
fehler "Das ERP ist nicht hochgefahren. Fehler ansehen mit: docker compose logs app"
