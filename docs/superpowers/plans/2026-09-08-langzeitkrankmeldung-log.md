# Kontext-Log: Langzeitkrankmeldung

Vorhaben: Langzeitkrankmeldung (Lohnfortzahlung → Krankengeld → Wiedereingliederung)
Issue: #91 — https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/91
Feature-Branch: `feature/langzeitkrankmeldung`
Spec: `docs/superpowers/specs/2026-09-08-langzeitkrankmeldung.md`
Design (abgenommen): `docs/superpowers/specs/2026-09-08-langzeitkrankmeldung-brainstorming.md`

**Append-only.** Nur unten anhängen, nie bestehenden Text ändern. Lock-Protokoll
siehe `.claude/skills/loese-problem/references/kontext-log-format.md`.

---

## Baseline (Orchestrator, vor dem ersten Coding-Agenten)

Zeit: 2026-09-08, gemessen auf dem unveränderten `feature/langzeitkrankmeldung`
(identisch mit `main` bei `f9d87afc`).

### Backend — `./mvnw -B test`

```
Tests run: 2461, Failures: 0, Errors: 4, Skipped: 0
```

**Vier vorbestehende Errors, alle umgebungsbedingt** (`CannotCreateTransaction:
Could not open JPA EntityManager for transaction` — diese Integrationstests
brauchen eine echte DB, die hier nicht läuft):

- `AuditChainRepairIntegrationTest.appendToChainHashIstNachRoundtripReproduzierbar`
- `AuditChainRepairIntegrationTest.rebuildMachtEchteLokaleKetteIntakt`
- `AuditHashRoundtripDiagnoseTest.getrimmterZeitstempelUeberlebtDbRoundtrip`
- `AuditHashRoundtripDiagnoseTest.rohNanosekundenUeberlebenDbRoundtripNicht`

**Abnahmeregel Backend: grün = genau diese 4 Errors.** Der fünfte ist neu und
gehört dir. Diese vier nicht "reparieren" — sie sind nicht kaputt, es fehlt nur
die Datenbank.

### Frontend PC — `react-pc-frontend`

- `npm run lint` → **exit 0**, 1 Problem: **0 Errors, 1 Warning**
  (`src/pages/BelegeKasseEditor.tsx:1204:8` — `react-hooks/exhaustive-deps`,
  fehlende Dependency `beleg`).
  **Abnahmeregel: 0 Errors und höchstens diese 1 Warning.** Eine zweite Warning
  ist neu.
- `npm run test` → **exit 0**, **88 Test-Dateien, alle grün.**

### Frontend Handy — `react-zeiterfassung`

- `npm ci` → exit 0
- `npm run lint` → **exit 0**
- `npm run test` → **exit 0**, **10 Test-Dateien, alle grün.**

### Flyway

Höchste vorhandene Migration: `V366__datensatz_lock_entitaet_typ_enum.sql`.
**`V367` ist frei** und für dieses Vorhaben reserviert.

---

## Umgebung — Workarounds, die jeder Agent kennen muss

### `react-pc-frontend/node_modules` war kaputt: `.bin` leer

Fehlerbild: `npm run lint` bzw. `npm run test` bricht ab mit
*"Der Befehl 'eslint' ist entweder falsch geschrieben oder konnte nicht gefunden
werden"* — obwohl `node_modules/eslint` und `node_modules/vitest` als Pakete
vorhanden sind. Ursache: `node_modules/.bin` war **leer** (0 Einträge), die
Wrapper-Skripte fehlten.

**`npm ci` repariert das hier nicht** — es scheitert mit
`EPERM: operation not permitted, unlink … @esbuild\win32-x64\esbuild.exe`, weil
ein laufender Prozess die Datei hält.

**Was funktioniert: `npm install`** (nicht `npm ci`, nicht `npm rebuild` — `npm
rebuild` lief zwar durch, ließ `.bin` aber leer). Danach 105 Einträge in `.bin`,
lint und test laufen. `package-lock.json` blieb dabei unverändert.

### Verwaiste Dev-Server sperren Dateien im Haupt-Checkout

Ursache des EPERM oben: Aus einem **früheren Pipeline-Lauf (05.09.2026)** liefen
noch zwei Vite-Dev-Server aus dem Worktree
`C:\Users\MarvinKuhn\dev\ERP-für-Handwerker\wt\review-design\react-pc-frontend`
sowie zwei `esbuild.exe` aus dem Haupt-Checkout.

Weil Worktrees ihr `node_modules` per Symlink/Junction auf das Haupt-Checkout
zeigen (so schreibt es `fallstricke.md` vor), sperrt ein Dev-Server **im
Worktree** die Binaries **im Haupt-Checkout**. Ein `npm ci` im Haupt-Checkout
scheitert dann, ohne dass man den Zusammenhang sieht.

Diese Prozesse laufen zum Zeitpunkt dieser Messung noch (der Kill war in dieser
Session nicht erlaubt). `npm install` kommt trotzdem durch. Wer einen Dev-Server
startet, beendet ihn am Ende seines Tasks wieder.

---

## Verlauf

## Setup (Orchestrator)

Zeit: 2026-09-08
Branch: feature/langzeitkrankmeldung
Status: fertig

Was gemacht wurde:
- Schritt 1: Spec geschrieben (`docs/superpowers/specs/2026-09-08-langzeitkrankmeldung.md`)
- Schritt 2: Issue #91 angelegt
- Baseline gemessen und oben festgehalten
- `react-pc-frontend/node_modules` repariert

Bedenken / Abweichungen vom Plan:
- Die Spec hat zwei Abweichungen zum abgenommenen Design gefunden:
  (1) `UrlaubsantragService.java:112` ruft `getSollstundenFuerTag` auf, steht im
  Design aber weder in der Umstellungs- noch in der Ausnahmeliste.
  (2) Die drei "wortgleich duplizierten" Feiertagsstellen sind **nicht**
  semantisch identisch — `ZeitkontoService` rechnet Feiertage als volle bezahlte
  Arbeitstage ins Periodensoll, die beiden anderen ermitteln eine separate
  Feiertagsgutschrift. Die Bündelung bleibt gewollt, darf aber kein Verhalten
  still ändern. Beides ist zur Entscheidung an den Grobplan gegangen.
- Statt 8 Aufrufstellen (Design) sind es real 11.

## Bewusste Verhaltensänderungen (Orchestrator, aus dem Grobplan)

Zeit: 2026-09-08
Status: zur Kenntnis für alle Coding- und Review-Agenten

Die Bündelung der Feiertagslogik darf nichts still ändern. Drei Stellen ändern
sich trotzdem — jede ist im Plan benannt und bekommt einen eigenen Test:

1. **`ZeitverwaltungController` /kalender, halbe Feiertage — Bugfix.**
   Heute liefert Zeile 512 an *jedem* Feiertag die **vollen** Sollstunden als
   Ist-Stunden, auch am Heiligabend, während `sollStundenMonat` (Zeile 543) den
   halben Feiertag korrekt halbiert. Das sind heute **+4 h Phantom-Überstunden
   pro halbem Feiertag**, und der Kalender widerspricht der Monatsübersicht.
   Nach der Umstellung liefert `feiertagsGutschrift` auch hier 4 h.
   Das ist ein **Bugfix, kein Rückschritt** — festgehalten in Task 11.
2. **Urlaubsstunden während einer Wiedereingliederung** folgen dem Stufenplan.
   Ohne Langzeitkrankmeldung ändert sich nichts.
3. **Krankheitsstunden im Verrechnungslohn**: Krankengeld- und
   Wiedereingliederungstage fallen aus Jahressoll und Lohnkosten heraus
   (Task 13). Ohne Langzeitkrankmeldung ändert sich nichts.

**Alles andere muss zahlengleich bleiben.** Dafür sind die
Charakterisierungs-Tests aus Task 2 da — sie nageln das heutige Verhalten aller
sechs Aufrufstellen fest, bevor umgestellt wird. Wer sie anfassen muss, hat
vermutlich einen Fehler gemacht; die einzige erlaubte Ausnahme ist die
Zusicherung zum halben Feiertag in Task 11.
