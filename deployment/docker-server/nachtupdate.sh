#!/usr/bin/env bash
# =============================================================================
# ERP Handwerk - Nachtupdate mit automatischem Rollback
# =============================================================================
# Laeuft per Cronjob jede Nacht um 3 Uhr auf dem Firmenserver (siehe
# einrichten.sh). Pull-Prinzip: Der Server holt sich neue Versionen selbst ab,
# GitHub braucht keinen Zugang ins Firmennetz.
#
# Ablauf - erst absichern, dann schrauben:
#   1. Neue Version aus der Registry holen. Ist es dieselbe wie aktiv -> fertig.
#   2. Pruefen, dass die aktuelle Version gesund laeuft (sonst kein Update).
#   3. App stoppen, DANN Datenbank sichern. In dieser Reihenfolge, damit
#      zwischen Sicherung und Update nichts mehr geschrieben wird, was ein
#      Rollback verschlucken wuerde.
#   4. Neue Version starten - sie fuehrt offene Flyway-Migrationen selbst aus.
#   5. /actuator/health abfragen, bis 200 kommt oder die Wartezeit um ist.
#   6. Fehlgeschlagen -> neue Version stoppen, Datenbank aus der Sicherung
#      zurueckholen, alte Version starten, Handy-Nachricht schicken.
#
# Eine Version, die schon einmal gescheitert ist, wird nicht jede Nacht neu
# versucht, sondern erst wieder, wenn eine neuere kommt (oder mit --erzwingen).
#
# Aufruf:   ./nachtupdate.sh [--erzwingen]
# Exit-Codes:
#   0  nichts zu tun oder Update erfolgreich
#   1  Update nicht eingespielt bzw. zurueckgerollt - alte Version laeuft
#   2  NOTFALL: Rollback fehlgeschlagen, ERP laeuft evtl. nicht
#   3  Voraussetzung fehlt (Einrichtung, Docker, .env ...)
# =============================================================================

# SC2016: Die $MYSQL_*-Variablen in den sh -c '...'-Aufrufen sollen bewusst erst
#         IM MySQL-Container aufgeloest werden - das Passwort verlaesst ihn nie.
# SC2012: ls -t sortiert nur unsere eigenen Sicherungsdateien (ohne Leerzeichen).
# shellcheck disable=SC2016,SC2012

set -Eeuo pipefail
# Sicherungen und Logs enthalten Kundendaten: nur fuer root lesbar
umask 077

SKRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SKRIPT_DIR"

ENV_DATEI="$SKRIPT_DIR/.env"
SICHERUNGS_DIR="$SKRIPT_DIR/sicherungen"
LOG_DIR="$SKRIPT_DIR/logs"
STATUS_DIR="$SKRIPT_DIR/.status"
# Hier merkt sich das Skript die Image-ID einer gescheiterten Version
GESCHEITERT_DATEI="$STATUS_DIR/gescheiterte-version"

AKTIV_TAG="erp-app:aktiv"
VORHER_TAG="erp-app:vorher"
GESCHEITERT_TAG="erp-app:gescheitert"
COMPOSE=(docker compose --project-directory "$SKRIPT_DIR" -f "$SKRIPT_DIR/docker-compose.yml")

ERZWINGEN=nein
if [[ "${1-}" == "--erzwingen" ]]; then
    ERZWINGEN=ja
fi

ZEITSTEMPEL="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$SICHERUNGS_DIR" "$LOG_DIR" "$STATUS_DIR"
LOG_DATEI="$LOG_DIR/nachtupdate-$ZEITSTEMPEL.log"
exec > >(tee -a "$LOG_DATEI") 2>&1

log() {
    printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

# Liest einen Wert aus der .env, ohne sie als Shell-Skript auszufuehren.
# env_wert KEY [Standardwert]
env_wert() {
    local zeile wert
    zeile="$(grep -E "^$1=" "$ENV_DATEI" 2>/dev/null | tail -n 1 || true)"
    wert="${zeile#*=}"
    wert="${wert%$'\r'}"
    wert="${wert%\"}"
    wert="${wert#\"}"
    if [[ -z "$zeile" || -z "$wert" ]]; then
        wert="${2-}"
    fi
    printf '%s' "$wert"
}

if [[ ! -f "$ENV_DATEI" ]]; then
    log "FEHLER: $ENV_DATEI fehlt - zuerst einrichten.sh ausfuehren."
    exit 3
fi

ERP_IMAGE="$(env_wert ERP_IMAGE)"
KUNDE_NAME="$(env_wert KUNDE_NAME "$(hostname)")"
APP_PORT="$(env_wert APP_PORT 8080)"
HEALTH_URL="$(env_wert HEALTH_URL "http://127.0.0.1:$APP_PORT/actuator/health")"
WARTEZEIT="$(env_wert HEALTH_WARTEZEIT_SEKUNDEN 300)"
SICHERUNGEN_BEHALTEN="$(env_wert SICHERUNGEN_BEHALTEN 14)"
WEBHOOK_URL="$(env_wert WEBHOOK_URL)"
WEBHOOK_TOKEN="$(env_wert WEBHOOK_TOKEN)"
WEBHOOK_FORMAT="$(env_wert WEBHOOK_FORMAT ntfy)"
WEBHOOK_BEI_ERFOLG="$(env_wert WEBHOOK_BEI_ERFOLG nein)"
WEBHOOK_MIT_FEHLERAUSZUG="$(env_wert WEBHOOK_MIT_FEHLERAUSZUG nein)"
# Mindestens so viel freier Platz (in MB) muss fuer die Sicherung da sein
MIN_FREI_MB=1024

# Zahlen pruefen, BEVOR irgendetwas veraendert wird: ein Tippfehler wie
# "300s" wuerde sonst mitten im Health-Check die Shell-Arithmetik sprengen -
# und damit ausgerechnet nach dem Versionswechsel Health-Check und Rollback.
zahl_pruefen() {  # zahl_pruefen <Name> <Wert> <Minimum>
    if [[ ! "$2" =~ ^[0-9]+$ ]] || (( 10#$2 < $3 )); then
        log "FEHLER: $1=$2 in .env ist ungueltig (ganze Zahl >= $3 erwartet). Nichts veraendert."
        exit 3
    fi
}
zahl_pruefen HEALTH_WARTEZEIT_SEKUNDEN "$WARTEZEIT" 30
zahl_pruefen SICHERUNGEN_BEHALTEN "$SICHERUNGEN_BEHALTEN" 1
zahl_pruefen APP_PORT "$APP_PORT" 1
WARTEZEIT=$((10#$WARTEZEIT))
SICHERUNGEN_BEHALTEN=$((10#$SICHERUNGEN_BEHALTEN))

if [[ -n "$WEBHOOK_URL" && ! "$WEBHOOK_URL" =~ ^https:// && ! "$WEBHOOK_URL" =~ ^http://(127\.0\.0\.1|localhost)[:/] ]]; then
    log "WARNUNG: WEBHOOK_URL ist nicht https - Nachrichten gehen unverschluesselt raus."
fi

PHASE="start"
SICHERUNG=""
ALT_ID=""
NEU_ID=""
ALT_VERSION="?"
NEU_VERSION="?"

# -----------------------------------------------------------------------------
# Handy-Nachricht
# -----------------------------------------------------------------------------

json_string() {
    local s="$1"
    s="$(printf '%s' "$s" | tr -d '\000-\010\013\014\016-\037')"
    s="${s//\\/\\\\}"
    s="${s//\"/\\\"}"
    s="${s//$'\t'/\\t}"
    s="${s//$'\r'/}"
    s="${s//$'\n'/\\n}"
    printf '"%s"' "$s"
}

# benachrichtigen <dringend|hoch|leise> <Titel> <Text>
benachrichtigen() {
    local stufe="$1" titel="$2" text="$3"
    log "Nachricht [$stufe]: $titel"
    if [[ -z "$WEBHOOK_URL" ]]; then
        log "(kein WEBHOOK_URL gesetzt - Nachricht steht nur in diesem Log)"
        return 0
    fi

    # Token ueber eine Kopfzeilen-Datei (nur fuer root lesbar, s. umask), damit
    # er nicht in der Prozessliste steht
    local kopf_datei="$STATUS_DIR/webhook-kopf"
    : > "$kopf_datei"
    if [[ -n "$WEBHOOK_TOKEN" ]]; then
        printf 'Authorization: Bearer %s\n' "$WEBHOOK_TOKEN" > "$kopf_datei"
    fi
    local -a kopf=(-H "@$kopf_datei")

    local ok=0
    if [[ "$WEBHOOK_FORMAT" == "json" ]]; then
        curl -fsS --max-time 20 -X POST "${kopf[@]}" -H "Content-Type: application/json" \
            --data-binary "{\"text\": $(json_string "$titel"$'\n'"$text")}" \
            "$WEBHOOK_URL" >/dev/null || ok=1
    else
        local prio tags
        case "$stufe" in
            dringend) prio=5; tags="rotating_light" ;;
            hoch)     prio=4; tags="warning" ;;
            *)        prio=2; tags="white_check_mark" ;;
        esac
        # ntfy versteht Umlaute im Titel nur RFC-2047-kodiert
        curl -fsS --max-time 20 -X POST "${kopf[@]}" \
            -H "Title: =?UTF-8?B?$(printf '%s' "$titel" | base64 | tr -d '\n')?=" \
            -H "Priority: $prio" -H "Tags: $tags" \
            --data-binary "$text" "$WEBHOOK_URL" >/dev/null || ok=1
    fi
    rm -f "$kopf_datei"
    if [[ $ok -ne 0 ]]; then
        log "WARNUNG: Nachricht konnte nicht gesendet werden."
    fi
    return 0
}

# Die aussagekraeftigsten Fehlerzeilen aus dem App-Log, ohne Kundendaten:
# geschwaerzt werden Werte in Anfuehrungszeichen (z.B. "Duplicate entry '...'"),
# Objekt-Inhalte in {...} (toString von Entities), E-Mail- und IP-Adressen.
# Das volle Log bleibt auf dem Server. Nur mit WEBHOOK_MIT_FEHLERAUSZUG=ja.
fehler_auszug() {
    grep -E "ERROR|Exception|Caused by|APPLICATION FAILED|Migration.*failed" "$1" 2>/dev/null \
        | grep -vE '^[[:space:]]+at ' \
        | sed -E "s/'[^']*'/'…'/g; s/\"[^\"]*\"/\"…\"/g; s/\{[^}]*\}/{…}/g; s/[[:alnum:]._%+-]+@[[:alnum:].-]+/…@…/g; s/[0-9]{1,3}(\.[0-9]{1,3}){3}/…/g" \
        | cut -c1-300 \
        | head -n 8 || true
}

# -----------------------------------------------------------------------------
# Bausteine
# -----------------------------------------------------------------------------

image_version() {
    local rev
    rev="$(docker image inspect -f '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$1" 2>/dev/null || true)"
    if [[ -z "$rev" || "$rev" == "<no value>" ]]; then
        rev="$(docker image inspect -f '{{.Id}}' "$1" 2>/dev/null | sed 's/^sha256://' || true)"
    fi
    printf '%s' "${rev:0:7}"
}

app_container() {
    "${COMPOSE[@]}" ps -a -q app 2>/dev/null | head -n 1
}

# Wartet, bis /actuator/health mit 200 antwortet. Bricht frueh ab, wenn der
# Container abstuerzt oder (bei frisch gestarteten Containern) neu gestartet
# wurde. Fuer die schon laufende alte Version zaehlen alte Neustarts nicht.
# warte_auf_gesund <Sekunden> [neustarts_zaehlen: ja|nein]
warte_auf_gesund() {
    local ende=$((SECONDS + $1)) zaehlen="${2:-ja}" code container status neustarts
    while (( SECONDS < ende )); do
        code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$HEALTH_URL" || true)"
        if [[ "$code" == "200" ]]; then
            return 0
        fi
        container="$(app_container)"
        if [[ -z "$container" ]]; then
            log "App-Container fehlt."
            return 1
        fi
        status="$(docker inspect -f '{{.State.Status}}' "$container" 2>/dev/null || echo unbekannt)"
        neustarts="$(docker inspect -f '{{.RestartCount}}' "$container" 2>/dev/null || echo 0)"
        if [[ "$zaehlen" != "ja" ]]; then
            neustarts=0
        fi
        if [[ "$status" == "exited" || "$status" == "dead" || "$neustarts" -gt 0 ]]; then
            log "App-Container ist abgestuerzt (Status: $status, Neustarts: $neustarts)."
            return 1
        fi
        sleep 1
    done
    log "Keine Antwort mit 200 von $HEALTH_URL innerhalb von $1 s."
    return 1
}

app_starten() {
    "${COMPOSE[@]}" up -d --no-deps --force-recreate app
}

# Komplette Sicherung der Datenbank. Das Passwort bleibt im MySQL-Container
# (dort liegt es schon als Umgebungsvariable) und taucht in keiner
# Prozessliste auf.
sicherung_erstellen() {
    local ziel="$SICHERUNGS_DIR/vor-update-$ZEITSTEMPEL.sql.gz"
    local unfertig="$ziel.unfertig"
    if ! "${COMPOSE[@]}" exec -T mysql sh -c \
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --quick --routines --triggers --events --hex-blob --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 "$MYSQL_DATABASE"' \
        | gzip > "$unfertig"; then
        rm -f "$unfertig"
        return 1
    fi
    # mysqldump schreibt als letzte Zeile "-- Dump completed ..." - fehlt sie,
    # ist die Sicherung abgeschnitten und taugt nicht fuer einen Rollback.
    if ! gzip -cd "$unfertig" | tail -n 1 | grep -q "Dump completed"; then
        log "Sicherung ist unvollstaendig."
        rm -f "$unfertig"
        return 1
    fi
    mv "$unfertig" "$ziel"
    SICHERUNG="$ziel"
    log "Sicherung: $ziel ($(du -h "$ziel" | cut -f1))"
}

# Setzt die Datenbank komplett auf den Stand der Sicherung zurueck.
# Leeren + neu anlegen ist noetig, weil MySQL DDL nicht in Transaktionen
# kapselt: eine halb gelaufene Migration hinterlaesst neue Tabellen/Spalten
# und einen Fehleintrag in flyway_schema_history.
datenbank_zuruecksetzen() {
    # Erst pruefen, ob die Sicherung lesbar ist - sonst wuerde das DROP eine
    # leere Datenbank hinterlassen.
    if [[ ! -s "$1" ]] || ! gzip -t "$1" 2>/dev/null; then
        log "Sicherung fehlt oder ist beschaedigt: $1 - Datenbank bleibt unangetastet."
        return 1
    fi
    "${COMPOSE[@]}" exec -T mysql sh -c \
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -e "DROP DATABASE IF EXISTS \`$MYSQL_DATABASE\`; CREATE DATABASE \`$MYSQL_DATABASE\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"' \
        || return 1
    gzip -cd "$1" | "${COMPOSE[@]}" exec -T mysql sh -c \
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 "$MYSQL_DATABASE"' \
        || return 1
}

aufraeumen() {
    # Nur die neuesten Sicherungen behalten (Dateinamen ohne Leerzeichen, s.o.)
    local alt
    alt="$(ls -1t "$SICHERUNGS_DIR"/vor-update-*.sql.gz 2>/dev/null | tail -n +"$((SICHERUNGEN_BEHALTEN + 1))" || true)"
    if [[ -n "$alt" ]]; then
        log "Loesche alte Sicherungen: $(echo "$alt" | wc -l) Datei(en)"
        echo "$alt" | xargs -r rm -f --
    fi
    find "$LOG_DIR" -name '*.log' -mtime +90 -delete 2>/dev/null || true
    # Nur unbenannte Image-Reste; erp-app:vorher bleibt fuer den naechsten Rollback
    docker image prune -f >/dev/null 2>&1 || true
}

# -----------------------------------------------------------------------------
# Rollback
# -----------------------------------------------------------------------------

# zurueckrollen <Grund> [Version sperren: ja|nein]
# Gesperrt (= naechste Nacht nicht erneut versucht) wird eine Version nur, wenn
# sie selbst nicht hochkam - nicht nach einem Abbruch von aussen.
zurueckrollen() {
    local grund="$1" sperren="${2:-ja}"
    trap - ERR
    set +e
    PHASE="rollback"
    log "=== ROLLBACK: $grund ==="

    local app_log="$LOG_DIR/gescheitert-$ZEITSTEMPEL-app.log"
    "${COMPOSE[@]}" logs --no-color --no-log-prefix --tail 1000 app > "$app_log" 2>&1
    local auszug
    auszug="$(fehler_auszug "$app_log")"

    "${COMPOSE[@]}" stop app
    if [[ "$sperren" == "ja" ]]; then
        docker tag "$NEU_ID" "$GESCHEITERT_TAG" 2>/dev/null
        printf '%s\n' "$NEU_ID" > "$GESCHEITERT_DATEI"
    fi

    local text
    text="Version $ALT_VERSION -> $NEU_VERSION
Grund: $grund"
    if [[ "$WEBHOOK_MIT_FEHLERAUSZUG" == "ja" ]]; then
        text+="
Fehler:
${auszug:-(keine Fehlerzeile im Log gefunden)}"
    fi
    text+="

Volles Log auf dem Server: logs/$(basename "$app_log")"

    log "Setze Datenbank auf die Sicherung zurueck: $SICHERUNG"
    if datenbank_zuruecksetzen "$SICHERUNG" \
        && docker tag "$VORHER_TAG" "$AKTIV_TAG" \
        && app_starten \
        && warte_auf_gesund "$WARTEZEIT"; then
        log "Rollback erfolgreich - alte Version $ALT_VERSION laeuft wieder."
        benachrichtigen hoch "ERP-Update fehlgeschlagen - $KUNDE_NAME" \
            "Alte Version laeuft wieder, Datenbank auf Stand vor dem Update. Der Betrieb merkt nichts.
$text"
        exit 1
    fi

    log "NOTFALL: Rollback fehlgeschlagen!"
    benachrichtigen dringend "NOTFALL ERP - $KUNDE_NAME" \
        "Update UND Rollback fehlgeschlagen - das ERP laeuft vermutlich NICHT.
Sicherung: sicherungen/$(basename "$SICHERUNG")
Alte Version: $VORHER_TAG
$text"
    exit 2
}

# Unerwarteter Skriptfehler (ERR) oder Abbruch von aussen (Shutdown, kill):
# je nach Schritt die alte Version wieder hochbringen. Wichtig, weil
# "compose stop" den Container als manuell gestoppt markiert - ohne diesen
# Trap bliebe das ERP nach einem Reboot mitten im Update einfach aus.
# shellcheck disable=SC2329  # wird ueber die Traps aufgerufen
bei_fehler() {
    local was="$1"
    trap - ERR INT TERM HUP
    set +e
    # Bei Shutdown/Strg+C stirbt der tee-Prozess der Ausgabeumleitung mit.
    # Die erste Ausgabe in die tote Pipe wuerde den Handler sonst abbrechen -
    # also ab hier direkt in die Logdatei schreiben.
    exec >>"$LOG_DATEI" 2>&1
    local log_name
    log_name="logs/$(basename "$LOG_DATEI")"
    log "Abbruch: $was (Schritt: $PHASE)."
    case "$PHASE" in
        gewechselt)
            zurueckrollen "$was nach dem Versionswechsel" nein
            ;;
        gestoppt)
            app_starten
            benachrichtigen hoch "ERP-Update abgebrochen - $KUNDE_NAME" \
                "$was vor dem Versionswechsel. Alte Version wurde wieder gestartet. Log: $log_name"
            exit 1
            ;;
        rollback)
            benachrichtigen dringend "NOTFALL ERP - $KUNDE_NAME" \
                "$was MITTEN im Rollback - das ERP laeuft vermutlich NICHT. Sicherung: sicherungen/$(basename "$SICHERUNG"). Log: $log_name"
            exit 2
            ;;
        fertig)
            log "Update war schon eingespielt, nur das Aufraeumen ist abgebrochen."
            exit 0
            ;;
        *)
            benachrichtigen hoch "ERP-Update abgebrochen - $KUNDE_NAME" \
                "$was, es wurde nichts veraendert. Log: $log_name"
            exit 3
            ;;
    esac
}
trap '' PIPE
trap 'bei_fehler "Skriptfehler in Zeile $LINENO"' ERR
trap 'bei_fehler "Abbruch von aussen (Signal)"' INT TERM HUP

# -----------------------------------------------------------------------------
# Hauptablauf
# -----------------------------------------------------------------------------

# Nie zwei Laeufe gleichzeitig
exec 9>"$STATUS_DIR/lock"
if ! flock -n 9; then
    log "Ein anderes Nachtupdate laeuft gerade - nichts zu tun."
    exit 0
fi

log "=== Nachtupdate gestartet ($KUNDE_NAME) ==="

if [[ -z "$ERP_IMAGE" ]]; then
    log "FEHLER: ERP_IMAGE ist in .env nicht gesetzt."
    exit 3
fi
if ! docker image inspect "$AKTIV_TAG" >/dev/null 2>&1; then
    log "FEHLER: Es ist noch keine Version installiert - zuerst einrichten.sh ausfuehren."
    exit 3
fi

# --- 1. Neue Version holen ---
log "Hole $ERP_IMAGE ..."
if ! docker pull -q "$ERP_IMAGE" >/dev/null; then
    benachrichtigen hoch "ERP-Update: Download fehlgeschlagen - $KUNDE_NAME" \
        "Neue Version konnte nicht geladen werden (Internet? Anmeldung an der Registry abgelaufen?). Es wurde nichts veraendert."
    exit 1
fi

ALT_ID="$(docker image inspect -f '{{.Id}}' "$AKTIV_TAG")"
NEU_ID="$(docker image inspect -f '{{.Id}}' "$ERP_IMAGE")"
ALT_VERSION="$(image_version "$AKTIV_TAG")"
NEU_VERSION="$(image_version "$ERP_IMAGE")"

if [[ "$ALT_ID" == "$NEU_ID" ]]; then
    log "Keine neue Version (aktiv: $ALT_VERSION)."
    aufraeumen
    exit 0
fi
if [[ "$ERZWINGEN" != "ja" && -f "$GESCHEITERT_DATEI" && "$(cat "$GESCHEITERT_DATEI")" == "$NEU_ID" ]]; then
    log "Version $NEU_VERSION ist schon einmal gescheitert - uebersprungen, bis eine neuere kommt (oder --erzwingen)."
    exit 0
fi
log "Neue Version gefunden: $ALT_VERSION -> $NEU_VERSION"

# --- 2. Laeuft die aktuelle Version gesund? Nur dann gibt es einen sicheren Rueckweg ---
if [[ "$ERZWINGEN" != "ja" ]] && ! warte_auf_gesund 120 nein; then
    benachrichtigen hoch "ERP-Update ausgelassen - $KUNDE_NAME" \
        "Das ERP lief schon VOR dem Update nicht sauber ($HEALTH_URL). Ohne gesunde Ausgangslage gibt es keinen sicheren Rollback - bitte pruefen. Mit --erzwingen trotzdem einspielen."
    exit 1
fi

# --- 3. Platz pruefen, App stoppen, Datenbank sichern ---
frei_mb="$(df -Pm "$SICHERUNGS_DIR" | awk 'NR==2 {print $4}')"
if (( frei_mb < MIN_FREI_MB )); then
    benachrichtigen hoch "ERP-Update ausgelassen - $KUNDE_NAME" \
        "Zu wenig Speicherplatz fuer die Sicherung (${frei_mb} MB frei, mindestens ${MIN_FREI_MB} MB noetig). Es wurde nichts veraendert."
    exit 1
fi

log "Stoppe App ..."
PHASE="gestoppt"
"${COMPOSE[@]}" stop app

log "Sichere Datenbank ..."
if ! sicherung_erstellen; then
    log "Sicherung fehlgeschlagen - starte alte Version wieder."
    app_starten
    benachrichtigen hoch "ERP-Update ausgelassen - $KUNDE_NAME" \
        "Datenbank-Sicherung fehlgeschlagen. Ohne Sicherung kein Update - alte Version laeuft weiter. Log: logs/$(basename "$LOG_DATEI")"
    exit 1
fi

# --- 4. Version wechseln ---
log "Starte neue Version $NEU_VERSION ..."
docker tag "$ALT_ID" "$VORHER_TAG"
docker tag "$NEU_ID" "$AKTIV_TAG"
PHASE="gewechselt"
app_starten

# --- 5. Health-Check ---
log "Warte auf $HEALTH_URL (max. $WARTEZEIT s) ..."
if ! warte_auf_gesund "$WARTEZEIT"; then
    zurueckrollen "Neue Version ist nicht sauber hochgefahren"
fi

# --- 6. Geschafft ---
PHASE="fertig"
log "Update erfolgreich: $ALT_VERSION -> $NEU_VERSION"
rm -f "$GESCHEITERT_DATEI"
docker rmi "$GESCHEITERT_TAG" >/dev/null 2>&1 || true
aufraeumen
if [[ "$WEBHOOK_BEI_ERFOLG" == "ja" ]]; then
    benachrichtigen leise "ERP-Update erfolgreich - $KUNDE_NAME" "Version $ALT_VERSION -> $NEU_VERSION"
fi
exit 0
