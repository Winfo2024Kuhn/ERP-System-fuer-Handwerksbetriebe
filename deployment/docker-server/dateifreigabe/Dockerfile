# =============================================================================
# ERP Handwerk - Ordner-Freigabe fuer "Dateien direkt am PC oeffnen"
# =============================================================================
# Ein Container, zwei Programme:
#   tailscaled  macht den Container zu einem eigenen Geraet im Tailscale-Netz
#               (z. B. erp-dateien.<tailnet>.ts.net) - im Buero wie unterwegs.
#   smbd        gibt darueber den Zeichnungs-Ordner aus dem Upload-Volume frei.
# Keine Windows-Freigabe, kein Windows-Konto, kein Port im Firmennetz.
# Basis ist das offizielle Tailscale-Image (Alpine): "docker compose build --pull
# dateifreigabe" holt mit der neuen Tailscale-Fassung auch Samba-Updates.
# =============================================================================
FROM tailscale/tailscale:stable

# attr: prueft beim Start, ob der Ordner erweiterte Attribute kann (Mac-Finder-Infos)
RUN apk add --no-cache samba-server samba-common-tools jq attr \
 # Gleiche Kennung wie der Benutzer "erp" im ERP-Container (Dockerfile im
 # Projektstamm): Dateien, die ueber die Freigabe entstehen, kann das ERP lesen
 # und umgekehrt.
 && addgroup -g 10001 erp \
 && adduser -D -H -u 10001 -G erp -s /sbin/nologin -h /nonexistent erp-dateien

COPY smb.conf.vorlage /etc/samba/smb.conf.vorlage
COPY start.sh /usr/local/bin/dateifreigabe-start
RUN chmod 755 /usr/local/bin/dateifreigabe-start

ENTRYPOINT ["/usr/local/bin/dateifreigabe-start"]
