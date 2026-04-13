---
name: en1090-erweiterung
description: "EN 1090 Qualifikationsmodul erweitern im ERP-System für Handwerksbetriebe. Use for: neue EN-1090-Features implementieren, EXC-Klassen Logik anpassen, WPK-Status erweitern, E-Check-Prüfungen hinzufügen, Schweißer-Zertifikate, WPS, Werkstoffzeugnisse, Mobile E-Check-Seiten, ProjektEditor EN-1090-Tab, Feature-Flag Guards prüfen, Flyway-Migrationen für EN-1090-Tabellen. Do NOT use for: allgemeine ERP-Funktionen ohne EN-1090-Bezug, UI-Design-Fragen (→ ui-ux-pro-max skill)."
argument-hint: "Was soll erweitert werden? z.B. 'Mobile E-Check-Seiten', 'WPS-Formular', 'Schweißer-Warnung'"
---

# EN 1090 Erweiterungen — Skill

Schritt-für-Schritt-Anleitung für alle Erweiterungen am EN-1090/E-Check-Modul dieses ERP-Systems.

> **UI-Design:** Für alle Frontend-Komponenten dieses Moduls den Skill [`ui-ux-pro-max`](../ui-ux-pro-max/SKILL.md) hinzuziehen (rose/slate Farbschema, Button-Klassen, Page-Header-Pattern).

---

## Pflichtcheck vor jeder Änderung

1. **Feature-Flag beachten** — EN 1090 ist standardmäßig deaktiviert:
   ```properties
   en1090.features.enabled=false   # in application.properties
   echeck.features.enabled=false
   ```
2. **Frontend-Guard** — Jede EN-1090-Seite/-Komponente MUSS den Flag auswerten:
   ```tsx
   const { features } = useFeatures(); // react-pc-frontend/src/hooks/useFeatures.ts
   if (!features.en1090) return null;
   ```
3. **Backend-Guard** — Controller/Service mit `@ConditionalOnProperty` oder manuallem Check absichern.
4. **EXC-Klassen**: Nur `null` (Keine), `EXC_1`, `EXC_2` existieren im System (EXC_3/4 wurden entfernt).

---

## Architektur-Überblick

```
Backend (Spring Boot, Port 8080):
  controller/   En1090Controller, BetriebsmittelController, SchweisserZertifikatController
                WpsController, WerkstoffzeugnisController, FeatureFlagController
  service/      BetriebsmittelService, En1090ReportService
  domain/       Betriebsmittel, BetriebsmittelPruefung, SchweisserZertifikat, Wps, Werkstoffzeugnis
  API-Basis:    /api/en1090/**, /api/betriebsmittel/**, /api/schweisser-zertifikate/**
                /api/wps/**, /api/werkstoffzeugnisse/**

Desktop-Frontend (react-pc-frontend/src/):
  pages/        WpkDashboardPage.tsx, BetriebsmittelPage.tsx, SchweisserZertifikatePage.tsx
                WpsPage.tsx, WerkstoffzeugnissePage.tsx, ProjektEditor.tsx (EN-1090-Tab)
  hooks/        useFeatures.ts  ← IMMER verwenden für Feature-Flag

Mobile-Frontend (react-zeiterfassung/src/):
  Geplant:      /betriebsmittel, /betriebsmittel/scan, /betriebsmittel/:id/pruefung
```

---

## WPK-Status-System

Der `En1090ReportService` berechnet für ein Projekt einen Ampel-Status:

```java
// WpkStatus Felder (je OK | WARNUNG | FEHLER):
schweisser          + schweisserHinweis
wps                 + wpsHinweis
werkstoffzeugnisse  + werkstoffzeugnisseHinweis
echeck              + echeckHinweis
```

API-Aufruf: `GET /api/en1090/wpk/{projektId}`

---

## EXC-Klassen — Anforderungsmatrix

| Anforderung | EXC 1 | EXC 2 |
|------------|-------|-------|
| Schweißer-Qualifikation | Empfohlen (EN ISO 9606-1) | **Pflicht** (3-Jahres-Erneuerung) |
| WPS | Empfohlen (pWPS) | **Pflicht** (EN ISO 15614-1) |
| Werkstoffzeugnis | Pflicht (2.1 bei tragenden Teilen) | **Pflicht** (3.1 EN 10204, Schmelzerzeugnis) |
| E-Check DGUV V3 | Pflicht | **Pflicht** (dokumentationspflichtig) |
| WPK-Überwachung | Intern ausreichend | **Externe Zertifizierung + Notified Body** |
| Schweißaufsicht | Optional | **Pflicht** (IWE/IWT EN ISO 14731) |

---

## Procedure: Neue Backend-Entität/API hinzufügen

1. **Domain**: `src/main/java/.../domain/` — neue Entity mit `@Entity`, `@Table`
2. **Repository**: `src/main/java/.../repository/` — Interface extends `JpaRepository`
3. **Service** (optional):  `BetriebsmittelService` als Vorlage nutzen (Intervallberechnung, Fälligkeitscheck)
4. **Controller**: Endpunkt mit `@RestController`, `@RequestMapping("/api/...")`, **kein** `@CrossOrigin` (globale WebConfig!)
5. **Flyway**: `src/main/resources/db/migration/V{N}__beschreibung.sql` — nächste freie Nummer ab V214+
6. **Build & Test**: `./mvnw test` — alle 93+ EN-1090-Tests müssen grün bleiben

---

## Procedure: Neue Desktop-Seite (EN 1090)

1. **Feature-Guard** zuerst:
   ```tsx
   const { features } = useFeatures();
   if (!features.en1090) return <Navigate to="/" />;
   ```
2. **Page-Header** nach CLAUDE.md-Pattern (rose-600 Akzent, slate-900 Titel)
3. **Routing**: Route in `react-pc-frontend/src/App.tsx` eintragen
4. **Navigation**: `RibbonNav.tsx` → EN-1090-Kategorie — nur wenn Feature aktiv
5. **UI-Referenz**: [ui-ux-pro-max Skill](../ui-ux-pro-max/SKILL.md) für Komponenten, Karten, Buttons
6. **Build**: `cd react-pc-frontend && npm run build` — IMMER danach ausführen

---

## Procedure: Mobile E-Check-Seiten (größter offener Block)

Ziel-Routen in `react-zeiterfassung/src/App.tsx`:

| Route | Komponente | Funktion |
|-------|-----------|---------|
| `/betriebsmittel` | `BetriebsmittelListePage` | Liste fälliger Geräte, Dashboard-Kachel "X fällig" |
| `/betriebsmittel/scan` | `BetriebsmittelScanPage` | Barcode-Scanner via `html5-qrcode` (bereits installiert) |
| `/betriebsmittel/:id/pruefung` | `BetriebsmittelPruefungPage` | Formular: Sichtprüfung, Messwerte, Bestanden/Nicht bestanden |

Auth-Pattern: `?token=` Query-Parameter (wie andere Mobile-Seiten).

API-Calls:
- `GET /api/betriebsmittel?faellig=true` — fällige Geräte
- `GET /api/betriebsmittel/{id}` — Gerätedetail
- `POST /api/betriebsmittel/{id}/pruefungen` — neue Prüfung anlegen

---

## Procedure: ProjektEditor EN-1090-Tab erweitern

Tab ist bereits implementiert (`activeTab === 'en1090'`). Bei Erweiterungen:

1. EXC-Klasse aus `projekt.excKlasse` auslesen
2. Für `EXC_2`: Live-WPK-Status via `GET /api/en1090/wpk/{projektId}` laden
3. Statusanzeige: `OK` → grün, `WARNUNG` → gelb/amber, `FEHLER` → rot/rose
4. "Aktualisieren"-Button nur für EXC_2 anzeigen

---

## Offene Erweiterungen (nach Priorität)

| # | Task | Aufwand | Abhängigkeiten |
|---|------|---------|---------------|
| 1 | Mobile E-Check komplett (#6) | 2–3 h | — |
| 2 | ZeitbuchungService Zertifikat-Hook (#3) | 1 h | — |
| 3 | Mobile Schweißer-Warnung (#7) | 0.5 h | Punkt 2 |
| 4 | GeminiDokumentAnalyse Werkstoffzeugnis-Extraktion (#4) | 1 h | — |
| 5 | Mobile Werkstoffzeugnis-Panel (#8) | 0.5 h | Punkt 4 |
| 6 | BestellungEditor Werkstoffzeugnis-Tabs (#9) | 0.5 h | — |
| 7 | Service-Klassen Refactoring (#2) | optional | — |

Detaillierter Status: [`/memories/repo/en1090-implementation-status.md`](../../../../memories/repo/en1090-implementation-status.md)

---

## Test-Pattern (EN 1090)

```java
@ExtendWith(MockitoExtension.class)
class BetriebsmittelServiceTest {
    @Mock BetriebsmittelRepository repo;
    @InjectMocks BetriebsmittelService service;
    // Dummy-Daten: "Testgerät", Serial "TEST-001"
}

// Controller-Tests:
@WebMvcTest(En1090Controller.class)
@AutoConfigureMockMvc(addFilters = false) // Security deaktivieren für Unit-Tests
```

Coverage-Ziel: Services ≥ 80% (aktuell 93 Tests grün).

---

## Wichtige Dateipfade

| Datei | Zweck |
|-------|-------|
| `react-pc-frontend/src/hooks/useFeatures.ts` | Feature-Flag Hook |
| `react-pc-frontend/src/pages/WpkDashboardPage.tsx` | Referenz: Ampel-Pattern, StatusCard/StatusBadge |
| `react-pc-frontend/src/pages/ProjektEditor.tsx` | EN-1090-Tab (activeTab === 'en1090') |
| `src/main/java/.../service/En1090ReportService.java` | WPK-Status Berechnung |
| `src/main/java/.../controller/En1090Controller.java` | `/api/en1090/wpk/{projektId}` |
| `src/main/resources/db/migration/` | Flyway Migrationen (V212, V213 exist) |
| `src/main/resources/application.properties` | Feature-Flags (Zeilen 88–91) |
