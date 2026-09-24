# Beschaffung EN1090 Implementation Plan

> **For agentic workers:** Use subagent-driven-development for independent backend and component tasks. Preserve other workers' edits. Steps use checkboxes.

**Goal:** Den vollständigen Bedarf–Werkstatt–Bestellung-Ablauf mit den bestehenden EN1090-Komponenten nutzbar machen.
**Architecture:** EN1090-Seiten und Materialdialog wiederverwenden; aktuelle Einkaufs-Daten und Bestellabwicklung behalten. Werkstatt-Rückmeldung persistieren, freie Positionen zulassen, offene Preise ausdrücklich abbilden.
**Tech Stack:** React/TypeScript, Spring Boot/JPA, OpenPDF, Vitest, Playwright.
**Spec:** `docs/superpowers/specs/2026-09-24-beschaffung-en1090-ablauf.md`

## Global Constraints

- Vorhandene Fremdänderungen erhalten. Baseline: `/tmp/erp-beschaffung-en1090-baseline`.
- EN1090-Branchkomponenten übernehmen statt eine neue Bedienidee zu erfinden.
- Deutsche Dezimaleingaben mit Textentwürfen, gemeinsame UI-Komponenten, Rose/Slate.
- Keine Lagerverwaltung. Unbekannte Preise bleiben `null`.
- Kein Commit vor vollständigen grünen Prüfungen und Code-Review.

## Task 1: Backend für Werkstatt und freie Bestellungen

Files: EinkaufBedarfController/Service/Repository/Dto, EinkaufPositionService und Positionsart, EinkaufBestellungService und abhängige Versand/PDF/Kostenpfade, passende Tests.

Interfaces: `GET /api/einkauf/bedarf?projektId=ID` und `?ohneProjekt=true`; `PUT /api/einkauf/bedarf/werkstattpruefung` mit `{positionen:[{bedarfId,version,vorhanden}]}` liefert aktuelle `BedarfResponse[]`; `POST /api/einkauf/bedarf/pdf` mit `{bedarfIds:[...]}` liefert die druckbare Liste ohne lange URLs; GET bleibt kompatibel. `FREITEXT` erweitert Positionsart. Direktbestellung akzeptiert freie Positionen und fehlende Einträge in `preise` als offene Preise.

- [x] Regressionstests: 10 benötigt, 4 vorhanden, 6 bestellbar; Korrektur auf 3 vorhanden; veraltete Version und überhöhte Mengen werden atomar abgelehnt.
- [x] Bestehende Bedarfs-/Bestellservices mit diesen Schnittstellen erweitern. Freitext braucht Bezeichnung, positive Menge und Einheit; Projekt bleibt optional.
- [x] Offene Preise durch Entwurf, Freigabe, PDF, Versand und spätere Auswertung erhalten. Keine erfundene Nullsumme.
- [ ] Backend-Prüfungen ausführen und Ergebnisse dokumentieren.

## Task 2: EN1090-Materialdialog anschließen

Files: `react-pc-frontend/src/components/MaterialbestellungModal.tsx`, zugehöriger Adapter unter `features/einkauf/`, Komponenten-Tests.

Interfaces: Branch-Props `isOpen`, `onClose`, `onSuccess`, `initialProjekt`, `projektSperren` erhalten. Neue Props `ausgangsbedarf?: BedarfResponse`, `ohneProjekt?: boolean` verbinden aktuelle Daten. Speichern nutzt `/api/einkauf/bedarf`, freie Positionen `art:'FREITEXT'`.

- [x] Materialdialog aus `feature/en1090-echeck` übernehmen und bestehende Auswahldialoge wiederverwenden.
- [x] Katalog- und Freitexteingabe sowie Projekt/vorratsbezogenes Speichern testen und anschließen. Dezimaleingaben bleiben während der Bearbeitung Text.
- [x] Keine alten `/api/bestellungen/manuell`-Schreibzugriffe mehr aus dem Bedarf. Fehler zeigen Toasts und erhalten Eingaben.

## Task 3: Projektübersicht und Werkstatttabelle

Files: `pages/BedarfUebersichtPage.tsx`, `pages/ProjektBedarfPage.tsx`, `App.tsx`, Bedarfs-API-Hilfen, E2E-Spec.

- [x] Die beiden Seiten aus `feature/en1090-echeck` übernehmen.
- [x] Übersicht an aktuelle paginierte Bedarfe anschließen. Bereich ohne Projekt immer erreichbar machen. Projektauswahl führt direkt in dessen Liste.
- [x] Tabelle zeigt benötigte/vorhandene/fehlende Mengen und Bestellstände. `vorhanden` als Dezimalentwurf validieren und über Task-1-Schnittstelle speichern.
- [x] Druckliste und ausgewählte Fehlmengen an aktuelle Direktbestellung übergeben. Optionalen Anfrageweg erhalten.
- [x] E2E: Projekt und Vorrat, Speichern/Neuladen, Teilmenge, Druck, Bestellung, keine doppelte Übernahme; alle drei Desktop-Größen prüfen.

## Task 4: Bestelloberfläche und Abschluss

Files: `features/einkauf/components/DirektbestellungDialog.tsx`, `pages/EinkaufBestellungDetail.tsx` und betroffene Preisansichten, Tests.

- [x] Vorausgewählte Bedarfe vollständig per ID laden; keine Begrenzung auf erste 100 Datensätze.
- [x] Preise optional erfassen, fehlende Preise aus dem Direktpreis-Paket weglassen. Freie Bedarfe zulassen.
- [x] Offene Preise in Detail/Freigabe sichtbar benennen. Test: fehlender Preis wird nicht als 0 € dargestellt.
- [ ] Lint, Frontend-Unit/E2E/Build und Backend-Suite ausführen. Screenshots ansehen; Reviewer-Findings beheben.
- [ ] Eigene Diffs gegen Baseline prüfen und Graphify einmal aktualisieren. Nur eigene Änderungen shippen, wenn alle Pflichtprüfungen grün sind.
