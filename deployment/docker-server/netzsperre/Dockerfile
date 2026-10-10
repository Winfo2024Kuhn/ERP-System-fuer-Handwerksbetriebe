# =============================================================================
# ERP Handwerk - Netzsperre
# =============================================================================
# Setzt die Firewall-Regeln, die das ERP vom Firmennetz trennen (sperre.sh).
# Braucht dafuer das Recht NET_ADMIN im Netz des Rechners - und sonst nichts:
# kein Docker-Socket, keine Daten, keine Ports.
# =============================================================================
FROM alpine:3.22

RUN apk add --no-cache iptables iptables-legacy

COPY sperre.sh /usr/local/bin/erp-netzsperre
RUN chmod +x /usr/local/bin/erp-netzsperre

# Gesund = Regeln stehen und haengen in DOCKER-USER
HEALTHCHECK --interval=60s --timeout=10s --start-period=20s \
    CMD test -f /tmp/sperre-aktiv || test "${NETZSPERRE:-ja}" != "ja"

ENTRYPOINT ["/usr/local/bin/erp-netzsperre"]
