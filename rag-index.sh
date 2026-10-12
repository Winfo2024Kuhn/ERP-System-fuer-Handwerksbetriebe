#!/usr/bin/env bash
# =============================================================================
# RAG-Datei der KI-Hilfe lokal erzeugen bzw. aktualisieren (Linux/Mac)
# =============================================================================
# Dieselbe Logik wie beim Image-Bau in GitHub und im Server (RAG_INDEXING.md):
# Was sich seit der vorhandenen Datei nicht geaendert hat, wird nicht neu
# eingebettet. Fuer die Kundenserver ist das nicht noetig - deren RAG-Datei
# kommt fertig mit dem Docker-Image.
#
#   GEMINI_API_KEY=... ./rag-index.sh                 -> .rag-cache.json
#   GEMINI_API_KEY=... ./rag-index.sh pfad/datei.json
#   ./rag-index.sh --nur-zaehlen                      (ohne Schluessel, ohne Kosten)
#
# Den Schluessel nur als Umgebungsvariable, nie als Argument (Prozessliste,
# Shell-Verlauf).
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"

echo "Baue das ERP (ohne Tests) ..."
./mvnw -q -DskipTests package

# Nur der Spring-Boot-Jar - ein alter "...-jar-with-dependencies.jar" sortiert davor
JAR="$(ls target/Kalkulationsprogramm-*.jar | grep -vE -- '-(sources|javadoc|jar-with-dependencies)\.jar$' | head -1 || true)"
if [[ -z "$JAR" ]]; then
    echo "Kein Kalkulationsprogramm-*.jar in target/ - Build fehlgeschlagen?" >&2
    exit 1
fi
exec java -cp "$JAR" \
    -Dloader.main=org.example.kalkulationsprogramm.cli.StandaloneRagIndexer \
    org.springframework.boot.loader.launch.PropertiesLauncher . "$@"
