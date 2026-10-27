#!/usr/bin/env bash
# =============================================================================
# ERP Handwerk - Taegliche Sicherung
# =============================================================================
# Laeuft jede Nacht (Standard 2 Uhr, SICHERUNG_ZEITPLAN) im Updater-Container.
#
#   1. Komplette Datenbank sichern (pg_dump). Das ERP laeuft dabei weiter -
#      pg_dump sieht einen festen Stand.
#   2. Ablage auf diesem Rechner: sicherungen/taeglich-<zeit>.sql.gz
#   3. Kopie auf die externe Festplatte (SICHERUNG_PLATTE) und in den
#      Cloud-Ordner (SICHERUNG_CLOUD, z. B. OneDrive), jeweils:
#        db/       die Datenbank-Sicherungen
#        uploads/  Spiegel der hochgeladenen Dateien - nur Neues und Geaendertes
#                  wird kopiert, geloescht wird dort nie etwas (auch nicht, was
#                  im ERP geloescht wurde)
#   4. Sicherungen, die aelter als SICHERUNG_TAGE sind, loeschen.
#   5. Ergebnis fuer das ERP (betriebsstatus/sicherung.json - Admins sehen
#      Probleme unter "Benachrichtigungen") und ggf. Handy-Nachricht.
#
# Ein Ziel wird nur benutzt, wenn darin die Datei ERP-SICHERUNGSZIEL.txt liegt.
# Fehlt die Festplatte beim Start von Docker, bindet Docker an ihrer Stelle
# einen leeren Ordner ein - ohne diese Pruefung landete die Sicherung
# unbemerkt im Nirgendwo. Die Datei legt "--ziele-einrichten" an.
#
# Aufruf (im Updater: docker compose exec updater bash /erp/sicherung.sh ...):
#   sicherung.sh                     taegliche Sicherung
#   sicherung.sh --ziele-einrichten  Markierung in die eingetragenen Ziele legen
#                                    (Festplatte muss angeschlossen sein)
#   sicherung.sh --status-anlegen    nur "noch keine Sicherung" festhalten, wenn
#                                    es noch kein Ergebnis gibt (Updater-Start)
# Exit-Codes:
#   0  alles gesichert
#   1  nur teilweise gesichert (Ziel fehlt oder ist nicht eingetragen)
#   2  Sicherung fehlgeschlagen
#   3  Voraussetzung fehlt (.env, Einstellung ungueltig ...)
# =============================================================================

set -Eeuo pipefail
# Sicherungen und Logs enthalten Kundendaten: nur fuer root lesbar
umask 077

SKRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SKRIPT_DIR"

# shellcheck source=gemeinsam.sh
source "$SKRIPT_DIR/gemeinsam.sh"
STATUS_BEREICH=sicherung

# So heissen die Ziele im Updater-Container (docker-compose.yml)
ZIEL_PLATTE=/sicherungsziele/platte
ZIEL_CLOUD=/sicherungsziele/cloud
# Hochgeladene Dateien des ERP, nur lesend eingebunden
UPLOADS=/uploads
MARKIERUNG="ERP-SICHERUNGSZIEL.txt"

MODUS=sichern
case "${1-}" in
    --ziele-einrichten) MODUS=ziele-einrichten ;;
    --status-anlegen) MODUS=status-anlegen ;;
    "") ;;
    *) echo "Unbekannte Option: $1" >&2; exit 3 ;;
esac

ZEITSTEMPEL="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$SICHERUNGS_DIR" "$LOG_DIR" "$STATUS_DIR"

if [[ "$MODUS" == "status-anlegen" ]]; then
    # Beim Start des Updaters: Gibt es noch gar kein Ergebnis, faengt ab jetzt
    # die Uhr an - laeuft binnen eines Tages keine Sicherung, meldet das ERP es.
    [[ -f "$ENV_DATEI" ]] && einstellungen_lesen
    if [[ ! -f "$BETRIEBSSTATUS_DIR/$STATUS_BEREICH.json" ]]; then
        status_schreiben NEU "Sicherung eingerichtet" "Noch keine Sicherung gelaufen."
    fi
    exit 0
fi

LOG_DATEI="$LOG_DIR/sicherung-$ZEITSTEMPEL.log"
exec > >(tee -a "$LOG_DATEI") 2>&1

if [[ ! -f "$ENV_DATEI" ]]; then
    log "FEHLER: $ENV_DATEI fehlt - zuerst einrichten."
    exit 3
fi
einstellungen_lesen
LOCK_DATEI="${LOCK_DATEI:-$STATUS_DIR/lock}"
SICHERUNG_TAGE="$(env_wert SICHERUNG_TAGE 30)"
zahl_pruefen SICHERUNG_TAGE "$SICHERUNG_TAGE" 1
SICHERUNG_TAGE=$((10#$SICHERUNG_TAGE))

# Name eines Ziels in den Meldungen
ziel_name() {
    if [[ "$1" == "$ZIEL_PLATTE" ]]; then echo "Externe Festplatte"; else echo "Cloud-Ordner"; fi
}

# Zustand eines Ziels:
#   aus     nicht in der .env eingetragen
#   neu     in der .env eingetragen, aber eingebunden ist noch der Stand vom
#           Erstellen des Updaters (sonst landete die Sicherung im leeren
#           Platzhalter) - "docker compose up -d updater" fehlt
#   bereit  eingetragen und genau so eingebunden
ziel_zustand() {
    local eingetragen eingebunden
    if [[ "$1" == "$ZIEL_PLATTE" ]]; then
        eingetragen="$(env_wert SICHERUNG_PLATTE)"
        eingebunden="${SICHERUNG_PLATTE_EINGEBUNDEN-}"
    else
        eingetragen="$(env_wert SICHERUNG_CLOUD)"
        eingebunden="${SICHERUNG_CLOUD_EINGEBUNDEN-}"
    fi
    if [[ -z "$eingetragen" ]]; then
        echo aus
    elif [[ "$eingetragen" != "$eingebunden" ]]; then
        echo neu
    else
        echo bereit
    fi
}
# Laeuft auf diesem Rechner der Docker-Waechter (Windows, docker-waechter.ps1)?
# Er frischt bei jedem Lauf (alle 5 Minuten) diese Datei auf und bindet
# abgerissene Sicherungsziele vor der naechsten Sicherung selbst wieder ein.
waechter_aktiv() {
    [[ -n "$(find "$LOG_DIR/docker-waechter-lebt.txt" -mmin -30 2>/dev/null)" ]]
}
NICHT_EINGEBUNDEN_HINWEIS="ist in der .env eingetragen, aber noch nicht eingebunden. Bitte im ERP-Ordner einmal ausführen: docker compose up -d updater"
# Nach einem Umzug der Uploads (dateifreigabe.sh --umziehen) haengt bis zum
# naechsten "docker compose up -d" noch der alte Ordner im Updater. Aus dem
# spiegelte die Sicherung sonst weiter - und neue Dateien fehlten darin.
uploads_eingebunden() {
    # Ein Updater von vor dieser Pruefung kennt die Variable nicht: nichts zu vergleichen
    [[ -z "${UPLOADS_ORDNER_EINGEBUNDEN+x}" ]] && return 0
    [[ "$(env_wert UPLOADS_ORDNER)" == "$UPLOADS_ORDNER_EINGEBUNDEN" ]]
}
UPLOADS_UMGEZOGEN_HINWEIS="Die hochgeladenen Dateien sind umgezogen, der Updater sichert aber noch den alten Ordner. Bitte im ERP-Ordner einmal ausführen: docker compose up -d"

# -----------------------------------------------------------------------------
# Ziele einrichten
# -----------------------------------------------------------------------------

if [[ "$MODUS" == "ziele-einrichten" ]]; then
    eingerichtet=0
    for ziel in "$ZIEL_PLATTE" "$ZIEL_CLOUD"; do
        case "$(ziel_zustand "$ziel")" in
            aus)
                log "$(ziel_name "$ziel"): nicht in .env eingetragen - uebersprungen."
                continue ;;
            neu)
                log "$(ziel_name "$ziel") $NICHT_EINGEBUNDEN_HINWEIS"
                continue ;;
        esac
        # Auch eine tote Einbindung (siehe ziel_sichern) ist hier "kein Ordner"
        if [[ ! -d "$ziel" ]] || ! ls -A "$ziel/" >/dev/null 2>&1; then
            log "$(ziel_name "$ziel"): $ziel ist im Updater nicht lesbar - Docker Desktop (bzw. den Rechner) neu starten, danach erneut versuchen."
            continue
        fi
        printf '%s\r\n' \
            "Dieser Ordner ist ein Sicherungsziel des ERP (taegliche Sicherung)." \
            "Bitte nicht loeschen: Ohne diese Datei sichert das ERP hier nicht mehr." \
            "Eingerichtet am $(date '+%d.%m.%Y um %H:%M Uhr')." > "$ziel/$MARKIERUNG"
        log "$(ziel_name "$ziel"): eingerichtet."
        eingerichtet=$((eingerichtet + 1))
    done
    [[ $eingerichtet -gt 0 ]] || exit 1
    exit 0
fi

# -----------------------------------------------------------------------------
# Bausteine
# -----------------------------------------------------------------------------

# Kopiert die Datenbank-Sicherung nach <ziel>/db/ und spiegelt die Uploads nach
# <ziel>/uploads/. Laeuft in $(...): Auf stdout steht NUR das Problem fuer die
# Meldung - alles fuers Log geht nach stderr (landet trotzdem im Log).
# ziel_sichern <Ziel> <Sicherungsdatei>
ziel_sichern() {
    local ziel="$1" datei="$2" name dateiname code=0
    name="$(ziel_name "$ziel")"
    if [[ "$(ziel_zustand "$ziel")" == neu ]]; then
        echo "$name $NICHT_EINGEBUNDEN_HINWEIS"
        return 1
    fi
    # Tote Einbindung: Das Ziel war schon eingebunden und ist zwischendurch
    # kurz verschwunden (Windows schaltet eine USB-Platte beim Energiesparen
    # ab, OneDrive startet neu ...). Unter Windows ist es danach wieder da,
    # Docker haelt aber an der alten Verbindung fest: Jeder Zugriff endet mit
    # "No such device". Anders als ein leerer Platzhalter laesst sich der
    # Ordner gar nicht mehr lesen.
    if ! ls -A "$ziel/" >/dev/null 2>&1; then
        if waechter_aktiv; then
            echo "$name: Die Verbindung ist abgerissen (z. B. hat Windows die Platte beim Energiesparen kurz abgeschaltet). Der Docker-Wächter stellt sie vor der nächsten Sicherung von selbst wieder her. Sofort beheben: Docker Desktop neu starten."
        else
            echo "$name: Die Verbindung ist abgerissen (z. B. kurz abgezogen oder beim Energiesparen abgeschaltet). Bitte den Rechner neu starten."
        fi
        # shellcheck disable=SC2012  # nur die Fehlermeldung von ls zaehlt
        log "(Docker kommt nicht mehr an $ziel: $(ls -A "$ziel/" 2>&1 >/dev/null | head -n 1 || true))" >&2
        return 1
    fi
    if [[ ! -f "$ziel/$MARKIERUNG" ]]; then
        if [[ "$ziel" == "$ZIEL_PLATTE" ]]; then
            if waechter_aktiv; then
                echo "Externe Festplatte nicht gefunden. Bitte anschließen – der Docker-Wächter bindet sie vor der nächsten Sicherung von selbst ein."
            else
                echo "Externe Festplatte nicht gefunden. Bitte anschließen und den Rechner neu starten."
            fi
        else
            echo "Cloud-Ordner nicht gefunden. Gibt es den Ordner noch? Danach den Rechner neu starten."
        fi
        log "(Ist das Ziel da und fehlt nur $MARKIERUNG: einmal sicherung.sh --ziele-einrichten ausfuehren.)" >&2
        return 1
    fi
    # Gleicher Name wie bei der frueheren Windows-Sicherung - so laeuft die
    # Aufbewahrung ueber alte und neue Dateien gleich.
    dateiname="kalkulationsprogramm_db_${ZEITSTEMPEL/-/_}.sql.gz"
    if ! mkdir -p "$ziel/db" \
        || ! cp "$datei" "$ziel/db/$dateiname.unfertig" \
        || [[ "$(stat -c %s "$ziel/db/$dateiname.unfertig")" != "$(stat -c %s "$datei")" ]] \
        || ! mv -f "$ziel/db/$dateiname.unfertig" "$ziel/db/$dateiname"; then
        rm -f "$ziel/db/$dateiname.unfertig" 2>/dev/null || true
        echo "$name: Datenbank-Sicherung konnte nicht kopiert werden (voll oder schreibgeschützt?)."
        return 1
    fi
    log "$name: Datenbank-Sicherung kopiert ($dateiname)." >&2

    if ! uploads_eingebunden; then
        echo "$name: $UPLOADS_UMGEZOGEN_HINWEIS"
        return 1
    fi
    if [[ -d "$UPLOADS" ]]; then
        # -rt: Ordner und Zeitstempel, keine Rechte/Besitzer (FAT32, OneDrive).
        # --modify-window=2: FAT speichert Zeiten nur auf 2 Sekunden genau -
        # sonst kopierte rsync jede Nacht alles neu. Ohne --delete.
        mkdir -p "$ziel/uploads" && rsync -rt --modify-window=2 "$UPLOADS/" "$ziel/uploads/" >&2 || code=$?
        # 24 = Datei waehrend des Kopierens verschwunden (im ERP geloescht) - harmlos
        if [[ $code -ne 0 && $code -ne 24 ]]; then
            echo "$name: Hochgeladene Dateien nur teilweise kopiert (Fehler $code, Einzelheiten im Log)."
            return 1
        fi
        log "$name: hochgeladene Dateien abgeglichen." >&2
    fi

    # Alte Sicherungen loeschen - auch die der frueheren Windows-Sicherung, die
    # im Cloud-Ordner direkt im Hauptordner lagen. Dazu Reste hart
    # abgebrochener Kopien.
    find "$ziel/db" "$ziel" -maxdepth 1 -type f -name 'kalkulationsprogramm_db_*.sql.gz' \
        -mtime +"$SICHERUNG_TAGE" -delete 2>/dev/null || true
    find "$ziel/db" -maxdepth 1 -type f -name 'kalkulationsprogramm_db_*.unfertig' \
        -mmin +60 -delete 2>/dev/null || true
    return 0
}

# Fehlerfall: Ergebnis festhalten, melden, mit Exit-Code beenden
abbrechen() {  # abbrechen <Text>
    melden FEHLER "ERP-Sicherung fehlgeschlagen - $KUNDE_NAME" \
        "$1
Log auf dem Server: logs/$(basename "$LOG_DATEI")"
    exit 2
}

# shellcheck disable=SC2329  # wird ueber die Traps aufgerufen
bei_fehler() {
    trap - ERR INT TERM HUP
    set +e
    exec >>"$LOG_DATEI" 2>&1
    abbrechen "$1 - die Sicherung wurde nicht fertig."
}
trap '' PIPE
trap 'bei_fehler "Skriptfehler in Zeile $LINENO"' ERR
trap 'bei_fehler "Abbruch von außen (Signal)"' INT TERM HUP

# -----------------------------------------------------------------------------
# Hauptablauf
# -----------------------------------------------------------------------------

log "=== Taegliche Sicherung gestartet ($KUNDE_NAME) ==="

if ! command -v rsync >/dev/null 2>&1; then
    abbrechen "Der Updater ist veraltet (rsync fehlt). Bitte im ERP-Ordner einmal ausführen: docker compose build updater && docker compose up -d updater"
fi
frei="$(frei_mb)"
if zu_wenig_platz "$frei"; then
    abbrechen "Zu wenig Speicherplatz auf diesem Rechner (${frei} MB frei, mindestens ${MIN_FREI_MB} MB nötig)."
fi

# --- 1./2. Datenbank sichern ---
# Nicht gleichzeitig mit einem Nachtupdate: das stoppt die App und spielt bei
# einem Rollback die Datenbank zurueck. Die Sperre gilt nur fuer pg_dump - das
# Kopieren danach darf neben einem Update laufen.
exec 9>"$LOCK_DATEI"
if ! flock -w 7200 9; then
    abbrechen "Ein Nachtupdate lief über zwei Stunden - die Sicherung wurde ausgelassen."
fi
SICHERUNG="$SICHERUNGS_DIR/taeglich-$ZEITSTEMPEL.sql.gz"
log "Sichere Datenbank ..."
if ! datenbank_sichern "$SICHERUNG"; then
    flock -u 9
    abbrechen "Die Datenbank konnte nicht gesichert werden. Läuft der Datenbank-Container (docker compose ps)?"
fi
flock -u 9
find "$SICHERUNGS_DIR" -maxdepth 1 -type f -name 'taeglich-*.sql.gz' -mtime +"$SICHERUNG_TAGE" -delete 2>/dev/null || true
find "$SICHERUNGS_DIR" -maxdepth 1 -name 'taeglich-*.unfertig' -mmin +60 -delete 2>/dev/null || true

# --- 3./4. Kopien auf Festplatte und in den Cloud-Ordner ---
gesichert=("auf diesem Rechner")
probleme=()
for ziel in "$ZIEL_PLATTE" "$ZIEL_CLOUD"; do
    [[ "$(ziel_zustand "$ziel")" != aus ]] || continue
    if problem="$(ziel_sichern "$ziel" "$SICHERUNG")"; then
        if [[ "$ziel" == "$ZIEL_PLATTE" ]]; then
            gesichert+=("auf der externen Festplatte")
        else
            gesichert+=("im Cloud-Ordner")
        fi
    else
        log "$problem"
        probleme+=("$problem")
    fi
done

# --- 5. Ergebnis ---
# "Gesichert auf diesem Rechner, auf der externen Festplatte und im Cloud-Ordner."
zusammenfassung="Gesichert ${gesichert[0]}"
for (( i = 1; i < ${#gesichert[@]}; i++ )); do
    if (( i == ${#gesichert[@]} - 1 )); then
        zusammenfassung+=" und ${gesichert[i]}"
    else
        zusammenfassung+=", ${gesichert[i]}"
    fi
done
zusammenfassung+="."
if [[ ${#gesichert[@]} -eq 1 && ${#probleme[@]} -eq 0 ]]; then
    melden TEILWEISE "ERP-Sicherung nur auf diesem Rechner - $KUNDE_NAME" \
        "Es ist weder eine externe Festplatte noch ein Cloud-Ordner eingetragen. Fällt die Festplatte dieses Rechners aus, sind die Daten weg. Bitte SICHERUNG_PLATTE oder SICHERUNG_CLOUD in der .env eintragen (README, Abschnitt Tägliche Sicherung)."
    exit 1
fi
if [[ ${#probleme[@]} -gt 0 ]]; then
    melden TEILWEISE "ERP-Sicherung nur teilweise - $KUNDE_NAME" \
        "$(printf '%s\n' "${probleme[@]}")"$'\n'"$zusammenfassung"
    exit 1
fi
log "$zusammenfassung"
melden OK "ERP-Sicherung erfolgreich - $KUNDE_NAME" "$zusammenfassung"
exit 0
