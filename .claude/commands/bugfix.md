---
name: bugfix
description: Schritt-für-Schritt Workflow für Bugfixes im ERP-System.
---

# Bugfix-Workflow

## 0. Sicherheits-Vorabcheck
- [ ] Enthält der betroffene Code API-Keys oder Passwörter, die versehentlich im Code stehen?
- [ ] Betrifft der Bug personenbezogene Daten (Leak, falsche Zugriffskontrolle)?
- [ ] Betrifft der Bug GoBD-relevante Daten (Zeitbuchungen, Rechnungen, Audit-Trail)?

## 1. Reproduzieren & Isolieren
- Exakte Schritte zur Reproduktion dokumentieren
- Betroffene Komponente(n) identifizieren
- Logs prüfen falls vorhanden (`logs/` oder Spring Boot Output)
- `git log --oneline -20` – wann wurde das Problem eingeführt?

## 2. Ursachenanalyse
- Betroffene Datei(en) **vollständig lesen** – nicht nur die fehlerhafte Zeile
- Ursache verstehen und dokumentieren, bevor etwas geändert wird
- Prüfen: Ist das ein einzelner Bug oder ein Muster?

## 3. Fix implementieren
- **Minimale Änderung** – kein Refactoring, keine Cleanup-Aktivitäten nebenbei
- Schichtentrennung einhalten (keine Logik in Controller schieben)
- Bei SQL-Bugs: Parametrisierte Queries prüfen
- Bei Frontend-Bugs: Pflicht-Komponenten korrekt verwendet?

## 4. Regressionstest schreiben
- Test, der den Bug reproduziert und jetzt grün ist
- Testname beschreibt das Problem: z.B. `gibtFehlerZurueckWennDatumNullIst()`
- Nur Dummy-Daten in Tests (`test@example.com`, `Max Mustermann`)
- **Backend:** JUnit-Test in passender Schicht (`@WebMvcTest`, `@ExtendWith(MockitoExtension.class)`)
- **Frontend:** Vitest-Test als `*.test.tsx` neben der Quell-Datei
  - Utility-/Pure-Functions (z.B. `replacePlaceholders`) direkt mit `import` testen
  - Komponenten mit `@testing-library/react` rendern und DOM-Ausgabe prüfen
  - Mindestens: Bug-Szenario (vorher fail) + Happy-Path (muss grün bleiben)

## 5. Qualitätssicherung
- [ ] `./mvnw test` – alle Tests grün (inkl. neuer Regressionstest)
- [ ] `npm run build` – falls Frontend betroffen
- [ ] `git diff` prüfen: Änderung ist minimal und zielgerichtet
- [ ] `git diff --staged` prüfen: Keine Secrets, keine echten Nutzerdaten

## 6. Commit & PR
- Branch: `fix/{kurzbeschreibung}`
- Commit-Body: Ursache erklären, nicht nur "Bug behoben"
- PR: Reproduktionsschritte + wie verifiziert wurde
