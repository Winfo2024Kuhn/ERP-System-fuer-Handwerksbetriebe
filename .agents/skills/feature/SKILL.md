---
name: feature
description: Vollständiger Workflow für die Implementierung neuer Features im ERP-System.
---

# Feature-Workflow

Führe die folgenden Schritte für jedes neue Feature aus. Überspringe keinen Schritt.

## 0. Sicherheits-Vorabcheck
- [ ] Enthält der bestehende Code in der Nähe API-Keys, Passwörter oder Tokens? → Sofort melden, nicht committen
- [ ] Werden Personendaten (Kunden, Mitarbeiter) verarbeitet? → DSGVO-Anforderungen beachten

## 1. Anforderungen klären
- Akzeptanzkriterien und Business Case prüfen (`docs/BUSINESS_CASES.md`)
- Betroffene Module identifizieren (Backend / `react-pc-frontend` / `react-zeiterfassung`)
- `.github/DEVELOPMENT.md` auf bestehende UI-Patterns und Komponenten prüfen

## 2. Design & Architektur
- Architektur prüfen: `docs/ARCHITEKTUR_UEBERSICHT.md`
- Schichtentrennung einhalten: Controller → Service → Repository → Domain
- DTOs planen (NIEMALS Entities direkt exponieren)
- Bei neuen UI-Patterns: **Zuerst** `.github/DEVELOPMENT.md` aktualisieren

## 3. Datenbankmigrationen (falls nötig)
- Neue Datei: `src/main/resources/db/migration/V{N}__{beschreibung}.sql`
- Versionsnummer aufsteigend (aktuell ab V207+)
- Bestehende Migrationen NIEMALS editieren

## 4. Backend-Implementierung
Reihenfolge einhalten:
1. **Domain-Entity** (`domain/`) mit JPA-Annotationen
2. **Repository** (`repository/`) mit Spring Data JPA
3. **DTO** (`dto/`) für API-Kommunikation
4. **Mapper** (`mapper/`) für DTO ↔ Entity
5. **Service** (`service/`) mit Business-Logik + Transaktionen
6. **Controller** (`controller/`) mit REST-Endpoints

Coding-Regeln:
- Constructor Injection / Lombok `@AllArgsConstructor`
- Parametrisierte Queries (`@Query` mit `:param`), kein String-Concat
- `uploads/`-Pfade immer normalisieren und validieren

## 5. Frontend-Implementierung
- Pflicht-Komponenten verwenden: `Select`, `DatePicker`, `ImageViewer`, `DetailLayout`
- Page Header Pattern: Kategorie-Label (rose-600), Titel (UPPERCASE), Beschreibung (slate-500)
- Farbschema: ausschließlich Rose/Slate – kein indigo/blue
- `npm run build` nach JEDER einzelnen Komponente ausführen (fail fast)

## 6. Tests schreiben
Backend:
- `{Service}Test.java` – Unit-Test mit `@ExtendWith(MockitoExtension.class)`
- `{Controller}Test.java` – REST-Test mit `@WebMvcTest`
- Test-Daten: NUR Dummy-Daten (`test@example.com`, `Max Mustermann`)

Security-Tests (bei jedem neuen Endpoint Pflicht):
- SQL Injection: `'; DROP TABLE x; --`
- XSS: `<script>alert(1)</script>`
- Ungültige IDs: negative Werte, `Long.MAX_VALUE`, `0`
- Leere/überlange Eingaben (> 10.000 Zeichen)
- Bei Datei-Endpoints: Path-Traversal + gefährliche Dateitypen

Frontend:
- `{Komponente}.test.tsx` neben der Quell-Datei (Vitest + Testing Library)

## 7. Qualitätssicherung
- [ ] `./mvnw test` – alle Tests grün
- [ ] `cd react-pc-frontend && npm run build` – kein Kompilierfehler
- [ ] `cd react-zeiterfassung && npm run build` – falls betroffen
- [ ] `git diff --staged` prüfen: Keine API-Keys, keine Passwörter, keine echten Nutzerdaten
- [ ] Kein `application-local.properties` gestaged

## 8. Commit & PR
- Branch: `feat/{kurzbeschreibung}`
- Commit: imperativer Betreff auf Englisch, Body mit Begründung
- PR: Summary + verlinkte Issues + Verifikationsschritte
