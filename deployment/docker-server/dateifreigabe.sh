#!/usr/bin/env bash
# =============================================================================
# ERP Handwerk - Ordner-Freigabe einschalten (und Uploads ins Volume umziehen)
# =============================================================================
# Aufruf im ERP-Ordner (laeuft im Updater, der Docker steuern darf):
#
#   docker compose exec updater bash /erp/dateifreigabe.sh
#       Schaltet die Ordner-Freigabe ein (Dienst "dateifreigabe"), baut und
#       startet sie und zeigt beim ersten Mal den Tailscale-Anmelde-Link.
#       Mehrfach aufrufen schadet nicht.
#
#   docker compose exec updater bash /erp/dateifreigabe.sh --umziehen
#       Vorher: Liegen die Uploads noch in einem Ordner auf dem Rechner
#       (UPLOADS_ORDNER in .env, z. B. C:\Kalkulationsprogramm\uploads), kopiert
#       es sie ins Docker-Volume app_uploads, prueft die Kopie, stellt die .env
#       um und startet das ERP damit neu. Der alte Ordner bleibt unveraendert
#       als Reserve liegen - geloescht wird nichts.
#
# Danach einmal auf dem Rechner selbst (nicht im Updater):  docker compose up -d
# Das bindet die Uploads auch im Updater neu ein (taegliche Sicherung).
# Erklaerung: README, Abschnitt "Ordner-Freigabe ohne Windows".
# =============================================================================
set -Eeuo pipefail
umask 077

SKRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SKRIPT_DIR"
source "$SKRIPT_DIR/gemeinsam.sh"

UMZIEHEN=0
case "${1-}" in
    --umziehen) UMZIEHEN=1 ;;
    "") ;;
    *) echo "Aufruf: dateifreigabe.sh [--umziehen]" >&2; exit 2 ;;
esac

# Name aus "name:" in docker-compose.yml - so heissen auch die Volumes
PROJEKT=erp-handwerk
VOLUME="${PROJEKT}_app_uploads"
CONTAINER=erp-dateifreigabe
UPDATER_IMAGE=erp-updater:lokal
WARTEN_SEKUNDEN=900
# Endung fuer Dateien im Volume, die der Umzug durch gleichnamige ersetzt
SICHERUNGSENDUNG=".vor-umzug-$(date '+%Y%m%d')"

fehler() { log "FEHLER: $*"; exit 1; }

[[ -f "$ENV_DATEI" ]] || fehler ".env fehlt - zuerst einrichten (Einrichten.cmd bzw. einrichten.sh)."
docker info >/dev/null 2>&1 || fehler "Docker ist nicht erreichbar. Laeuft das Skript im Updater (docker compose exec updater ...)?"

# Setzt KEY=WERT in der .env: ersetzt die letzte vorhandene Zeile oder haengt an.
# Windows-Zeilenenden (CRLF) bleiben, wie sie sind. Vor der ersten Aenderung eines
# Laufs legt es eine Kopie daneben (.env.vor-dateifreigabe-<Zeitpunkt>).
ENV_GESICHERT=""
env_setzen() {  # env_setzen KEY WERT [Kommentar]
    local schluessel="$1" wert="$2" kommentar="${3-}" neu
    if [[ -z "$ENV_GESICHERT" ]]; then
        ENV_GESICHERT="$ENV_DATEI.vor-dateifreigabe-$(date '+%Y%m%d-%H%M%S')"
        cp -p "$ENV_DATEI" "$ENV_GESICHERT"
        log "Kopie der .env: $(basename "$ENV_GESICHERT")"
    fi
    neu="$(mktemp "$ENV_DATEI.XXXXXX")"
    awk -v k="$schluessel" -v w="$wert" -v c="$kommentar" '
        { cr = sub(/\r$/, "") ? "\r" : ""; zeilen[NR] = $0; ende[NR] = cr
          if (cr != "") crlf = "\r"
          if (index($0, k "=") == 1) letzte = NR }
        END {
            for (i = 1; i <= NR; i++) {
                if (i == letzte) {
                    if (c != "") print "# " c ende[i]
                    print k "=" w ende[i]
                } else print zeilen[i] ende[i]
            }
            if (!letzte) { if (c != "") print "# " c crlf; print k "=" w crlf }
        }' "$ENV_DATEI" > "$neu"
    # Nur ueberschreiben, wenn die neue Fassung vollstaendig geschrieben ist
    [[ -s "$neu" ]] || { rm -f "$neu"; fehler ".env konnte nicht geschrieben werden (Platte voll?). Nichts geaendert."; }
    cat "$neu" > "$ENV_DATEI"
    rm -f "$neu"
}

# --- 1. Uploads umziehen (nur mit --umziehen) ---------------------------------
alt="$(env_wert UPLOADS_ORDNER)"
if [[ $UMZIEHEN -eq 1 ]]; then
    if [[ -z "$alt" ]]; then
        log "UPLOADS_ORDNER ist leer - die Uploads liegen schon im Volume. Nichts umzuziehen."
    else
        [[ "$alt" == /* ]] || fehler "UPLOADS_ORDNER='$alt' ist kein Pfad, wie Docker ihn sieht (unter Windows z. B. /run/desktop/mnt/host/c/Kalkulationsprogramm/uploads). Nichts veraendert."
        log "Ziehe die Uploads um: $alt -> Docker-Volume $VOLUME"
        log "Wichtig: Vorher alle Excel-Dateien und Zeichnungen schliessen und eine Windows-Freigabe"
        log "des alten Ordners entfernen - was danach dort gespeichert wird, kommt nicht mit."
        docker image inspect "$UPDATER_IMAGE" >/dev/null 2>&1 \
            || fehler "Image $UPDATER_IMAGE fehlt (docker compose build updater)."
        # rsync in einem Hilfscontainer mit beiden Ordnern: der alte nur lesend
        kopierer() {
            docker run --rm --network none -v "$alt:/alt:ro" -v "$VOLUME:/neu" \
                --entrypoint rsync "$UPDATER_IMAGE" "$@"
        }
        if ! docker run --rm --network none -v "$alt:/alt:ro" --entrypoint sh "$UPDATER_IMAGE" \
                -c 'ls -A /alt | grep -q .'; then
            fehler "Der alte Ordner $alt ist leer oder nicht lesbar. Nichts veraendert."
        fi
        # Volume so anlegen, wie es Docker Compose selbst taete (sonst warnt compose)
        if ! docker volume inspect "$VOLUME" >/dev/null 2>&1; then
            docker volume create --label "com.docker.compose.project=$PROJEKT" \
                --label com.docker.compose.volume=app_uploads "$VOLUME" >/dev/null
        fi
        if docker run --rm --network none -v "$VOLUME:/neu:ro" --entrypoint sh "$UPDATER_IMAGE" \
                -c 'find /neu -type f | grep -q .'; then
            log "Hinweis: Im Volume liegen schon Dateien. Sie bleiben; ersetzt rsync eine gleichnamige,"
            log "bleibt die bisherige daneben als <Name>$SICHERUNGSENDUNG liegen."
        fi
        # Platz: Die Kopie liegt auf demselben Datentraeger wie die Datenbank
        platz="$(docker run --rm --network none -v "$alt:/alt:ro" -v "$VOLUME:/neu:ro" --entrypoint sh \
            "$UPDATER_IMAGE" -c 'echo "$(du -sb /alt | cut -f1) $(df -B1 --output=avail /neu | tail -n 1)"')"
        read -r bedarf frei <<< "$platz"
        if [[ ! "$bedarf" =~ ^[0-9]+$ || ! "$frei" =~ ^[0-9]+$ ]] \
                || (( frei < bedarf + MIN_FREI_MB * 1024 * 1024 )); then
            fehler "Zu wenig Platz fuer die Kopie (noetig: $bedarf Bytes + $MIN_FREI_MB MB Reserve, frei: $frei Bytes). Nichts veraendert."
        fi

        # Bei jedem Abbruch ab hier (auch durch set -e): alles wieder wie vorher -
        # alter Ordner, nichts umgestellt. Die Freigabe nur, wenn sie vorher lief.
        freigabe_lief="$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null || true)"
        zurueck() {
            "${COMPOSE[@]}" start app || true
            if [[ "$freigabe_lief" == true ]]; then
                docker start "$CONTAINER" >/dev/null 2>&1 || true
            fi
        }
        trap zurueck EXIT
        log "ERP und Ordner-Freigabe anhalten, damit waehrend des Kopierens niemand etwas speichert ..."
        docker stop "$CONTAINER" >/dev/null 2>&1 || true
        "${COMPOSE[@]}" stop app
        # -rlt: Ordner, Links, Zeitstempel. Besitzer = Benutzer des ERP (10001).
        # --modify-window=2: Windows-Ordner speichern Zeiten ungenauer.
        log "Kopiere (je nach Menge einige Minuten) ..."
        if ! kopierer -rlt --modify-window=2 --chown=10001:10001 --backup --suffix="$SICHERUNGSENDUNG" \
                /alt/ /neu/; then
            fehler "Kopieren fehlgeschlagen. Das ERP laeuft wieder mit dem alten Ordner, nichts umgestellt."
        fi

        # Pruefen: gleiche Anzahl Dateien, gleiche Groesse, und rsync findet
        # nichts mehr zu kopieren
        zaehle='printf "%s %s\n" "$(find "$1" -type f | wc -l)" "$(find "$1" -type f -printf "%s\n" | awk "{s+=\$1} END {print s+0}")"'
        vorher="$(docker run --rm --network none -v "$alt:/alt:ro" --entrypoint sh "$UPDATER_IMAGE" -c "$zaehle" _ /alt)"
        nachher="$(docker run --rm --network none -v "$VOLUME:/neu:ro" --entrypoint sh "$UPDATER_IMAGE" -c "$zaehle" _ /neu)"
        if ! pruefung="$(kopierer -rltn --modify-window=2 --out-format='%n' /alt/ /neu/)"; then
            fehler "Die Kopie liess sich nicht pruefen. Das ERP laeuft wieder mit dem alten Ordner, nichts umgestellt."
        fi
        offen="$(printf '%s\n' "$pruefung" | grep -v -e '/$' -e '^$' | head -n 5 || true)"
        read -r anzahl_alt bytes_alt <<< "$vorher"
        read -r anzahl_neu bytes_neu <<< "$nachher"
        log "Alter Ordner: $anzahl_alt Dateien, $bytes_alt Bytes. Volume: $anzahl_neu Dateien, $bytes_neu Bytes."
        if [[ -n "$offen" ]] || (( anzahl_neu < anzahl_alt )) || (( bytes_neu < bytes_alt )); then
            fehler "Die Kopie ist nicht vollstaendig (z. B. $offen). Das ERP laeuft wieder mit dem alten Ordner, nichts umgestellt."
        fi

        env_setzen UPLOADS_ORDNER "" \
            "Umgezogen am $(date '+%d.%m.%Y') ins Docker-Volume app_uploads. Alter Ordner (Reserve, nicht mehr benutzt): $alt"
        log ".env umgestellt: UPLOADS_ORDNER ist jetzt leer (= Docker-Volume)."
        # Neu erstellen statt nur starten: Erst so haengt das Volume im ERP. Die
        # Version bleibt dieselbe (lokales Image, kein Pull).
        "${COMPOSE[@]}" up -d --no-deps app
        trap - EXIT
        log "ERP laeuft wieder - jetzt mit den Dateien aus dem Volume."
        alt=""
    fi
elif [[ -n "$alt" ]]; then
    log "Hinweis: Die Uploads liegen noch in $alt. Die Freigabe funktioniert so, schneller und"
    log "mit Mac-Finder-Infos aber im Volume: dateifreigabe.sh --umziehen (der alte Ordner bleibt)."
fi

# --- 2. Freigabe einschalten --------------------------------------------------
profile="$(env_wert COMPOSE_PROFILES)"
if [[ ",$profile," != *",dateifreigabe,"* ]]; then
    env_setzen COMPOSE_PROFILES "${profile:+$profile,}dateifreigabe" \
        "Ordner-Freigabe (dateifreigabe.sh) eingeschaltet am $(date '+%d.%m.%Y')"
    log ".env: COMPOSE_PROFILES enthaelt jetzt dateifreigabe."
fi
# Fuer die folgenden compose-Aufrufe (die .env liest compose erst beim naechsten Start neu)
COMPOSE_PROFILES="$(printf '%s,dateifreigabe' "$profile" | tr ',' '\n' | awk 'NF && !s[$0]++' | paste -sd, -)"
export COMPOSE_PROFILES

log "Baue und starte die Ordner-Freigabe ..."
"${COMPOSE[@]}" build --pull dateifreigabe
"${COMPOSE[@]}" up -d --no-deps dateifreigabe

# Haengt das Status-Volume schon im ERP? Bei einer aelteren docker-compose.yml
# fehlt es - dann kennt das ERP die Adresse nicht.
if ! docker inspect erp-app --format '{{range .Mounts}}{{.Destination}} {{end}}' 2>/dev/null \
        | grep -q '/app/dateifreigabe'; then
    log "ERP neu erstellen, damit es die Adresse der Freigabe lesen kann ..."
    "${COMPOSE[@]}" up -d --no-deps app
fi

# --- 3. Warten, bis der Ordner bereit ist -------------------------------------
log "Warte auf die Anmeldung im Tailscale-Netz (bis zu $((WARTEN_SEKUNDEN / 60)) Minuten) ..."
link_gezeigt=""
for _ in $(seq 1 "$WARTEN_SEKUNDEN"); do
    if docker exec "$CONTAINER" test -f /status/dateifreigabe.json 2>/dev/null; then
        adresse="$(docker exec "$CONTAINER" sh -c 'jq -r .adresse /status/dateifreigabe.json')"
        echo ""
        log "Fertig. Der Ordner ist im Tailscale-Netz erreichbar unter:  $adresse"
        echo ""
        echo "Noch zu tun:"
        echo "  1. Auf diesem Rechner im ERP-Ordner:  docker compose up -d"
        echo "     (bindet die Uploads auch fuer die taegliche Sicherung neu ein)"
        echo "  2. Im ERP: Einstellungen > Dateien > \"Diese Adresse eintragen\" > speichern."
        echo "  3. Jeder PC: Tailscale installieren und das Hilfsprogramm einrichten"
        echo "     (Einstellungen > Dateien > \"Dateien direkt am PC oeffnen\")."
        echo "     Benutzer und Passwort fuer den Ordner bringt die Einrichtung mit."
        exit 0
    fi
    zustand="$(docker inspect "$CONTAINER" --format '{{.State.Status}}' 2>/dev/null)" || zustand=fehlt
    if [[ "$zustand" != running ]]; then
        docker logs --tail 20 "$CONTAINER" 2>&1 || true
        fehler "Die Ordner-Freigabe laeuft nicht ($zustand). Meldung oben; ganzes Log: docker compose logs dateifreigabe"
    fi
    if [[ -z "$link_gezeigt" ]]; then
        link="$(docker logs "$CONTAINER" 2>&1 | grep -Eo 'https://login\.tailscale\.com/[A-Za-z0-9/_-]+' | tail -n 1 || true)"
        if [[ -n "$link" ]]; then
            echo ""
            echo "  >>> Bitte diesen Link im Browser oeffnen und mit dem Admin-Konto von Tailscale bestaetigen:"
            echo "      $link"
            echo ""
            link_gezeigt=1
        fi
    fi
    if docker logs --tail 5 "$CONTAINER" 2>&1 | grep -q 'FEHLER:'; then
        docker logs --tail 20 "$CONTAINER" 2>&1 || true
        fehler "Die Ordner-Freigabe meldet einen Fehler (oben)."
    fi
    sleep 1
done
fehler "Nach $((WARTEN_SEKUNDEN / 60)) Minuten noch nicht bereit. Log: docker compose logs dateifreigabe"
