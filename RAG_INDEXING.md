# Wissen der KI-Hilfe (RAG-Datei)

Die KI-Hilfe (Knopf „KI-Hilfe“ in der oberen Menüleiste, Chatfenster unten rechts)
beantwortet Fragen zur Bedienung nur dann richtig, wenn sie das Programm kennt: Doku, Seiten, Knöpfe, Abläufe. Dieses
Wissen steckt in der **RAG-Datei** – Doku und Quellcode, zerlegt in Abschnitte, jeder
mit einem Embedding-Vektor von Gemini. Bei jeder Frage sucht das ERP die passendsten
Abschnitte heraus und gibt sie der KI mit (Retrieval-Augmented Generation).

Stand: Oktober 2026

---

## Kurz: Wer erzeugt die RAG-Datei wann?

| Wo | Wer | Wann | Gemini-Schlüssel |
| --- | --- | --- | --- |
| **Kundenserver (Docker)** | GitHub beim Image-Bau | nach jedem Merge auf `main` | Repo-Secret `GEMINI_API_KEY` |
| **Entwicklung** | das ERP selbst beim Start | bei jedem Start, nur Geändertes | aus Einstellungen → KI-Funktionen |
| **Von Hand** | `rag-index.sh` / `rag-index.cmd` | wenn du willst | Umgebungsvariable `GEMINI_API_KEY` |

Alle drei Wege nutzen dieselbe Klasse (`service/kihilfe/RagIndexer`). Ein Abschnitt,
dessen Inhalt sich nicht geändert hat, wird nie neu eingebettet.

---

## Kundenserver: die RAG-Datei kommt mit dem Nachtupdate

Im Docker-Image liegt kein Quellcode. Die fertige RAG-Datei liegt dort unter
`/app/ki-hilfe/rag-cache.json` (`ai.rag.datei` in `application-docker.properties`).

1. **Image-Bau** (`.github/workflows/docker-image.yml`, Schritt „KI-Hilfe – RAG-Datei
   aktualisieren“): Die RAG-Datei des aktuellen `:stable` dient als Vorlage. Der
   `StandaloneRagIndexer` läuft im gerade gebauten Image, zerlegt Doku und Quellcode
   dieses Stands und bettet nur neue oder geänderte Abschnitte ein.
2. **Image fertigstellen:** Die Datei kommt über den Build-Kontext `ki-hilfe` ins Image
   (`Dockerfile`, Stufe `ki-hilfe`). Das Label `de.erp-handwerk.ki-hilfe.abschnitte`
   hält die Anzahl der Abschnitte fest. Danach läuft der Starttest mit genau diesem Image.
3. **Nachtupdate** (`deployment/docker-server/nachtupdate.sh`): holt das neue Image – und
   damit die passende RAG-Datei. Bei einem Rollback kommt mit der alten Version auch das
   alte Wissen zurück. In Log und Erfolgsmeldung steht „KI-Hilfe kennt … Abschnitte“.
4. **Start der App:** `LocalRagService` lädt die Datei im Hintergrund. Der Server bettet
   selbst nichts ein, nur die Fragen der Nutzer – mit dem Gemini-Schlüssel des Betriebs
   (Einstellungen → KI-Funktionen). Klappt das nicht gleich, gibt es genau einen zweiten
   Versuch nach einer Sekunde; danach antwortet die KI-Hilfe ohne die Suche, statt den
   Nutzer warten zu lassen.

Das ERP liest die Datei nur. Sie gehört `root`, das Dateisystem des Containers ist
schreibgeschützt.

### Sicherheitsnetze

- **Secret `GEMINI_API_KEY` fehlt:** Das Image bekommt die RAG-Datei des letzten `:stable`
  unverändert (Warnung im Workflow). Gibt es auch die nicht, antwortet die KI-Hilfe ohne
  Programmwissen.
- **Gemini überlastet oder Fehler:** Der Indexer hört nach 12 Minuten auf, neue Stapel zu
  beginnen; ein laufender Stapel darf mit Wiederholungen noch etwas brauchen, der ganze
  Schritt höchstens 26 Minuten. Was dann fehlt, folgt beim nächsten Image.
- **Ein Release hängt nie an der KI-Hilfe:** Der Schritt „einbetten“ darf scheitern
  (`continue-on-error`), ebenso der zweite Bau mit der Datei – dann geht das Image mit der
  Vorlage aus `:stable` bzw. ohne RAG-Datei raus. Ist `:stable` wegen eines Netzfehlers
  nicht ladbar, wird nichts eingebettet (ohne Vorlage käme sonst nur Teilwissen heraus).
- **Halbe Dateien:** Geschrieben wird erst nach `….neu`, dann umbenannt; übrig gebliebene
  `.neu`-Dateien löscht der Workflow vor dem Bau. Die Datei bekommt die Rechte `0644`, damit
  der ERP-Benutzer im Container sie lesen kann.
- **Kleiner Rechner beim Kunden:** Die Datei wird Abschnitt für Abschnitt gelesen, nicht
  als ganzer JSON-Baum. Im Speicher belegt sie etwa so viel, wie sie groß ist.

### Einmalig einrichten

Repo-Einstellungen → *Secrets and variables* → *Actions* → *New repository secret*:
Name `GEMINI_API_KEY`, Wert ein Gemini-API-Schlüssel (Google AI Studio). Der erste Lauf
bettet alles ein (rund 3.000 Abschnitte, gut 11 Mio. Zeichen – mit bezahltem Schlüssel
etwa 0,50 $). Danach kostet ein Release nur noch Cent-Beträge. Mit einem Gratis-Schlüssel
reichen die 12 Minuten pro Lauf nicht für alles; dann füllt sich die Datei über mehrere
Releases.

**Alles neu einbetten** (z. B. nach einem Wechsel des Embedding-Modells): Actions →
„Docker-Image fuer Kundenserver“ → *Run workflow* → Häkchen „RAG-Datei komplett neu
einbetten“. Achtung: Auch dieser Lauf veröffentlicht `:stable`. Reichen die 12 Minuten
nicht für alle Abschnitte, haben die Kunden bis zum nächsten Image nur Teilwissen (vorne
die Doku, hinten fehlt Backend-Code). Ein Wechsel des Modells (`RagIndexer.MODELL`)
verwirft die alte Datei ohnehin von selbst.

---

## Entwicklung: das ERP baut die Datei selbst

Mit `./mvnw spring-boot:run` liegt der Quellcode im Projektordner. Beim Start zerlegt
`LocalRagService` ihn, bettet Neues mit dem Gemini-Schlüssel aus
Einstellungen → KI-Funktionen ein und speichert alles in `.rag-cache.json` (gitignored).

- Ohne Schlüssel bleibt RAG aus (Meldung im Log). Nach dem Eintragen: Neustart.
- Im Log: `RAG-Index FERTIG: N Abschnitte (… aus der RAG-Datei, … neu)`.
- Bei einer Frage: `Lokale Vektor-Suche: N Treffer …`.

---

## Von Hand: `rag-index.sh` / `rag-index.cmd`

Baut das ERP (ohne Tests) und startet den `StandaloneRagIndexer` aus dem fertigen Jar.
Den Schlüssel nur als Umgebungsvariable übergeben, nie als Argument.

```bash
GEMINI_API_KEY=... ./rag-index.sh                  # -> .rag-cache.json
GEMINI_API_KEY=... ./rag-index.sh pfad/datei.json
./rag-index.sh --nur-zaehlen                        # ohne Schlüssel, ohne Kosten
```

Windows: `set GEMINI_API_KEY=...`, dann `rag-index.cmd` mit denselben Argumenten.

Der Altweg unter Windows ohne Docker (`deployment/scripts/update-production.ps1`) kopiert
die `.rag-cache.json` aus dem Repo auf den Server – aber nur im aktuellen Format.
Eine Datei von vor Oktober 2026 meldet er und lässt sie liegen; dann einmal `rag-index.cmd`.

`--nur-zaehlen` zeigt je Bereich, wie viele Abschnitte und Zeichen es gibt und wie viele
neu eingebettet werden müssten. Weitere Option: `--max-minuten N` (Zeitlimit).
Exit-Code: 0 = fertig (auch bei erreichtem Zeitlimit), 1 = Einbetten fehlgeschlagen
(Teilstand gespeichert), 2 = falscher Aufruf oder kein Schlüssel.

Direkt mit dem Jar (so läuft es in GitHub):

```bash
java -cp target/Kalkulationsprogramm-<version>.jar \
  -Dloader.main=org.example.kalkulationsprogramm.cli.StandaloneRagIndexer \
  org.springframework.boot.loader.launch.PropertiesLauncher [projekt-ordner] [rag-datei] [--max-minuten N]
```

---

## Was in die RAG-Datei kommt

In dieser Reihenfolge – was vorne steht, wird zuerst eingebettet:

| Bereich | Quelle |
| --- | --- |
| Doku | `docs/benutzer/`, `docs/*.md` (nur die oberste Ebene), `deployment/docker-server/*.md` |
| Routen | `react-pc-frontend/src/App.tsx` |
| Seiten | `react-pc-frontend/src/pages/`, `react-zeiterfassung/src/pages/` |
| Komponenten | `react-pc-frontend/src/components/`, `react-zeiterfassung/src/components/` |
| Typen | `react-pc-frontend/src/types.ts` |
| Backend | `controller/`, `service/`, `domain/`, `dto/` unter `src/main/java/org/example/kalkulationsprogramm/` |

**Nicht** hinein: Tests (`*Test.java`, `*.test.tsx`, `*.spec.ts` …) sowie `docs/superpowers/`,
`docs/reviews/` und `docs/agent instructions/`. Pläne und Reviews beschreiben auch, was es
(noch) nicht gibt – die KI würde es sonst als vorhanden erklären. Symlinks werden nicht
verfolgt. Werte hinter `password`, `secret`, `api-key`, `token` und `credentials` werden
geschwärzt, auch in Anführungszeichen (`"password": "…"`, `apiKey = "…"`). Das ist ein
einfaches Muster, kein vollständiger Secret-Scanner – Schlüssel gehören ohnehin nie ins
Repository.

Darum lohnt sich gute Doku doppelt: Was in `docs/` steht, erzählt die KI-Hilfe den
Handwerkern weiter.

### Zerlegen

- **Markdown:** an `## `-Überschriften.
- **TSX/TS:** an `export`-Grenzen; **Java:** an Methoden/Feldern (gegen ReDoS gehärteter
  Ausdruck). Kleine Methoden bzw. Exporte werden zu Abschnitten bis 6.000 Zeichen
  zusammengepackt.
- **Zu Langes** wird an Zeilengrenzen in Teile zerlegt („Teil 1/3“) – nichts wird
  abgeschnitten.
- Eingebettet wird jeder Abschnitt mit seinem Dateipfad davor
  (`Datei: react-pc-frontend/src/pages/ProjektEditor.tsx | …`); das hilft der Suche.

### Suchen

- Der Code der Seite, auf der der Nutzer gerade ist, kommt immer mit (bis zu einem
  Drittel der Plätze). Große Seiten bestehen aus vielen Teilen – genommen werden die, die
  am besten zur Frage passen, nicht der Dateianfang. Welche Datei zu welcher Route gehört, steht in `LocalRagService`
  (`ROUTE_TO_FILE_KEYWORDS`); `/emails/inbox/42` findet über `/emails` das E-Mail-Center.
  **Neue Seite im ERP → dort eintragen.**
- Der Rest nach Ähnlichkeit (Kosinus), mindestens 30 % Frontend.
- `ai.rag.top-k` (Standard 15) Abschnitte, Mindestähnlichkeit `ai.rag.score-threshold` (0,3).

---

## Einstellungen

| Schlüssel | Standard | Docker-Profil |
| --- | --- | --- |
| `ai.rag.enabled` | `true` | `true` |
| `ai.rag.datei` | leer = `.rag-cache.json` im Projektordner | `/app/ki-hilfe/rag-cache.json` |
| `ai.rag.projekt-ordner` | Startordner (`user.dir`) | – (dort liegt kein Quellcode) |
| `ai.rag.top-k` | `15` | |
| `ai.rag.score-threshold` | `0.3` | |

Abschalten beim Kunden: `AI_RAG_ENABLED=false` in `deployment/docker-server/app.env`.

---

## Die Datei

```json
{
  "format": 2,
  "modell": "gemini-embedding-001",
  "dimensionen": 768,
  "abschnitte": [
    {
      "filePath": "docs/RECHNUNGSWESEN.md",
      "name": "Rechnung stornieren",
      "category": "documentation",
      "chunkType": "section",
      "contentHash": "1f0c9a…",
      "content": "## Rechnung stornieren …",
      "vector": "<768 float32, Base64>"
    }
  ]
}
```

- Wiedererkannt wird ein Abschnitt an **Pfad + Prüfsumme** (`contentHash` über Pfad und
  Inhalt). Gleicher Text = gleicher Vektor, egal wie er heißt.
- Anderes `format`, `modell` oder andere `dimensionen` → die Datei gilt als leer, alles
  wird neu eingebettet. Wer das Modell wechselt, ändert `RagIndexer.MODELL`.
- Geschrieben wird erst in `….neu`, dann umbenannt – nie eine halbe Datei.
- Größe: rund 25 MB. Nicht committen (`.gitignore`).

Alles neu einbetten lassen: Datei löschen (lokal) bzw. im Workflow *Run workflow* mit
„RAG-Datei komplett neu einbetten“ (siehe oben).

---

## Fehlersuche

| Meldung / Problem | Ursache, Lösung |
| --- | --- |
| `KI-Hilfe: keine RAG-Datei unter /app/ki-hilfe/rag-cache.json` | Image ohne RAG-Datei (Secret fehlte beim ersten Bau). Secret anlegen, nächstes Image abwarten |
| `Lokales RAG: Gemini API Key fehlt` | Entwicklung ohne Schlüssel: unter Einstellungen → KI-Funktionen eintragen, neu starten |
| `Gemini Embedding Fehler 400/403` | Schlüssel ungültig oder für die Embedding-API nicht freigeschaltet |
| `HTTP 429 … neuer Versuch in 20 s` | Beim Image-Bau: Kontingent je Minute erschöpft (Gratis-Schlüssel) – wartet und macht weiter |
| `RAG-Cache hat ein altes Format` (update-production.ps1) | Altweg unter Windows: `.rag-cache.json` im Repo stammt von einer älteren Version. `GEMINI_API_KEY` setzen, `rag-index.cmd` ausführen, Update erneut starten |
| `RAG-Datei … hat ein altes Format oder Modell` | Datei von einer älteren Version – wird neu aufgebaut |
| KI-Hilfe kennt eine neue Seite nicht | Route in `ROUTE_TO_FILE_KEYWORDS` eintragen; Doku in `docs/` ergänzen |

---

## Dateien

| Datei | Zweck |
| --- | --- |
| `service/kihilfe/RagIndexer.java` | Zerlegen, Einbetten (nur Geändertes), RAG-Datei lesen/schreiben |
| `service/kihilfe/GeminiEinbetter.java` | Embeddings über die Gemini-API, Wiederholen bei Überlastung |
| `service/LocalRagService.java` | Laden beim Start, Suche für die KI-Hilfe |
| `service/KiHilfeService.java` | Chat; nutzt die Suche, sonst den Fallback `CodebaseIndexService` |
| `cli/StandaloneRagIndexer.java` | Kommandozeile (Image-Bau, `rag-index.sh`) |
| `cli/RagIndexingRunner.java` | `--rag-index`: ERP starten, warten bis der Index fertig ist, beenden |

`QdrantRagService` und der Qdrant-Container in der Root-`docker-compose.yml` werden vom
ERP nicht genutzt.
