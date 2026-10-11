# =============================================================================
# ERP Handwerk - Updater
# =============================================================================
# Kleiner Hilfscontainer fuer das Nachtupdate und die taegliche Sicherung. Er
# steuert ueber den Docker-Socket die Container dieses Projekts (App stoppen,
# sichern, wechseln, zurueckrollen). Damit braucht der Rechner selbst weder
# cron noch bash - derselbe Ablauf laeuft auf einem Linux-Server und auf einem
# Windows-PC mit Docker Desktop.
#
# Die Skripte - auch das Startskript - kommen NICHT ins Image, sondern liegen
# im Projektordner (eingebunden als /erp). So reicht es, den Ordner zu
# aktualisieren. Neu bauen (docker compose build updater) nur, wenn sich
# diese Datei aendert.
# =============================================================================
FROM alpine:3.22

# rsync: hochgeladene Dateien auf Festplatte/Cloud-Ordner spiegeln (sicherung.sh)
RUN apk add --no-cache bash curl gzip coreutils util-linux findutils grep sed tzdata rsync \
        docker-cli docker-cli-compose

ENTRYPOINT ["bash", "/erp/updater/start.sh"]
