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
# Aufruf:  sudo ./einrichten.sh [--mysql] [--import <sicherung.sql oder .sql.gz>]
#   Standard ist PostgreSQL (Kunden-Installationen). --mysql fuer den eigenen
#   Server; dessen bisherige Datenbank kommt mit --import <mysqldump> mit.
#   Eine Sicherung passt nur zur selben Datenbankart - bei --import wird die
#   Art aus der Datei erkannt.
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
DATENBANK=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --mysql) DATENBANK=mysql; shift ;;
        --postgres) DATENBANK=postgres; shift ;;
        --import)
            IMPORT_DATEI="${2:?Bitte die Sicherungsdatei angeben: --import <datei.sql[.gz]>}"
            [[ -f "$IMPORT_DATEI" ]] || fehler "Sicherungsdatei nicht gefunden: $IMPORT_DATEI"
            shift 2 ;;
        *) fehler "Unbekannte Option: $1 (erlaubt: --mysql, --postgres, --import <datei>)" ;;
    esac
done

# Eine Sicherung passt nur zur selben Datenbankart. Vor dem Anlegen pruefen -
# sonst steht bei vergessenem --mysql schon eine leere PostgreSQL-Installation.
if [[ -n "$IMPORT_DATEI" ]]; then
    if gzip -t "$IMPORT_DATEI" 2>/dev/null; then
        kopf="$(gzip -cd "$IMPORT_DATEI" | head -c 4096 | tr -d '\0' || true)"
    else
        kopf="$(head -c 4096 "$IMPORT_DATEI" | tr -d '\0' || true)"
    fi
    import_art=mysql
    [[ "$kopf" == *"PostgreSQL database dump"* ]] && import_art=postgres
    if [[ -n "$DATENBANK" && "$DATENBANK" != "$import_art" ]]; then
        fehler "Die Sicherung stammt aus $import_art, eingerichtet werden soll $DATENBANK - das passt nicht zusammen."
    fi
    DATENBANK="$import_art"
    echo "Sicherung stammt aus $import_art - richte $import_art ein."
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
    ERP_KUNDE="$kunde" awk -v root="$(zufallspasswort)" -v db="$(zufallspasswort)" -v datenbank="${DATENBANK:-postgres}" '
        /^KUNDE_NAME=/ { print "KUNDE_NAME=" ENVIRON["ERP_KUNDE"]; next }
        /^COMPOSE_PROFILES=/ { print "COMPOSE_PROFILES=" datenbank; next }
        { sub(/CHANGE_ME_ROOT_PW/, root); sub(/CHANGE_ME_DB_PW/, db); print }
    ' .env.example > .env
    echo ".env mit zufaelligen Passwoertern angelegt (Datenbank: ${DATENBANK:-postgres}, KUNDE_NAME=$kunde) - WEBHOOK_URL bei Bedarf eintragen."
else
    vorhanden="$(grep -E '^COMPOSE_PROFILES=' .env | tail -n 1 | cut -d= -f2- | tr -d '\r"' || true)"
    [[ -n "$vorhanden" ]] || fehler ".env stammt aus einer aelteren Einrichtung (COMPOSE_PROFILES fehlt) - mit .env.example abgleichen."
    if [[ -n "$DATENBANK" && "$DATENBANK" != "$vorhanden" ]]; then
        fehler "Diese Installation laeuft schon mit $vorhanden - die Datenbank wird nicht gewechselt."
    fi
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
