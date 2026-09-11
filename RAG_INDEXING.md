# RAG-Indexierung manuell (ohne Server)

Dieses Projekt enthält zwei Wege, um die RAG-Indizierung durchzuführen:

## 1. **StandaloneRagIndexer** (Empfohlen für schnelle CLI-Läufe)

### Standalone Jar kompilieren

```bash
# Kompiliere das Projekt
mvn clean compile assembly:single -DskipTests

# Das erzeugt einen Fat-Jar mit allen Dependencies
ls target/kalkulationsprogramm-*-jar-with-dependencies.jar
```

### Indexierung starten

```bash
# Setze deinen Gemini API Key
export GEMINI_API_KEY="sk-dein-api-key-hier"

# Starte die Indexierung von der Projektroot aus
java -cp target/kalkulationsprogramm-*-jar-with-dependencies.jar \
  org.example.kalkulationsprogramm.cli.StandaloneRagIndexer "$GEMINI_API_KEY" .

# Oder unter Windows (PowerShell)
$env:GEMINI_API_KEY = "sk-dein-api-key-hier"
java -cp "target/kalkulationsprogramm-*-jar-with-dependencies.jar" `
  org.example.kalkulationsprogramm.cli.StandaloneRagIndexer "$env:GEMINI_API_KEY" .
```

**Was passiert:**
- ✂️ Extrahiert Chunks aus Quellcode (.java, .tsx, .ts, .md)
- 🧠 Embeddet neue/geänderte Chunks via Gemini API
- 💾 Speichert Cache in `.rag-cache.json` (wiederverwendbar)
- ⏳ ~2-5 Minuten für komplette Indexierung (abhängig von Codebase)

**Ausgabe Beispiel:**
```
╔════════════════════════════════════════════════════════════╗
║  STANDALONE RAG INDEXER (keine Server nötig)              ║
╚════════════════════════════════════════════════════════════╝
📂 Projekt-Root: .
🔑 API Key: sk-1234...

📚 Lade Cache...
   ✓ 42 Einträge geladen
✂️  Chunke Quellcode...
   ✓ 156 Chunks extrahiert
🔍 Vergleiche mit Cache...
   ✓ 40 aus Cache, 116 neu zu embedden
🧠 Embedde neue Chunks...
   Batch 1/6 (20 Chunks)...
   Batch 2/6 (20 Chunks)...
   ...
   ✓ 116 Chunks embedded
💾 Speichere Cache...
   ✓ 156 Einträge gespeichert in .rag-cache.json

✅ Indexierung erfolgreich abgeschlossen!
   Cache: .rag-cache.json
```

---

## 2. **RagIndexingRunner** (Spring Boot ApplicationRunner)

Falls du trotzdem Spring Boot starten möchtest, mit Beendigung nach Indexierung:

```bash
# Starte nur die Indexierung und beende dann
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--rag-index" \
  -Dspring.profiles.active=local \
  -Dai.rag.enabled=true \
  -Dai.rag.gemini-api-key="sk-dein-key"
```

---

## Cache-Datei (`.rag-cache.json`)

Der Cache wird automatisch geladen und wiederverwendet:

```json
[
  {
    "content": "public class ProjektController { ... }",
    "filePath": "src/main/java/org/example/kalkulationsprogramm/controller/ProjektController.java",
    "category": "backend-controller",
    "chunkType": "class",
    "name": "ProjektController",
    "contentHash": "a1b2c3d4e5f6g7h8",
    "vector": [0.123, -0.456, ...]
  },
  ...
]
```

### Cache leeren

Willst du eine komplette Neu-Indexierung ohne Cache:

```bash
rm .rag-cache.json
java -cp target/kalkulationsprogramm-*-jar-with-dependencies.jar \
  org.example.kalkulationsprogramm.cli.StandaloneRagIndexer "sk-api-key" .
```

---

## Debugging & Tipps

### 1. **Gemini API Key prüfen**

```bash
# Teste die Gemini API
curl -X POST https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=sk-dein-key \
  -H "Content-Type: application/json" \
  -d '{
    "content": {
      "parts": [{"text": "test"}]
    },
    "taskType": "RETRIEVAL_QUERY",
    "outputDimensionality": 768
  }'
```

### 2. **Nur bestimmte Chunks re-indexieren**

Willst du nur bestimmte Dateien neu embedden, editiere `.rag-cache.json`:
- Lösche die `vector` für diese Einträge oder
- Ändere `contentHash` auf etwas Ungültiges
- Beim nächsten Lauf werden diese neu embedded

### 3. **Chunking optimieren**

Im `StandaloneRagIndexer`:
- `MAX_CHUNK_CHARS = 6000` — Chunk-Größe (kleiner = mehr Chunks, längere Embedding)
- `BATCH_SIZE = 20` — Wie viele Chunks pro Gemini-Batch-API-Call
- `SECRET_PATTERN` — Welche Secrets redacted werden

---

## Projekt-Struktur (relevant für RAG)

```
src/
├── main/
│   ├── java/org/example/kalkulationsprogramm/
│   │   ├── cli/
│   │   │   ├── RagIndexingRunner.java    ← Spring Boot Runner
│   │   │   └── StandaloneRagIndexer.java ← Standalone Main (EMPFOHLEN)
│   │   └── service/
│   │       ├── LocalRagService.java      ← In-Memory Vector Store
│   │       └── QdrantRagService.java     ← Externe Vector DB
│   └── resources/
│       └── application.properties        ← Config
│
└── .rag-cache.json                       ← Cache (auto-created)
```

---

## Configuration (application.properties)

```properties
# Globale RAG-Einstellungen
ai.rag.enabled=true
ai.rag.top-k=10                    # Wie viele Chunks zurückgeben
ai.rag.score-threshold=0.3         # Minimum Cosine-Similarity

# Gemini API
ai.gemini.api-key=sk-your-key      # ODER via SystemSettingsService lesen

# Falls Qdrant genutzt wird (optional)
ai.rag.qdrant.host=localhost
ai.rag.qdrant.port=6333
ai.rag.qdrant.collection=codebase
```

---

## Was wird indexiert?

Der Indexer chunked automatisch:

- ✅ **Frontend** — `react-pc-frontend/src/pages`, `react-pc-frontend/src/components`, `App.tsx`, `types.ts`
- ✅ **Mobile** — `react-zeiterfassung/src/pages`, `react-zeiterfassung/src/components`
- ✅ **Backend** — `src/main/java/.../controller`, `service`, `domain`, `dto`
- ✅ **Docs** — `docs/*.md`
- ❌ **Test-Dateien** — Werden ignoriert

---

## Troubleshooting

| Problem | Lösung |
|---------|--------|
| `java: command not found` | Java-Path nicht gesetzt, `java -version` prüfen |
| `Gemini 401/403 Unauthorized` | API Key ungültig oder abgelaufen |
| `Timeout bei Embedding` | Gemini-Server überlastet, mit Retry automatisch gelöst |
| `.rag-cache.json` wird nicht gelesen | Format-Fehler → lösche und neu erstellen |
| Sehr viele Chunks = sehr lange Indexierung | Reduziere `MAX_CHUNK_CHARS` oder chunke selektiv |

---

## Performance

**Typische Zeiten für das ERP-System:**

- **Chunking** (Dateien lesen + splitten): ~5 Sekunden
- **Embedding** (neue Chunks via Gemini): ~2-3 Minuten (abhängig von Chunk-Anzahl)
  - 1 Batch (~20 Chunks) = ~3-5 Sekunden + Rate-Limit (150ms)
- **Cache speichern** (~200 Einträge als JSON): <1 Sekunde

**Gesamt für komplette Neu-Indexierung:** ~5 Minuten

Bei Wiederholungen (Cache-Hit): ~10-20 Sekunden

---

## Was passiert mit den Embeddings?

1. **LocalRagService** (Default):
   - Vektor-Index im Memory (`CopyOnWriteArrayList<ChunkEntry>`)
   - Persistiert zu `.rag-cache.json`
   - Bei Server-Neustart: Cache geladen + wieder im Memory

2. **QdrantRagService** (Optional):
   - Externe Vector DB (Docker-Container nötig)
   - Höherer Durchsatz, skalierbarer
   - Braucht separate `docker run ...` oder k8s Deployment

---

## Integrierung in Git Workflow

Empfehlung: `.rag-cache.json` committen!

```bash
# In .gitignore NICHT eintragen (!)
# Das ermöglicht schnellere Startups nach git clone

# Optional: Nur bei großen Änderungen neu-indexieren
git add .rag-cache.json
git commit -m "chore: RAG cache update nach Feature-Erweiterung"
```

Falls Cache zu groß wird (>10 MB):

```bash
# Periodisch komprimieren
git rm --cached .rag-cache.json
echo ".rag-cache.json" >> .gitignore
git commit -m "chore: Remove large RAG cache from git"
```

---

## Nächste Schritte

1. **Indexierung durchführen:**
   ```bash
   mvn clean compile assembly:single -DskipTests
   java -cp target/kalkulationsprogramm-*-jar-with-dependencies.jar \
     org.example.kalkulationsprogramm.cli.StandaloneRagIndexer "sk-your-key" .
   ```

2. **Cache validieren:**
   ```bash
   cat .rag-cache.json | jq 'length'  # sollte > 0
   ```

3. **Server starten (optional):**
   ```bash
   mvn spring-boot:run
   # RAG wird automatisch mit LocalRagService initialisiert
   # Logs zeigen: "RAG-Index FERTIG: X Chunks geladen"
   ```

4. **Im Browser testen:**
   - Öffne die App
   - Nutze KI-Features, die RAG-Suche triggen (z.B. KI-Hilfe)
   - Logs sollten zeigen: "Lokale Vektor-Suche: X Treffer..."
