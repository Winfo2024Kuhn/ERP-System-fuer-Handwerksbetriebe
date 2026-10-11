#!/usr/bin/env bash
# Startet das ERP lokal mit Docker und füllt die leere Datenbank mit Demo-Daten.
# Aufruf aus dem Projektordner: scripts/demo/demo-starten.sh
# Aufräumen (inkl. Daten):     docker compose -p erp-demo down -v
#
# Eigener Projektname "erp-demo": Die Demo bekommt eigene Volumes und trifft nie
# eine Datenbank, die mit "docker compose up" im selben Ordner läuft. Läuft schon
# ein Stack aus dieser docker-compose.yml oder ist Port 8080 belegt, bricht der
# Start ab.
set -euo pipefail

cd "$(dirname "$0")/../.."
compose=(docker compose -p erp-demo)

# Eine APP_DB_URL aus der .env zeigte sonst auf eine fremde Datenbank. Leer
# greift der Standard aus docker-compose.yml: der Demo-Container "postgres".
export APP_DB_URL=

echo "ERP bauen und starten (der erste Build dauert einige Minuten) ..."
"${compose[@]}" up -d --build --wait --wait-timeout 600 || {
    echo "Die App ist nicht bereit geworden. Logs: docker compose -p erp-demo logs -f app" >&2
    exit 1
}

echo "Demo-Daten laden ..."
"${compose[@]}" exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
    < scripts/demo/demo-daten.sql

echo
echo "Fertig: http://localhost:8080"
echo "Wer sich zuerst registriert, wird Admin."
