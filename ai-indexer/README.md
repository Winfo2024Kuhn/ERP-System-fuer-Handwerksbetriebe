# AI Indexer — Codebase RAG for KI-Hilfe

Indexiert den Java- und React-Quellcode in eine Qdrant-Vektordatenbank für kontextsensitive KI-Hilfe.

## Voraussetzungen

1. **Docker** mit `docker compose` (für Qdrant)
2. **Python 3.11+**
3. **Gemini API Key** (derselbe wie in `application.properties`)

## Setup

```powershell
# 1. Qdrant starten (aus Projekt-Root)
docker compose up -d

# 2. Python-Umgebung einrichten
cd ai-indexer
python -m venv .venv
.venv\Scripts\activate   # Windows
pip install -r requirements.txt

# 3. API Key setzen
$env:GEMINI_API_KEY = "AIza..."
```

## Nutzung

```powershell
# Dry Run — zeigt Chunks ohne Embedding/Upload
python indexer.py --dry-run

# Vollständige Indexierung
python indexer.py

# Mit benutzerdefiniertem Collection-Namen
python indexer.py --collection my_collection
```

## Architektur

```
Codebase (.java + .tsx + .md)
  → Chunking (Klassen/Methoden/Komponenten)
    → Gemini Embedding (text-embedding-004)
      → Qdrant Vector DB (localhost:6333)
```

Jeder Chunk enthält:
- `content` — Der Code/Text
- `file_path` — Relativer Pfad zur Datei
- `category` — z.B. `backend-controller`, `frontend-page`
- `chunk_type` — z.B. `class`, `method`, `component`
- `name` — z.B. `AngebotController.getAll`

## Qdrant Dashboard

Nach dem Start erreichbar unter: http://localhost:6333/dashboard

## Reindexierung

Einfach `python indexer.py` erneut ausführen — die Collection wird dabei neu erstellt.
