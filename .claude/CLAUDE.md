# CLAUDE.md – ERP-System für Handwerksbetriebe

## Projekt-Überblick

Vollständiges ERP-System für Handwerksbetriebe mit:
- **Backend:** Spring Boot 3.2.5, Java 23, MySQL 8, Flyway, JPA/Hibernate
- **Desktop-Frontend:** React 18 + TypeScript + Vite + Tailwind CSS (`react-pc-frontend/`)
- **Mobile-Frontend:** React PWA + IndexedDB für Zeiterfassung (`react-zeiterfassung/`)
- **Backend-Port:** 8082 | **API-Basis:** `http://localhost:8082/api`

---

## SICHERHEIT – ABSOLUT VERBOTEN (nie ignorieren)

### API-Keys & Secrets
- **NIEMALS** API-Keys, Passwörter, Tokens oder Zugangsdaten in Code oder Commits schreiben
- Lokale Overrides ausschließlich in `application-local.properties` (in `.gitignore` eingetragen)
- Vor jedem Commit: `git diff --staged` prüfen – kein Key, kein Passwort sichtbar?
- Verdächtige Patterns im Code sofort melden: `api_key`, `password=`, `token=`, `secret=`
- Falls ein Key versehentlich committed wurde: **sofort rotieren** – Git-History reicht nicht

### Personenbezogene Daten (DSGVO)
- Kundendaten, Mitarbeiterdaten und Zeitbuchungen sind **personenbezogen** (DSGVO Art. 4)
- In Tests **keine echten Namen, E-Mails oder Adressen** verwenden – immer Dummy-Daten: `test@example.com`, `Max Mustermann`, `Musterstraße 1`
- Logs dürfen **keine** personenbezogenen Daten enthalten (keine Namen, IDs nur wenn notwendig)
- `uploads/` enthält ggf. Rechnungen mit Personendaten – **nie versionieren** (gitignored)
- Bei Datenbankdumps für Tests: Daten **anonymisieren** bevor sie ins Repo kommen

### Git-Sicherheitsregeln
```
# Diese Dateien NIEMALS committen:
application-local.properties   # DB-Credentials, API-Keys
*.env                          # Umgebungsvariablen
uploads/                       # Nutzerdateien (Rechnungen, Bilder)
*.key, *.pem, *.p12           # Zertifikate/Schlüssel
```

---

## Build & Run

```bash
# Backend
./mvnw clean package          # Kompilieren + Tests + JAR
./mvnw spring-boot:run        # Starten (Port 8082)
./mvnw test                   # Tests ausführen

# Desktop-Frontend
cd react-pc-frontend && npm run build   # IMMER nach Frontend-Änderungen!
cd react-pc-frontend && npm run dev     # Dev-Server

# Mobile-Frontend
cd react-zeiterfassung && npm run build
cd react-zeiterfassung && npm run dev

# Windows-Installer
./mvnw jpackage:jpackage
```

---

## Package-Struktur (Backend)

```
org.example.kalkulationsprogramm/
├── controller/    # REST-Endpoints (53 Controller)
│   └── advice/   # RestExceptionHandler
├── service/       # Business-Logik (84 Services)
├── repository/    # Spring Data JPA (75+ Repos)
├── domain/        # JPA-Entities + Enums (109 Entities)
├── dto/           # Data-Transfer-Objects
├── mapper/        # DTO ↔ Entity Mapper
└── config/        # Spring-Konfiguration

org.example.email/  # E-Mail-System (IMAP/SMTP)
```

---

## Kritische Coding-Regeln

### Backend
- **Schichtentrennung:** Controller → Service → Repository → Domain. Keine Logik im Controller.
- **Constructor Injection** für Spring-Beans; Lombok `@AllArgsConstructor` erlaubt.
- **Niemals Entities direkt exponieren** – immer DTOs verwenden.
- **SQL:** Ausschließlich parametrisierte Queries (`@Query` mit `:param`), kein String-Concat.
- **Flyway:** Neue Skripte unter `src/main/resources/db/migration/V{N}__{beschreibung}.sql` (aufsteigend ab V207+). Bestehende Migrationen NIEMALS ändern.

### Frontend
- **Nach JEDER Änderung:** `npm run build` ausführen (fail fast, nicht am Ende).
- **Kein `dangerouslySetInnerHTML`** ohne serverseitig sanitisierte Daten (`EmailHtmlSanitizer`).
- **URL-Parameter:** immer `encodeURIComponent()` verwenden.
- **Komponenten-Hierarchie:** `src/components/ui/` für Atome, `src/features/{name}/` für Domänenlogik.

---

## UI-Design-System

### Farbschema: Rose/Rot (ZWINGEND – kein indigo/blue)
- Primärfarbe: `#dc2626` (rose-600) | Palette: `rose-50`–`rose-900` + `slate-50`–`slate-900`

### Button-Klassen
| Typ | CSS |
|-----|-----|
| Primär | `bg-rose-600 text-white border border-rose-600 hover:bg-rose-700` |
| Sekundär | `border-rose-300 text-rose-700 hover:bg-rose-50` |
| Ghost | `variant="ghost"` + `text-rose-700 hover:bg-rose-100` |
| Größe | `size="sm"` Standard; Icons `w-4 h-4` links vom Text (Lucide React) |

### Pflicht-Komponenten (NIE neu implementieren)
| Komponente | Pfad | Ersetzt |
|-----------|------|---------|
| `Select` | `src/components/ui/select-custom.tsx` | HTML `<select>` |
| `DatePicker` | `src/components/ui/datepicker.tsx` | `<input type="date">` |
| `ImageViewer` | `src/components/ui/image-viewer.tsx` | Custom Fullscreen |
| `DetailLayout` | `src/components/DetailLayout.tsx` | 2-Spalten-Layout |
| `EmailHistory` | `src/components/EmailHistory.tsx` | Inline E-Mail-Listen |
| `GoogleMapsEmbed` | `src/components/GoogleMapsEmbed.tsx` | Inline Karten |
| `DocumentPreviewModal` | `src/components/DocumentPreviewModal.tsx` | `<iframe src={url}>` für PDFs – löst X-Frame-Options |

### Page Header Pattern (alle Seiten)
```tsx
<div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
  <div>
    <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">Kategorie</p>
    <h1 className="text-3xl font-bold text-slate-900">SEITENTITEL</h1>
    <p className="text-slate-500 mt-1">Beschreibung</p>
  </div>
  <div className="flex gap-2">{/* Buttons */}</div>
</div>
```

---

## Test-Anforderungen

| Schicht | Framework | Annotation |
|---------|-----------|------------|
| Service | JUnit 5 + Mockito | `@ExtendWith(MockitoExtension.class)` |
| Controller | MockMvc | `@WebMvcTest` |
| Repository | H2 In-Memory | `@DataJpaTest` |
| Frontend | Vitest + Testing Library | neben Quell-Datei als `*.test.tsx` |

Test-Daten: **immer Dummy-Daten** (`test@example.com`, `Max Mustermann`) – keine echten Nutzerdaten.

Coverage-Ziele: Services ≥ 80%, Controller alle Endpoints (Happy-Path + Fehlerfall), Utils 100%.

---

## Sicherheits-Pflichtcheckliste (neue Endpoints)

- SQL Injection: `'; DROP TABLE x; --` in allen String-Parametern testen
- XSS: `<script>alert(1)</script>` in allen Textfeldern testen
- Ungültige IDs: negative Werte, `Long.MAX_VALUE`, `0`
- Leere Pflichtfelder + überlange Eingaben (> 10.000 Zeichen)
- Datei-Endpoints: Path-Traversal (`../../etc/passwd`) + gefährliche Typen (`.exe`, `.bat`)

---

## Feature-Flags (application.properties)

Bestimmte Module sind über Feature-Flags steuerbar und standardmäßig **deaktiviert**. Bei Änderungen an diesen Modulen immer im Hinterkopf behalten, dass das Feature im laufenden System deaktiviert sein kann:

| Flag | Modul | Hinweis |
|------|-------|---------|
| `en1090.features.enabled=false` | EN 1090 Werkstattproduktionskontrolle (WPK) | Schaltet den EN-1090-Tab, EXC-Klassen-UI und WPK-Status komplett ab |
| `echeck.features.enabled=false` | E-Check (Betriebsmittelprüfung) | Auch aktiv wenn EN 1090 aktiviert ist; unabhängig schaltbar |
| `email.features.enabled=false` | E-Mail-Import, Cleanup, Vendor-Abruf (Scheduled Tasks) | |
| `ai.email.enabled=false` | KI-gestützte E-Mail-Analyse | |

> **Pflicht bei EN-1090-Änderungen:** Frontend-Code, der EN-1090-Funktionalität enthält, muss den `features.en1090`-Flag aus `useFeatures()` auswerten – niemals EN-1090-Inhalte ohne Guard rendern. Backend-Endpoints müssen ebenfalls den Flag prüfen (`@ConditionalOnProperty` oder manuell in Service).

---

## Architektur-Patterns

| Pattern | Einsatz |
|---------|---------|
| Audit-Trail (Snapshots) | GoBD: `ZeitbuchungAudit`, vollständige Snapshots |
| Dokumentketten | Angebote → Aufträge → Rechnungen (Vorgänger/Nachfolger) |
| MonatsSaldo-Caching | Vergangene Monate gecacht, aktueller Monat live |
| Datei-Deduplizierung | `LieferantDokument` → FK auf `EmailAttachment` |
| Enum State-Management | Typsichere Dokumenttypen, Mahnstufen, Audit-Aktionen |
| **ML Spam-Filter (Naive Bayes)** | Supervised Multinomial Naive Bayes in reinem Java. Ensemble: 40% Regel-Score (`SpamFilterService`) + 60% Bayes-Score (`SpamBayesService`). Token-Frequenzen in `spam_token_counts`-Tabelle, In-Memory-Cache mit 5-Min-Refresh. Cold-Start: nur Regeln (<20 Samples). Pre-trained mit UCI SMS Spam Collection (747 Spam, 4827 Ham). User-Feedback über `mark-spam`/`mark-not-spam` Endpoints trainiert das Modell supervised weiter. |

---

## Externe Dienste (Konfiguration in properties, NIE im Code)

| Dienst | Property-Prefix |
|--------|----------------|
| Google Gemini AI | `ai.gemini.*` |
| IMAP/SMTP E-Mail | `spring.mail.*` |
| Qdrant Vector DB | `ai.rag.*` (+ `docker-compose.yml`) |
| MySQL | `spring.datasource.*` (in `application-local.properties`) |

---

## Dokumentations-Update-Pflicht

Bei neuen UI-Patterns oder Komponenten:
1. **Zuerst** `.github/DEVELOPMENT.md` aktualisieren
2. **Dann** implementieren

Master-Referenz: [`.github/DEVELOPMENT.md`](../.github/DEVELOPMENT.md)
