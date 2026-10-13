#!/bin/sh
# =============================================================================
# Netzsperre: Das ERP darf nicht ins Firmennetz
# =============================================================================
# Bricht jemand ins ERP ein, soll er von dort aus weder die PCs im Buero, noch
# die Buchhaltung, noch den Rechner selbst (Dateifreigaben!) erreichen. Das ERP
# braucht aus dem Firmennetz nur das, was in NETZSPERRE_AUSNAHMEN steht (z. B.
# die FRITZ!Box fuer Anrufliste, Anrufbeantworter und Anrufmonitor). Ins
# Internet (Mail, KI, Website) und zur eigenen Datenbank darf es weiter.
#
# Umsetzung: eigene Kette ERP-NETZSPERRE in der Docker-Firewall (DOCKER-USER),
# gilt nur fuer Verbindungen, die das ERP SELBST aus seinem Netz (Bridge
# erp-extern) aufbaut. Antworten auf Anfragen an das ERP laufen normal.
# Funktioniert auf Linux-Servern und in Docker Desktop (Windows) gleich: Dort
# laeuft Docker in einer eigenen kleinen VM, deren Firewall wir hier setzen.
#
# Die Regeln verschwinden mit jedem Neustart von Docker. Deshalb laeuft dieser
# Container dauerhaft (restart: always), setzt sie beim Start und prueft sie
# jede Minute nach (Namen in den Ausnahmen koennen ihre Adresse aendern).
# =============================================================================
set -u

BRIDGE="${NETZSPERRE_BRIDGE:-erp-extern}"
KETTE="ERP-NETZSPERRE"
NEU="ERP-NETZSPERRE-NEU"
INTERVALL="${NETZSPERRE_INTERVALL:-60}"
# Private Netze (RFC 1918), Link-Local, Tailscale/CGNAT, Multicast
GESPERRT="10.0.0.0/8 172.16.0.0/12 192.168.0.0/16 169.254.0.0/16 100.64.0.0/10 224.0.0.0/4"

log() {
    printf '[netzsperre %s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

# iptables-nft oder iptables-legacy - genommen wird das, in dem Docker seine
# Kette DOCKER-USER angelegt hat. Ohne sie greift die Sperre nicht.
IPT=""
for kandidat in iptables-nft iptables-legacy; do
    if "$kandidat" -w -S DOCKER-USER >/dev/null 2>&1; then
        IPT="$kandidat"
        break
    fi
done

entfernen() {
    [ -n "$IPT" ] || return 0
    while "$IPT" -w -D DOCKER-USER -i "$BRIDGE" -j "$KETTE" 2>/dev/null; do :; done
    while "$IPT" -w -D DOCKER-USER -i "$BRIDGE" -j "$NEU" 2>/dev/null; do :; done
    for k in "$KETTE" "$NEU"; do
        "$IPT" -w -F "$k" 2>/dev/null && "$IPT" -w -X "$k" 2>/dev/null
    done
    return 0
}

if [ "${NETZSPERRE:-ja}" != "ja" ]; then
    entfernen
    log "NETZSPERRE=${NETZSPERRE:-} in .env - keine Sperre, das ERP erreicht das ganze Firmennetz."
    # Nicht beenden: restart: always wuerde den Container sonst endlos neu starten
    exec sleep infinity
fi

if [ -z "$IPT" ]; then
    log "FEHLER: Docker-Firewall (Kette DOCKER-USER) nicht gefunden - Netzsperre NICHT aktiv."
    exit 1
fi

# Ausnahmen "ziel:port" (Komma-getrennt), ziel als IP-Adresse oder Name.
# Gibt je Ausnahme eine Zeile "adresse port" aus. Nur TCP.
ausnahmen_aufloesen() {
    printf '%s\n' "${NETZSPERRE_AUSNAHMEN-fritz.box:49000,fritz.box:1012}" | tr ',' '\n' | while IFS= read -r eintrag; do
        eintrag="$(printf '%s' "$eintrag" | tr -d ' \r')"
        [ -n "$eintrag" ] || continue
        ziel="${eintrag%:*}"
        port="${eintrag##*:}"
        if [ "$ziel" = "$eintrag" ] || ! printf '%s' "$port" | grep -Eq '^[0-9]{1,5}$'; then
            log "Ausnahme '$eintrag' ignoriert: erwartet ziel:port (z. B. fritz.box:49000)" >&2
            continue
        fi
        if printf '%s' "$ziel" | grep -Eq '^[0-9]{1,3}(\.[0-9]{1,3}){3}$'; then
            printf '%s %s\n' "$ziel" "$port"
        else
            adressen="$(getent ahostsv4 "$ziel" 2>/dev/null | awk '{print $1}' | sort -u)"
            if [ -z "$adressen" ]; then
                log "Ausnahme '$eintrag': $ziel laesst sich gerade nicht aufloesen - bleibt gesperrt" >&2
                continue
            fi
            for a in $adressen; do printf '%s %s\n' "$a" "$port"; done
        fi
    done
}

# Gewuenschte Regeln als Text (gleiche Form wie "iptables -S"), damit sich
# leicht vergleichen laesst, ob neu gesetzt werden muss.
soll_regeln() {
    k="$1"
    echo "-A $k -m conntrack --ctstate RELATED,ESTABLISHED -j RETURN"
    # Innerhalb des eigenen Netzes (z. B. Updater -> ERP)
    echo "-A $k -o $BRIDGE -j RETURN"
    ausnahmen_aufloesen | while read -r adresse port; do
        echo "-A $k -d $adresse/32 -p tcp -m tcp --dport $port -j RETURN"
    done
    for netz in $GESPERRT; do
        echo "-A $k -d $netz -j REJECT --reject-with icmp-admin-prohibited"
    done
}

ist_regeln() {
    "$IPT" -w -S "$KETTE" 2>/dev/null | grep -v "^-N "
}

# Neue Kette komplett aufbauen, dann umhaengen: Es gibt keinen Moment ohne Sperre.
MELDEN=ja
setzen() {
    soll="$(soll_regeln "$KETTE")"
    if [ "$soll" = "$(ist_regeln)" ] && "$IPT" -w -C DOCKER-USER -i "$BRIDGE" -j "$KETTE" 2>/dev/null; then
        if [ "$MELDEN" = "ja" ]; then
            log "Sperre aktiv ($IPT, Netz $BRIDGE, Regeln unveraendert)."
            MELDEN=nein
        fi
        return 0
    fi
    MELDEN=nein
    "$IPT" -w -N "$NEU" 2>/dev/null || "$IPT" -w -F "$NEU"
    soll_regeln "$NEU" | while read -r zeile; do
        # shellcheck disable=SC2086 # Regel bewusst in Woerter zerlegen
        "$IPT" -w $zeile || { log "FEHLER beim Setzen: $zeile"; return 1; }
    done || return 1
    "$IPT" -w -I DOCKER-USER 1 -i "$BRIDGE" -j "$NEU"
    while "$IPT" -w -D DOCKER-USER -i "$BRIDGE" -j "$KETTE" 2>/dev/null; do :; done
    "$IPT" -w -F "$KETTE" 2>/dev/null && "$IPT" -w -X "$KETTE" 2>/dev/null
    "$IPT" -w -E "$NEU" "$KETTE"
    log "Sperre aktiv ($IPT, Netz $BRIDGE). Erlaubt aus dem Firmennetz: $(ausnahmen_aufloesen | awk '{printf "%s:%s ", $1, $2}')"
}

beenden() {
    entfernen
    log "Gestoppt - Sperre entfernt."
    exit 0
}
trap beenden TERM INT

while :; do
    if setzen; then
        touch /tmp/sperre-aktiv
    else
        rm -f /tmp/sperre-aktiv
    fi
    # sleep im Hintergrund, damit TERM sofort ankommt
    sleep "$INTERVALL" &
    wait $!
done
