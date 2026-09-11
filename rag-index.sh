#!/bin/bash
# RAG Indexierung starten (Linux/Mac)

set -e

if [ -z "$1" ]; then
  echo "❌ Gemini API Key erforderlich"
  echo "Verwendung: ./rag-index.sh <gemini-api-key> [project-root]"
  echo ""
  echo "Beispiel:"
  echo "  ./rag-index.sh sk-1234567890 ."
  echo ""
  echo "Oder mit Env-Variable:"
  echo "  export GEMINI_API_KEY='sk-1234567890'"
  echo "  ./rag-index.sh"
  exit 1
fi

API_KEY="${1:-$GEMINI_API_KEY}"
PROJECT_ROOT="${2:-.}"

if [ -z "$API_KEY" ]; then
  echo "❌ Gemini API Key nicht gesetzt"
  exit 1
fi

echo "📦 Kompiliere Projekt..."
mvn clean compile assembly:single -DskipTests -q

JAR=$(find target -name "*-jar-with-dependencies.jar" | head -1)

if [ -z "$JAR" ]; then
  echo "❌ JAR nicht gefunden nach Kompilierung"
  exit 1
fi

echo "🚀 Starte RAG-Indexierung..."
echo "   JAR: $JAR"
echo "   Root: $PROJECT_ROOT"
echo ""

java -cp "$JAR" \
  org.example.kalkulationsprogramm.cli.StandaloneRagIndexer "$API_KEY" "$PROJECT_ROOT"

echo ""
echo "✅ Fertig! Cache gespeichert in: $PROJECT_ROOT/.rag-cache.json"
