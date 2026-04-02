# AGENTS.md – ERP-System für Handwerksbetriebe

Agent-Workflow-Regeln für dieses Projekt. Vollständige Coding- und Design-Regeln → `.claude/CLAUDE.md`.

---

## Orientierung vor jeder Aufgabe

1. `.claude/CLAUDE.md` lesen – Pflicht-Komponenten, Design-System, Coding-Regeln
2. `.github/DEVELOPMENT.md` lesen – Button-API, DatePicker-API, vollständige Security-Checkliste
3. Betroffene Dateien lesen, bevor Änderungen vorgeschlagen werden
4. **Security-Check:** Enthält der bestehende Code Secrets oder Personendaten? → Sofort melden

---

## Absolut verbotene Aktionen

| Verboten | Warum |
|----------|-------|
| API-Keys, Tokens, Passwörter im Code | Landen in Git-Historie, nicht rückgängig zu machen |
| Echte Kundendaten / Mitarbeiterdaten in Tests | DSGVO-Verstoß |
| `application-local.properties` committen | Enthält DB-Credentials |
| Entities direkt in REST-Responses | Immer DTOs verwenden |
| String-Concat in JPQL/SQL | SQL Injection |
| `dangerouslySetInnerHTML` ohne Sanitisierung | XSS |
| `npm run build` überspringen | Fehler bleiben unentdeckt |
| Flyway-Migrationen nachträglich ändern | Bricht alle Deployments |
| Indigo/Blue/andere Akzentfarben im UI | Design-Inkonsistenz |
| Native `<select>` oder `<input type="date">` | Pflicht-Komponenten verwenden |
| **Externe URL direkt als `<iframe src>`** | X-Frame-Options bricht die Vorschau → `DocumentPreviewModal` nutzen |

---

## Code-Wiederverwendung (DRY)

**Regel:** Bevor eine neue Komponente, Funktion oder ein Modal implementiert wird, immer prüfen ob es bereits etwas Ähnliches gibt.

```
1. Suche in src/components/ nach ähnlichen Komponenten
2. Suche in src/pages/ nach lokalen Hilfsfunktionen die mehrfach vorkommen
3. Wenn Code ≥ 2× fast identisch vorkommt → in shared component/util auslagern
4. Shared components nach src/components/ (nicht in features/ oder pages/)
```

**Wann auslagern:**
- Gleiche JSX-Struktur an ≥ 2 Stellen → shared component
- Gleiche Logik (fetch, transform, validate) an ≥ 2 Stellen → shared hook oder util
- Gleicher Modal-Aufbau → bestehenden Modal erweitern, nicht neuen bauen

**Warum:** Konsistentes Design, ein Fix gilt überall, leichter zu verstehen.

**Bekannte Pflicht-Komponenten** (nie neu implementieren → CLAUDE.md Tabelle):
`Select`, `DatePicker`, `ImageViewer`, `DetailLayout`, `EmailHistory`, `GoogleMapsEmbed`, `DocumentPreviewModal`

---

## PDF-Vorschau: Pflicht-Komponente `DocumentPreviewModal`

**Problem:** Externe Dokument-URLs (Tailscale, S3, CDN) setzen `X-Frame-Options: deny`.
Der Browser verweigert das Einbetten im `<iframe>` → weißes Bild / `chrome-error://chromewebdata`.

**Regel: Niemals eine URL direkt als `<iframe src>` verwenden.**

```tsx
// FALSCH – bricht bei externen URLs
<iframe src={dokument.url} />
```

**Immer `DocumentPreviewModal` aus `src/components/DocumentPreviewModal.tsx` nutzen:**

```tsx
import DocumentPreviewModal, { type PreviewDoc } from '../components/DocumentPreviewModal';

const [previewDoc, setPreviewDoc] = useState<PreviewDoc | null>(null);

// Öffnen (url + title angeben)
<button onClick={() => setPreviewDoc({ url: dokument.url, title: dokument.name })}>
    PDF ansehen
</button>

// Rendern
{previewDoc && (
    <DocumentPreviewModal doc={previewDoc} onClose={() => setPreviewDoc(null)} />
)}
```

**Warum das funktioniert:** Die Komponente fetcht das PDF als `Blob` und erzeugt eine
`blob://`-URL. Diese ist immer same-origin → X-Frame-Options greift nicht.
Download-Button nutzt die Original-URL (X-Frame-Options gilt nicht für `<a href>`).

**Ausnahmen (kein `DocumentPreviewModal` nötig):**

| Komponente | Grund |
|-----------|-------|
| `PdfCanvasViewer` | Nutzt intern bereits fetch + Blob (PDF.js) |
| `LieferantDokumentImportModal` | Lokaler File-Upload → blob-URL aus `File`-Objekt |
| `AusgangsrechnungUploadModal` | Gleicher Fall |
| `GoogleMapsEmbed` | Google Maps – kein Dokument |
| `EmailContentFrame` | Email-HTML via `srcdoc` – keine externe URL |

---

## Pflicht-Workflow Backend

1. Betroffene Controller/Service/Repository/Entity lesen
2. Schichtentrennung: Controller → Service → Repository → Domain
3. DTOs verwenden (niemals Entities direkt zurückgeben)
4. Parametrisierte Queries (`@Query` mit `:param`)
5. Neue Endpoints: Security-Checkliste aus CLAUDE.md abarbeiten
6. Tests schreiben (`{Klasse}Test.java` Unit, `{Klasse}IT.java` Integration)
7. `./mvnw test` ausführen – muss grün sein

## Pflicht-Workflow Frontend

1. Pflicht-Komponenten prüfen (Select, DatePicker, ImageViewer, DetailLayout…)
2. Farbschema: Rose/Slate – kein indigo/blue
3. Page Header Pattern auf neuen Seiten anwenden
4. `npm run build` nach JEDER Änderung
5. Bei neuen UI-Patterns: `.github/DEVELOPMENT.md` zuerst aktualisieren

## Pflicht-Workflow Datenbankmigrationen

1. Neue Datei: `src/main/resources/db/migration/V{N}__{beschreibung}.sql`
2. Versionsnummer aufsteigend (aktuell ab V207+)
3. Bestehende Migrationen NIEMALS editieren

---

## Aufgabenmuster: Full-Stack Feature

```
1.  Flyway-Migration erstellen
2.  Domain-Entity  (domain/)
3.  Repository     (repository/)
4.  DTO            (dto/)
5.  Mapper         (mapper/)
6.  Service        (service/)
7.  Controller     (controller/)
8.  Tests          (service + controller)
9.  Frontend-Komponente mit Design-System
10. ./mvnw test + npm run build
```

## Aufgabenmuster: Bugfix

```
1. Betroffene Datei(en) vollständig lesen
2. Ursache verstehen (nicht blind fixen)
3. Minimale Änderung – kein Refactoring nebenbei
4. Regressionstest schreiben
5. Build verifizieren
```

## Aufgabenmuster: Neue React-Seite

```
1. Page Header Pattern anwenden (Kategorie aus DEVELOPMENT.md)
2. Pflicht-Komponenten importieren
3. Rose-Farbschema einhalten
4. npm run build ausführen
```

---

## Test-Daten-Regel (DSGVO)

Tests dürfen **ausschließlich** diese Dummy-Daten verwenden:
- E-Mail: `test@example.com`, `max.mustermann@example.com`
- Name: `Max Mustermann`, `Erika Musterfrau`
- Adresse: `Musterstraße 1, 12345 Musterstadt`
- Telefon: `+49 30 12345678`
- IBAN: `DE89370400440532013000` (offizielle Test-IBAN)

---

## Verfügbare Slash Commands

| Command | Zweck |
|---------|-------|
| `/feature` | Vollständiger Workflow für neue Features |
| `/bugfix` | Schritt-für-Schritt Bugfix-Prozess |
| `/pre-merge` | Pre-Merge-Checkliste vor Pull Requests |
| `/security-audit` | Security-Review eines Endpoints oder einer Komponente |
| `/new-page` | Checkliste für neue React-Seiten |
