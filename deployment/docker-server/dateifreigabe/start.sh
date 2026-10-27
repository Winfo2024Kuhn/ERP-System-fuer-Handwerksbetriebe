#!/bin/sh
# =============================================================================
# ERP Handwerk - Startskript der Ordner-Freigabe (Container "dateifreigabe")
# =============================================================================
# 1. Tailscale starten und anmelden: mit TS_AUTHKEY ohne Zutun, sonst steht ein
#    Anmelde-Link im Log (docker compose logs dateifreigabe). Danach merkt sich
#    der Container die Anmeldung im Volume.
# 2. Samba starten: gibt /daten/<Ordner> nur ueber Tailscale frei.
# 3. Dem ERP mitteilen, unter welcher Adresse und mit welchem Zugang der Ordner
#    erreichbar ist (Volume dateifreigabe_status, liest das ERP nur). So muss
#    niemand die Adresse abtippen, und die Einrichtung des Hilfsprogramms an den
#    PCs bringt den Zugang gleich mit.
#
# Einstellungen (alle optional, Erklaerung in .env.example):
#   DATEIFREIGABE_NAME      Geraetename im Tailscale-Netz (Standard erp-dateien)
#   DATEIFREIGABE_ORDNER    freigegebener Unterordner der Uploads (CADdrawings)
#   DATEIFREIGABE_TAG       Tailscale-Tag des Geraets (tag:erp-dateien, leer = keins)
#   DATEIFREIGABE_PASSWORT  Passwort fuer den Ordner (leer = einmal zufaellig erzeugt)
#   TS_AUTHKEY              Tailscale-Schluessel fuer die Anmeldung ohne Link
# =============================================================================
set -eu

NAME="${DATEIFREIGABE_NAME:-erp-dateien}"
ORDNER="${DATEIFREIGABE_ORDNER:-CADdrawings}"
TAG="${DATEIFREIGABE_TAG-tag:erp-dateien}"
PASSWORT="${DATEIFREIGABE_PASSWORT:-}"
BENUTZER=erp-dateien

DATEN=/daten
ZUSTAND=/var/lib/dateifreigabe
SOCKET=/run/tailscale/tailscaled.sock
STATUS=/status/dateifreigabe.json
KONFIG=/run/samba/smb.conf

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
# Eine Minute warten, damit "restart: always" bei einem Einstellungsfehler nicht
# im Sekundentakt neu startet und das Log flutet
fehler() { log "FEHLER: $*"; sleep 60; exit 1; }
ts() { tailscale --socket="$SOCKET" "$@"; }

# --- Einstellungen pruefen: landen in smb.conf und in Tailscale-Befehlen ------
printf '%s' "$NAME" | grep -Eq '^[a-z0-9][a-z0-9-]{0,62}$' \
    || fehler "DATEIFREIGABE_NAME='$NAME' ist ungueltig (nur Kleinbuchstaben, Ziffern und -)."
printf '%s' "$ORDNER" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$' \
    || fehler "DATEIFREIGABE_ORDNER='$ORDNER' ist ungueltig (nur Buchstaben, Ziffern, _ und -)."
if [ -n "$TAG" ]; then
    printf '%s' "$TAG" | grep -Eq '^tag:[a-z0-9-]{1,63}$' \
        || fehler "DATEIFREIGABE_TAG='$TAG' ist ungueltig (Form tag:name)."
fi
[ -d "$DATEN" ] || fehler "$DATEN fehlt - das Upload-Volume ist nicht eingebunden."

mkdir -p "$ZUSTAND/tailscale" /run/tailscale /run/samba/lock /var/lib/samba/private \
    /var/cache/samba /var/log/samba
chmod 700 "$ZUSTAND"
# Solange der Ordner nicht erreichbar ist, bietet das ERP keine Adresse an
rm -f "$STATUS"

# --- Passwort: aus .env oder einmal erzeugt und im Volume gemerkt ------------
if [ -z "$PASSWORT" ]; then
    if [ ! -s "$ZUSTAND/passwort" ]; then
        (umask 077; tr -dc 'A-HJ-NP-Za-km-z2-9' < /dev/urandom | head -c 32 > "$ZUSTAND/passwort")
        log "Passwort fuer den Ordner erzeugt (steckt in der Einrichtung des Hilfsprogramms)."
    fi
    PASSWORT="$(cat "$ZUSTAND/passwort")"
fi
# Nur Buchstaben und Ziffern: Es landet in der Windows-Einrichtung (cmdkey) und
# im Mac-Schluesselbund, ohne dass Sonderzeichen maskiert werden muessen
printf '%s' "$PASSWORT" | grep -Eq '^[A-Za-z0-9]{16,128}$' \
    || fehler "DATEIFREIGABE_PASSWORT ist ungueltig (16 bis 128 Buchstaben und Ziffern)."

# --- Ordner anlegen -----------------------------------------------------------
if [ ! -d "$DATEN/$ORDNER" ]; then
    mkdir -p "$DATEN/$ORDNER"
    chown 10001:10001 "$DATEN/$ORDNER"
    log "Ordner $ORDNER im Upload-Volume angelegt."
fi

# --- Tailscale ----------------------------------------------------------------
tailscaled --statedir="$ZUSTAND/tailscale" --socket="$SOCKET" --tun=tailscale0 --port=41642 \
    > /var/log/tailscaled.log 2>&1 &
TSD=$!

beenden() {
    rm -f "$STATUS"
    [ -n "${SMBD:-}" ] && kill "$SMBD" 2>/dev/null
    kill "$TSD" 2>/dev/null
    wait 2>/dev/null
    exit 0
}
trap beenden TERM INT

i=0
until ts status --json >/dev/null 2>&1 || [ "$(ts status --json 2>/dev/null | jq -r '.BackendState // empty')" = NeedsLogin ]; do
    i=$((i + 1))
    [ "$i" -le 30 ] || { tail -n 20 /var/log/tailscaled.log; fehler "Tailscale startet nicht (siehe Zeilen oben). Gibt es /dev/net/tun?"; }
    kill -0 "$TSD" 2>/dev/null || { tail -n 20 /var/log/tailscaled.log; fehler "Tailscale ist beim Start abgebrochen (siehe Zeilen oben)."; }
    sleep 1
done

set -- --reset --hostname="$NAME" --accept-dns=false --accept-routes=false
[ -n "$TAG" ] && set -- "$@" --advertise-tags="$TAG"
if [ -n "${TS_AUTHKEY:-}" ]; then
    set -- "$@" --auth-key="$TS_AUTHKEY"
fi
if [ "$(ts status --json | jq -r '.BackendState')" != Running ]; then
    log "Melde den Ordner im Tailscale-Netz an. Ohne TS_AUTHKEY erscheint gleich ein Anmelde-Link:"
    log "Link im Browser oeffnen und mit dem Admin-Konto des Tailscale-Netzes bestaetigen."
fi
if ! ts up "$@" 2>&1; then
    if [ -n "$TAG" ]; then
        fehler "Anmeldung fehlgeschlagen. Steht \"$TAG\" in den Tailscale-Regeln unter tagOwners? (README, Abschnitt Ordner-Freigabe)"
    fi
    fehler "Anmeldung bei Tailscale fehlgeschlagen (Meldung oben)."
fi

DNS=""
i=0
while [ -z "$DNS" ]; do
    DNS="$(ts status --json | jq -r '.Self.DNSName // empty' | sed 's/\.$//')"
    i=$((i + 1))
    [ -n "$DNS" ] || [ "$i" -le 30 ] || fehler "Tailscale meldet keinen Geraetenamen."
    [ -n "$DNS" ] || sleep 1
done
log "Im Tailscale-Netz als $DNS"
# Netzmaske des Tailscale-Bereichs: Mit /32 laesst Samba die IPv4-Adresse aus (keine
# Broadcast-Adresse). Gebunden wird trotzdem nur an die eigene Adresse.
ADRESSEN="$(ts ip -4 | sed 's|$|/10|') $(ts ip -6 | sed 's|$|/48|')"
printf '%s' "$ADRESSEN" | grep -Eq '^[0-9a-f.:/ ]+$' || fehler "Tailscale meldet keine gueltigen Adressen: $ADRESSEN"

# --- Samba --------------------------------------------------------------------
# Mac-Finder-Infos als erweiterte Attribute speichern statt als ._-Dateien.
# Kann der Ordner das nicht (z. B. ein Windows-Ordner ueber Docker Desktop),
# laeuft die Freigabe ohne diese Erweiterung.
PROBE="$DATEN/$ORDNER/.erp-xattr-probe"
VFS="$(mktemp)"
if touch "$PROBE" 2>/dev/null && setfattr -n user.erp -v 1 "$PROBE" 2>/dev/null; then
    cat > "$VFS" <<'EOF'
   vfs objects = catia fruit streams_xattr
   fruit:metadata = stream
   fruit:posix_rename = yes
   fruit:veto_appledouble = yes
   fruit:wipe_intentionally_left_blank_rfork = yes
   fruit:delete_empty_adfiles = yes
EOF
else
    log "Hinweis: Der Ordner kann keine erweiterten Attribute - Mac-Finder-Infos landen in ._-Dateien."
    echo '   vfs objects = catia' > "$VFS"
fi
rm -f "$PROBE"
awk -v ordner="$ORDNER" -v vfs="$VFS" -v adressen="$(printf '%s' "$ADRESSEN" | tr '\n' ' ')" '
    /^@VFS@$/ { while ((getline zeile < vfs) > 0) print zeile; next }
    { gsub(/@ORDNER@/, ordner); gsub(/@TAILSCALE_ADRESSEN@/, adressen); print }
' /etc/samba/smb.conf.vorlage > "$KONFIG"
rm -f "$VFS"
testparm -s "$KONFIG" >/dev/null 2>&1 || { testparm -s "$KONFIG"; fehler "smb.conf ungueltig (siehe oben)."; }

printf '%s\n%s\n' "$PASSWORT" "$PASSWORT" | smbpasswd -c "$KONFIG" -s -a "$BENUTZER" >/dev/null
smbd --configfile="$KONFIG" --foreground --no-process-group --debug-stdout &
SMBD=$!

# --- Dem ERP Bescheid geben ---------------------------------------------------
i=0
until nc -z 127.0.0.1 445 2>/dev/null; do
    i=$((i + 1))
    [ "$i" -le 30 ] || fehler "Samba lauscht nicht auf Port 445."
    kill -0 "$SMBD" 2>/dev/null || fehler "Samba ist beim Start abgebrochen (Meldung oben)."
    sleep 1
done
NEU="$STATUS.neu"
(umask 027
 jq -n --arg adresse "\\\\$DNS\\$ORDNER" --arg benutzer "$BENUTZER" --arg passwort "$PASSWORT" \
     --arg stand "$(date -Iseconds)" \
     '{adresse: $adresse, benutzer: $benutzer, passwort: $passwort, stand: $stand}' > "$NEU")
chown 0:10001 "$NEU"
chmod 640 "$NEU"
mv -f "$NEU" "$STATUS"
log "Bereit: \\\\$DNS\\$ORDNER (Benutzer $BENUTZER)"

# --- Wache: faellt eines der beiden aus, startet Docker den Container neu -----
while kill -0 "$TSD" 2>/dev/null && kill -0 "$SMBD" 2>/dev/null; do
    sleep 5 &
    wait $! || true
done
log "Tailscale oder Samba ist beendet - Neustart."
rm -f "$STATUS"
kill "$SMBD" "$TSD" 2>/dev/null || true
exit 1
