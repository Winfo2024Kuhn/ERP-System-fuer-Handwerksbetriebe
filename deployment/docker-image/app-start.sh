#!/bin/sh
# =============================================================================
# Startpunkt des ERP-Containers (ENTRYPOINT im Dockerfile)
# =============================================================================
# Java laeuft NIE als root, sondern als Benutzer "erp" (uid 10001) ohne jede
# Linux-Berechtigung. Findet jemand eine Luecke im ERP, hat er im Container
# keine Rechte - und damit auch keinen Hebel gegen den Rechner.
#
# Root ist nur fuer die ersten Millisekunden noetig: Upload- und Log-Ordner aus
# aelteren Installationen gehoeren noch root (fruehere Images liefen als root).
# Das Nachtupdate tauscht nur das Image, nicht die docker-compose.yml beim
# Kunden - ohne diesen Schritt koennte die neue Version dort nichts speichern.
#
# Startet der Container gleich als normaler Benutzer (docker run --user ...),
# wird nur Java gestartet.
# =============================================================================
set -eu

ERP_UID=10001

# Ein Proxy auf demselben Rechner (tailscale serve, cloudflared) erreicht den
# Container ueber das Gateway seines Docker-Netzes, nicht ueber 127.0.0.1 - die
# App muss ihm dann die weitergereichte Absender-Adresse (X-Forwarded-For)
# glauben. Nur zusammen mit APP_ADRESSE=127.0.0.1 einschalten: Ist der Port im
# ganzen LAN offen, kommen dort (Docker Desktop) ALLE Anfragen ueber dieses
# Gateway, und jeder im LAN koennte sich als beliebige Adresse ausgeben.
if [ "${ERP_LOKALER_PROXY:-nein}" = "ja" ]; then
    hex="$(awk '$2 == "00000000" && $3 != "00000000" { print $3; exit }' /proc/net/route)"
    if [ -n "$hex" ]; then
        # /proc/net/route: Gateway als Hex, niederwertigstes Byte zuerst
        gateway="$(printf '%d.%d.%d.%d' \
            "0x$(echo "$hex" | cut -c7-8)" "0x$(echo "$hex" | cut -c5-6)" \
            "0x$(echo "$hex" | cut -c3-4)" "0x$(echo "$hex" | cut -c1-2)")"
        ZEITERFASSUNG_SECURITY_TRUSTEDPROXIES="${ZEITERFASSUNG_SECURITY_TRUSTEDPROXIES:+$ZEITERFASSUNG_SECURITY_TRUSTEDPROXIES,}$gateway"
        export ZEITERFASSUNG_SECURITY_TRUSTEDPROXIES
        echo "Lokaler Proxy: vertraue Weiterleitungen vom Docker-Gateway $gateway"
    else
        echo "WARNUNG: ERP_LOKALER_PROXY=ja, aber kein Docker-Gateway gefunden." >&2
    fi
fi

# Caches einzelner Bibliotheken (z. B. Schriften fuer PDFs) landen im
# Home-Verzeichnis. Das Dateisystem des Containers ist schreibgeschuetzt,
# /tmp ist der einzige freie Ort ausser den Daten-Ordnern.
HOME=/tmp
export HOME

if [ "$(id -u)" = "0" ]; then
    for ordner in /app/uploads /app/logs; do
        [ -d "$ordner" ] || continue
        # Nur, was der ERP-Benutzer sonst nicht beschreiben duerfte. Ordner aus
        # Windows (Docker Desktop) sind fuer alle beschreibbar - die bleiben,
        # wie sie sind. Bei einem alten Volume ist das ein einziger Durchlauf.
        find "$ordner" -xdev ! -user "$ERP_UID" ! -perm -0002 \
            -exec chown -h "$ERP_UID:$ERP_UID" {} + 2>/dev/null || true
    done
    # Wechsel auf uid 10001: Der Kernel loescht dabei alle Berechtigungen des
    # Prozesses; no-new-privileges (docker-compose.yml) verhindert, dass er je
    # wieder welche bekommt.
    exec setpriv --reuid="$ERP_UID" --regid="$ERP_UID" --clear-groups -- "$@"
fi
exec "$@"
