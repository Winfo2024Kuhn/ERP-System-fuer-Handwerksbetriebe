---
name: pre-merge
description: Vollständige Pre-Merge-Checkliste vor jedem Pull Request in den main-Branch.
---

# Pre-Merge-Checkliste

Alle Punkte müssen erfüllt sein, bevor ein PR gemergt wird.

---

## KRITISCH: Secrets & Datenschutz

- [ ] `git diff main...HEAD -- "*.properties" "*.yml" "*.yaml" "*.env"` – keine Credentials sichtbar?
- [ ] Kein `application-local.properties` im Diff
- [ ] Keine API-Keys, Tokens, Passwörter in Java-/TypeScript-Dateien
- [ ] Test-Daten: ausschließlich Dummy-Daten (`test@example.com`, `Max Mustermann`)
- [ ] Keine echten Kundennamen, E-Mails oder Adressen im Code oder Tests
- [ ] `uploads/`-Verzeichnis nicht versioniert

**Falls Secrets gefunden:** PR sofort stoppen, Key rotieren, dann erst beheben.

---

## A. Backend (Java / Spring Boot)

```bash
./mvnw clean package -DskipTests   # Kompiliert ohne Fehler?
./mvnw test                         # Alle Tests grün?
```

- [ ] Kompilierung erfolgreich
- [ ] Alle Tests grün (inkl. neue Tests für geänderte Funktionalität)
- [ ] Neue Endpoints haben Security-Tests (SQL Injection, XSS, ungültige IDs)
- [ ] Keine Entities direkt in REST-Responses (DTOs!)
- [ ] Flyway-Migrationen: Versionsnummer korrekt, bestehende nicht geändert
- [ ] Keine String-Konkatenation in JPQL/SQL

---

## B. Desktop-Frontend (`react-pc-frontend`)

```bash
cd react-pc-frontend
npm install          # Falls package.json geändert
npm run lint         # Keine ESLint-Fehler
npm run build        # Kein TypeScript-/Build-Fehler
npm run test         # Alle Tests grün
```

- [ ] Lint-Fehler behoben
- [ ] Build erfolgreich
- [ ] Tests grün
- [ ] Farbschema: Rose/Slate (kein indigo/blue)
- [ ] Pflicht-Komponenten verwendet (`Select`, `DatePicker` etc.)
- [ ] Page Header Pattern auf neuen Seiten vorhanden

---

## C. Mobile-Frontend (`react-zeiterfassung`)

```bash
cd react-zeiterfassung
npm install          # Falls package.json geändert
npm run lint
npm run build
npm run test
```

- [ ] Lint-Fehler behoben
- [ ] Build erfolgreich
- [ ] Tests grün

---

## D. Vollständiger System-Check

- [ ] Backend startet: `./mvnw spring-boot:run` ohne Exceptions
- [ ] Flyway-Migrationen laufen durch (kein Fehler beim Start)
- [ ] API antwortet auf `http://localhost:8082/api`
- [ ] Frontend kommuniziert korrekt mit Backend

---

## E. Code-Qualität

- [ ] Keine TODOs oder temporäre Debug-Logs (`System.out.println`, `console.log`) im Commit
- [ ] Kein auskommentierter Code im Commit
- [ ] Neue UI-Patterns in `.github/DEVELOPMENT.md` dokumentiert
- [ ] Tests enthalten keine echten Personendaten (DSGVO)
