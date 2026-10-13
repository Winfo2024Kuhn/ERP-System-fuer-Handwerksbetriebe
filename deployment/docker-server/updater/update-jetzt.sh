#!/usr/bin/env bash
# =============================================================================
# ERP Handwerk - Update per Klick ("Jetzt aktualisieren")
# =============================================================================
# Laeuft IM Updater-Container. Aufgerufen wird es vom kleinen Container
# "erp-jetzt-aktualisieren" (docker-compose.yml): In Docker Desktop auf Start
# klicken, und genau das Nachtupdate laeuft sofort - mit Sicherung, Health-Check
# und automatischem Rollback. Hier steckt keine eigene Update-Logik.
#
# Das Update laeuft abgekoppelt (eigene Sitzung, Ausgabe in eine Datei): Wer im
# Klick-Container "Stop" drueckt oder Docker Desktop schliesst, bricht es nicht
# mitten in der Datenbank ab. Dieses Skript zeigt nur das Log und den Ausgang.
#
# Exit-Code = Exit-Code von nachtupdate.sh (0 ok/nichts zu tun, 1 nicht
# eingespielt, 2 NOTFALL, 3 Voraussetzung fehlt), damit Docker Desktop einen
# Fehlschlag als solchen anzeigt. Jeder andere Wert heisst: Ausgang unklar.
# =============================================================================
set -uo pipefail
# Das Log kann Kundendaten enthalten (wie bei nachtupdate.sh)
umask 077

SKRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Im Updater-Container ist /run beschreibbar. Dateien ueberleben einen Neustart
# des Containers, gehen aber beim Neuanlegen verloren - deshalb unten das Aufraeumen.
ARBEITS_DIR="${UPDATE_JETZT_DIR:-/run}"
KENNUNG="$(date +%Y%m%d-%H%M%S)-$$"
LOG_DATEI="$ARBEITS_DIR/update-jetzt.$KENNUNG.log"
CODE_DATEI="$ARBEITS_DIR/update-jetzt.$KENNUNG.exit"

# Reste abgebrochener Klicks (Stop mitten im Lauf) wegraeumen
find "$ARBEITS_DIR" -maxdepth 1 -name 'update-jetzt.*' -mmin +1440 -delete 2>/dev/null || true

cat <<'TEXT'
============================================================
 ERP jetzt aktualisieren
============================================================
 Gibt es eine neue Version, sichert das Programm zuerst die
 Datenbank und spielt dann das Update ein. Das ERP ist dabei
 einige Minuten nicht erreichbar. Geht etwas schief, kommt
 automatisch die alte Version zurueck.

 Du kannst dieses Fenster jederzeit schliessen oder den
 Container stoppen: Das Update laeuft trotzdem sauber zu Ende.
============================================================

TEXT

# Laeuft schon ein Update oder die Sicherung, wartet nachtupdate.sh darauf
# (bis zu 30 Minuten) - und schreibt dabei nichts. Das hier sagt es vorher.
if ! flock -n "${LOCK_DATEI:-/run/erp-nachtupdate.lock}" true 2>/dev/null; then
    echo "Ein anderes Update oder die Sicherung laeuft gerade - warte, bis sie fertig ist ..."
fi

: > "$LOG_DATEI"
# Die Pipeline wartet, bis ALLE Schreiber fertig sind (auch das "tee" in
# nachtupdate.sh, auf das bash beim Beenden nicht wartet): Erst dann ist das Log
# vollstaendig, und erst dann wird der Ausgang geschrieben. Der Ausgang kommt
# ueber eine Zwischendatei, damit der Leser nie eine leere Datei sieht.
setsid bash -c '
    bash "$1/nachtupdate.sh" 2>&1 | cat >>"$2"
    echo "${PIPESTATUS[0]}" >"$3.neu" && mv "$3.neu" "$3"
' _ "$SKRIPT_DIR" "$LOG_DATEI" "$CODE_DATEI" </dev/null >/dev/null 2>&1 &
lauf=$!

# Log mitlesen, bis das Update fertig ist. Schliesst jemand das Fenster, laeuft
# dieses Skript hoechstens noch bis zum Ende mit - das Update ist davon unabhaengig.
position=0
code=""
while :; do
    # Erst den Ausgang pruefen, dann das Log lesen: Steht der Ausgang schon da,
    # ist das Log vollstaendig
    fertig=nein
    [[ -e "$CODE_DATEI" ]] && fertig=ja
    groesse="$(wc -c < "$LOG_DATEI" 2>/dev/null || echo 0)"
    groesse="${groesse//[[:space:]]/}"
    if (( groesse > position )); then
        tail -c +"$((position + 1))" "$LOG_DATEI" | head -c "$((groesse - position))"
        position=$groesse
        continue
    fi
    [[ "$fertig" == "ja" ]] && break
    if ! kill -0 "$lauf" 2>/dev/null && [[ ! -e "$CODE_DATEI" ]]; then
        echo "FEHLER: Der Update-Lauf ist ohne Ergebnis beendet worden."
        code=255
        break
    fi
    sleep 1
done
[[ -n "$code" ]] || code="$(cat "$CODE_DATEI" 2>/dev/null)"
[[ "$code" =~ ^[0-9]+$ ]] || code=255

echo
echo "============================================================"
case "$code" in
    0)
        if grep -q 'Update erfolgreich' "$LOG_DATEI"; then
            echo " Fertig: Das ERP wurde aktualisiert und laeuft."
        elif grep -q 'Keine neue Version' "$LOG_DATEI"; then
            echo " Fertig: Das ERP ist schon auf dem neuesten Stand."
        else
            echo " Fertig. Details stehen oben."
        fi
        ;;
    1)
        echo " Das Update wurde NICHT eingespielt. Die bisherige Version"
        echo " laeuft weiter, die Daten sind unveraendert. Den Grund"
        echo " siehst du oben und im ERP unter Benachrichtigungen."
        ;;
    2)
        echo " NOTFALL: Das ERP laeuft moeglicherweise nicht. Bitte den"
        echo " Betreuer anrufen. Die Sicherung liegt im Ordner sicherungen."
        ;;
    3)
        echo " Es wurde nichts veraendert (Voraussetzung fehlt, siehe oben)."
        ;;
    *)
        echo " Der Ausgang ist unklar (Code $code). Bitte im ERP unter"
        echo " Benachrichtigungen und im Ordner logs nachsehen, ob das ERP"
        echo " laeuft."
        ;;
esac
echo "============================================================"

rm -f "$LOG_DATEI" "$CODE_DATEI"
exit "$code"
