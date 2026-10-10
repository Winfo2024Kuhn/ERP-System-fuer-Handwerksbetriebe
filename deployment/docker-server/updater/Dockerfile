# =============================================================================
# ERP Handwerk - Updater
# =============================================================================
# Kleiner Hilfscontainer, der das Nachtupdate ausfuehrt. Er steuert ueber den
# Docker-Socket die Container dieses Projekts (App stoppen, sichern, wechseln,
# zurueckrollen). Damit braucht der Rechner selbst weder cron noch bash -
# derselbe Ablauf laeuft auf einem Linux-Server und auf einem Windows-PC mit
# Docker Desktop.
#
# Die Skripte selbst kommen NICHT ins Image, sondern liegen im Projektordner
# (eingebunden als /erp). So reicht es, den Ordner zu aktualisieren.
# =============================================================================
FROM alpine:3.22

RUN apk add --no-cache bash curl gzip coreutils util-linux findutils grep sed tzdata \
        docker-cli docker-cli-compose

COPY start.sh /usr/local/bin/erp-updater-start
RUN chmod +x /usr/local/bin/erp-updater-start

ENTRYPOINT ["/usr/local/bin/erp-updater-start"]
