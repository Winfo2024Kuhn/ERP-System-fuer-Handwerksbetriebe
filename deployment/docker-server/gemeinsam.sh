#!/usr/bin/env bash
# =============================================================================
# ERP Handwerk - Gemeinsame Bausteine fuer nachtupdate.sh und sicherung.sh
# =============================================================================
# Wird mit "source" eingebunden, nachdem das Skript SKRIPT_DIR gesetzt hat.
# Legt nur Pfade und Funktionen an - ausgefuehrt wird hier nichts.
#
# Das einbindende Skript setzt vor dem ersten "melden":
#   STATUS_BEREICH  Name der Statusdatei fuer das ERP (nachtupdate | sicherung)
# und ruft nach dem Pruefen der .env einmal "einstellungen_lesen" auf.
# =============================================================================

# SC2016: Die $POSTGRES_*-Variablen in den sh -c '...'-Aufrufen sollen bewusst
#         erst IM Datenbank-Container aufgeloest werden - das Passwort verlaesst
#         ihn nie.
# SC2034: Die Variablen hier benutzen die einbindenden Skripte.
# shellcheck disable=SC2016,SC2034

ENV_DATEI="$SKRIPT_DIR/.env"
SICHERUNGS_DIR="$SKRIPT_DIR/sicherungen"
LOG_DIR="$SKRIPT_DIR/logs"
STATUS_DIR="$SKRIPT_DIR/.status"
# Ergebnis der letzten Laeufe fuer die Benachrichtigungen im ERP. Im Updater
# das Volume "betriebsstatus", das die App nur lesend einbindet
# (docker-compose.yml). Die Dateien darin enthalten keine Kundendaten und sind
# deshalb fuer alle lesbar - die App laeuft als eigener Benutzer.
BETRIEBSSTATUS_DIR="${BETRIEBSSTATUS_DIR:-$SKRIPT_DIR/betriebsstatus}"
COMPOSE=(docker compose --project-directory "$SKRIPT_DIR" -f "$SKRIPT_DIR/docker-compose.yml")
# Mindestens so viel freier Platz (in MB) muss fuer eine Sicherung da sein
MIN_FREI_MB=1024

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
    # Anfuehrungszeichen wie Docker Compose weglassen - sonst saehe z. B.
    # SICHERUNG_PLATTE='...' fuer sicherung.sh anders aus als fuer Compose
    wert="${wert%\"}"
    wert="${wert#\"}"
    wert="${wert%\'}"
    wert="${wert#\'}"
    if [[ -z "$zeile" || -z "$wert" ]]; then
        wert="${2-}"
    fi
    printf '%s' "$wert"
}

# Zahlen pruefen, BEVOR irgendetwas veraendert wird: ein Tippfehler wie
# "300s" wuerde sonst mitten im Lauf die Shell-Arithmetik sprengen.
zahl_pruefen() {  # zahl_pruefen <Name> <Wert> <Minimum>
    if [[ ! "$2" =~ ^[0-9]+$ ]] || (( 10#$2 < $3 )); then
        log "FEHLER: $1=$2 in .env ist ungueltig (ganze Zahl >= $3 erwartet). Nichts veraendert."
        exit 3
    fi
}

# Einstellungen, die beide Skripte brauchen (Handy-Nachricht, Betriebsname).
einstellungen_lesen() {
    KUNDE_NAME="$(env_wert KUNDE_NAME "$(hostname)")"
    WEBHOOK_URL="$(env_wert WEBHOOK_URL)"
    WEBHOOK_TOKEN="$(env_wert WEBHOOK_TOKEN)"
    WEBHOOK_FORMAT="$(env_wert WEBHOOK_FORMAT ntfy)"
    WEBHOOK_BEI_ERFOLG="$(env_wert WEBHOOK_BEI_ERFOLG nein)"
    WEBHOOK_MIT_FEHLERAUSZUG="$(env_wert WEBHOOK_MIT_FEHLERAUSZUG nein)"
    if [[ -n "$WEBHOOK_URL" && ! "$WEBHOOK_URL" =~ ^https:// && ! "$WEBHOOK_URL" =~ ^http://(127\.0\.0\.1|localhost)[:/] ]]; then
        log "WARNUNG: WEBHOOK_URL ist nicht https - Nachrichten gehen unverschluesselt raus."
    fi
}

# -----------------------------------------------------------------------------
# Melden: Handy-Nachricht und Benachrichtigungen im ERP
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
    # er nicht in der Prozessliste steht. Eigener Name je Lauf: Sicherung und
    # Nachtupdate koennen gleichzeitig melden.
    local kopf_datei="$STATUS_DIR/webhook-kopf-$$"
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

# Haelt das Ergebnis fuer die Benachrichtigungen im ERP fest
# (betriebsstatus/<STATUS_BEREICH>.json). Wird ueber eine Zwischendatei
# geschrieben, damit die App nie eine halbe Datei liest. Scheitert das
# Schreiben, laeuft das Skript trotzdem weiter.
# status_schreiben <OK|NEU|TEILWEISE|FEHLER|NOTFALL> <Titel> <Text>
status_schreiben() {
    local ergebnis="$1" titel="$2" text="$3" datei neu jetzt letzter_erfolg
    [[ -n "${STATUS_BEREICH-}" ]] || return 0
    datei="$BETRIEBSSTATUS_DIR/$STATUS_BEREICH.json"
    # Eigene Zwischendatei je Lauf: Cron und ein Aufruf von Hand koennen gleichzeitig schreiben
    neu="$datei.$$.neu"
    jetzt="$(date -Iseconds)"
    if [[ "$ergebnis" == "OK" ]]; then
        letzter_erfolg="$jetzt"
    else
        letzter_erfolg="$(sed -n 's/.*"letzterErfolg": *"\([^"]*\)".*/\1/p' "$datei" 2>/dev/null | head -n 1 || true)"
    fi
    # Der Betriebsname steht fuer die Handy-Nachricht in jedem Titel - im ERP
    # selbst ist er ueberfluessig.
    titel="${titel% - "${KUNDE_NAME-}"}"
    # chmod darf scheitern (z. B. auf Windows-Ordnern) - die Datei soll trotzdem kommen
    if mkdir -p "$BETRIEBSSTATUS_DIR" \
        && { chmod 755 "$BETRIEBSSTATUS_DIR" 2>/dev/null || true; } \
        && printf '{"ergebnis": %s, "zeitpunkt": %s, "titel": %s, "text": %s, "letzterErfolg": %s}\n' \
            "$(json_string "$ergebnis")" "$(json_string "$jetzt")" "$(json_string "$titel")" \
            "$(json_string "$text")" "$(json_string "$letzter_erfolg")" > "$neu" \
        && { chmod 644 "$neu" 2>/dev/null || true; } \
        && mv -f "$neu" "$datei"; then
        return 0
    fi
    rm -f "$neu" 2>/dev/null || true
    log "WARNUNG: Ergebnis fuer das ERP konnte nicht nach $datei geschrieben werden."
    return 0
}

# Ergebnis festhalten (ERP) und per Handy melden.
# melden <OK|TEILWEISE|FEHLER|NOTFALL> <Titel> <Text> [Zusatz nur fuers Handy]
#   OK        Handy-Nachricht nur mit WEBHOOK_BEI_ERFOLG=ja
#   TEILWEISE, FEHLER  Handy-Nachricht mit Warnung
#   NOTFALL   dringende Handy-Nachricht
# Der Zusatz (z. B. Fehlerzeilen aus dem App-Log) geht nie ins ERP: Die
# Statusdatei ist fuer alle lesbar und darf keine Kundendaten enthalten.
melden() {
    local ergebnis="$1" titel="$2" text="$3" handy="$3"
    [[ -n "${4-}" ]] && handy+=$'\n'"$4"
    status_schreiben "$ergebnis" "$titel" "$text"
    case "$ergebnis" in
        OK) [[ "$WEBHOOK_BEI_ERFOLG" == "ja" ]] && benachrichtigen leise "$titel" "$handy" ;;
        NOTFALL) benachrichtigen dringend "$titel" "$handy" ;;
        *) benachrichtigen hoch "$titel" "$handy" ;;
    esac
    return 0
}

# -----------------------------------------------------------------------------
# Datenbank
# -----------------------------------------------------------------------------

# Schreibt die komplette Datenbank als SQL auf stdout. Das Passwort bleibt im
# Datenbank-Container (dort liegt es schon als Umgebungsvariable) und taucht
# in keiner Prozessliste auf. Haelt jemand eine Sperre, gibt pg_dump nach
# 2 Minuten auf statt ewig zu warten. pg_dump sieht einen festen Stand - das
# ERP darf dabei weiterlaufen.
datenbank_ausgeben() {
    "${COMPOSE[@]}" exec -T postgres sh -c \
        'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-privileges --lock-wait-timeout=120s'
}

# pg_dump schreibt zum Schluss eine feste Abschlusszeile (danach noch ein paar
# Zeilen wie "\unrestrict ..."). Fehlt sie, ist die Sicherung abgeschnitten
# und taugt nicht zum Zurueckspielen.
# Das Ende erst ganz einlesen, dann pruefen: Ein "| grep -q" hoert nach dem
# ersten Treffer auf zu lesen. Schickt tail die Zeilen in zwei Stuecken, gibt
# das "Broken pipe" - und pipefail meldet eine gute Sicherung als kaputt.
sicherung_vollstaendig() {
    local ende
    ende="$(gzip -cd "$1" | tail -n 20)" || return 1
    [[ "$ende" == *"PostgreSQL database dump complete"* ]]
}

# Komplette Sicherung der Datenbank nach <Zieldatei> (.sql.gz). Erst eine
# Zwischendatei, die nur umbenannt wird, wenn sie vollstaendig ist.
# datenbank_sichern <Zieldatei>
datenbank_sichern() {
    local ziel="$1" unfertig="$1.unfertig"
    if ! datenbank_ausgeben | gzip > "$unfertig"; then
        rm -f "$unfertig"
        return 1
    fi
    if ! sicherung_vollstaendig "$unfertig"; then
        log "Sicherung ist unvollstaendig."
        rm -f "$unfertig"
        return 1
    fi
    # Laeuft meist in einer if-Bedingung (dort greift set -e nicht) - also selbst pruefen
    if ! mv "$unfertig" "$ziel"; then
        rm -f "$unfertig"
        return 1
    fi
    log "Sicherung: $ziel ($(du -h "$ziel" | cut -f1))"
}

# Freier Platz (MB) im Ordner fuer die Sicherungen; leer, wenn unbekannt.
# Darf nie scheitern: Es laeuft in $(...), ein Fehler loeste dort den
# ERR-Trap ein zweites Mal aus.
frei_mb() {
    df -Pm "$SICHERUNGS_DIR" 2>/dev/null | awk 'NR==2 {print $4}' || true
}

# Ist zu wenig Platz fuer eine Sicherung? (unbekannt = nein, die Sicherung
# selbst scheitert dann mit einer eigenen Meldung)
# zu_wenig_platz <frei_mb>
zu_wenig_platz() {
    [[ "$1" =~ ^[0-9]+$ ]] && (( 10#$1 < MIN_FREI_MB ))
}
