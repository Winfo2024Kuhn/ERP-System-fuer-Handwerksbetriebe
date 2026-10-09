---
name: fullstack-parallel
description: Ablauf für Aufgaben im ERP, die Backend (Spring Boot) UND Frontend (react-pc-frontend / react-zeiterfassung) betreffen – kurzes Brainstorming mit dem Nutzer, API-Vertrag festlegen, Frontend parallel per Subagent bauen lassen, Backend selbst bauen, und erst wenn beides fertig ist EIN gemeinsames /review-and-ship. Unbedingt verwenden, sobald ein Wunsch neue oder geänderte Endpoints plus neue Knöpfe/Dialoge/Seiten braucht („mach das auch im Frontend“, „neuer Dialog mit Vorschlägen“, „Endpoint + Anzeige“), auch wenn der Nutzer nicht „parallel“ sagt. Auch explizit über „/fullstack-parallel“. NICHT für reine Backend- oder reine Frontend-Änderungen und nicht für kleine Fixes; für große, mehrteilige Features mit Issue/PR-Pipeline ist /loese-problem gedacht.
---

# fullstack-parallel – Backend selbst, Frontend parallel, Review am Schluss

**Ankündigen:** „Ich nutze den fullstack-parallel-Skill: kurzes Brainstorming, dann Backend und Frontend parallel, Review am Schluss.“

Warum dieser Ablauf: Backend und Frontend hängen nur über die API zusammen. Steht der
Vertrag (Endpoints, Parameter, JSON-Felder, Fehlercodes) einmal fest, können beide Seiten
gleichzeitig entstehen – das halbiert die Wartezeit. Ein Review über halbfertigen Code
kostet dagegen eine Runde und findet Dinge, die sich ohnehin noch ändern. Deshalb:
parallel bauen, aber **einmal** am Ende prüfen.

## 1. Kurzes Brainstorming (Minuten, nicht Stunden)

Erst den betroffenen Code lesen (Grep/Read, bei breiten Fragen `scripts/graphify`), dann
dem Nutzer **einen konkreten Vorschlag** machen statt offener Fragen:

- Was genau gebaut wird, in Handwerker-Sprache (welcher Knopf, wo, was passiert).
- Was es schon gibt und wiederverwendet/verallgemeinert wird.
- Höchstens 2–3 echte Entscheidungsfragen, nur wenn die Antwort den Bau ändert.
- **Auslagerung/Refactoring** (Komponente, Hook, Service verallgemeinern) gleich
  mit zur Freigabe stellen – CLAUDE.md verlangt die Zustimmung des Nutzers vorher.

Erst nach dem „ja passt“ weiter. Ist der Weg schon völlig klar und vom Nutzer
vorgegeben, genügt ein Satz Zusammenfassung.

## 2. API-Vertrag festlegen

Vor dem Aufteilen den Vertrag schriftlich festhalten – er ist die einzige Abhängigkeit
zwischen beiden Hälften:

- Methode + Pfad, Query-/Body-Parameter mit Typen und Pflichtfeldern
- Antwort-JSON Feld für Feld (Namen exakt, `null`-Fälle)
- Statuscodes: 200, 400 (mit `{message}` in Handwerker-Sprache), 401, 403, 404
- Welche alten Endpoints bleiben (die das Frontend weiter nutzt)

Bestehende DTOs wiederverwenden, wo die Felder passen (z. B. `DokumentRef`) – dann muss
das Frontend keine neuen Typen erfinden.

## 3. Frontend-Subagent starten (Hintergrund)

`Agent` mit `subagent_type: "general-purpose"`, `run_in_background: true`, **im selben
Working Tree** (kein Worktree – die Dateien überschneiden sich nicht, und ein späteres
Zusammenführen entfällt). Der Prompt muss allein tragen; der Agent kennt das Gespräch
nicht. Hinein gehört:

1. **Pflichtlektüre & Hooks:** `Read` auf `docs/agent instructions/docs/FRONTEND_UI.md`
   und `TESTING_SECURITY.md`, `Skill` `handwerkerprogramm-design` – sonst blockt der
   Edit-Hook.
2. **Ziel aus Nutzersicht** inkl. eines konkreten Beispiels aus dem Gespräch.
3. **Bestehende Basis** mit Pfaden und ungefähren Zeilen (Komponenten, Hilfsdateien, Tests).
4. **Der API-Vertrag aus Schritt 2** wörtlich.
5. **Umsetzungsschritte** und wo neue Knöpfe hinkommen.
6. **Tests:** Vitest + Testing Library, bestehende grün halten, E2E unter
   `react-pc-frontend/e2e/` mit `page.route`-Mocks; `npm run lint`, `npm run test -- --run`,
   `npm run build`; wenn möglich Skill `playwright-design-pruefung`.
7. **Grenzen:** nur Dateien unter `react-pc-frontend/` (bzw. `react-zeiterfassung/`),
   `src/main/java` nicht anfassen, **nicht committen**.
8. **Bericht:** vollständige Liste geänderter/neuer/gelöschter Dateien, Testzahlen,
   Lint/Build-Status, offene Punkte.

Ändert sich der Vertrag später, dem laufenden Agenten per `SendMessage` Bescheid geben –
nicht einen zweiten starten.

## 4. Backend selbst bauen (währenddessen)

`docs/agent instructions/docs/BACKEND_ARCH.md` und `TESTING_SECURITY.md` lesen, dann:

- Logik in einen Service (Controller bleibt dünn), Constructor Injection, Flyway nur
  neu und idempotent.
- Endpoints mit Sichtbarkeits-/Rechteprüfung wie die Nachbar-Endpoints, Validierung
  (`@Valid`, `@Positive`), verständliche 400-Meldungen.
- Tests: Service (Mockito), Controller/Security (MockMvc: 401, CSRF 403, 404 nicht
  sichtbar, 400 ungültige IDs/SQL-Injection/XSS-Strings), Dummy-Daten („Max Mustermann“).
- Alle Konstruktor-Aufrufe in bestehenden Tests mitziehen (`grep -rn "new <Klasse>("`).
- `./mvnw -q compile`, gezielte Tests, danach die **komplette Suite im Hintergrund**.

## 5. Warten, bis beides fertig ist

Kein Review, kein Commit, solange der Frontend-Agent läuft oder Tests offen sind –
auch wenn der Stop-Hook „uncommitted changes“ meldet. Dem Nutzer kurz sagen, worauf
gewartet wird. Nicht pollen: die Benachrichtigungen kommen von selbst.

Wenn der Frontend-Bericht da ist:

- Gegenprobe Vertrag: Endpoints und Feldnamen im Frontend greppen und mit dem Backend
  vergleichen (häufigster Fehler: ein umbenanntes Feld).
- Frontend selbst einmal `lint`, `test -- --run`, `build` laufen lassen.
- Rote Tests, die nichts mit der Änderung zu tun haben: mehrfach einzeln laufen lassen,
  als unzuverlässig benennen statt still zu ignorieren.

## 6. Ein gemeinsames /review-and-ship

Jetzt `Skill` → `review-and-ship` über den **gesamten** Diff (Backend + Frontend). Dort
gelten dessen Regeln: ein Reviewer-Subagent, höchstens 2 Runden, nur eigene Dateien
stagen (die Liste aus deinen Edits + dem Bericht des Frontend-Agenten), Build-Artefakte
aus `npm run build` dürfen mit.

## Abschluss an den Nutzer

Kurz und in Handwerker-Sprache: was jetzt wo geht (welcher Knopf, welcher Reiter), was
bewusst nicht gemacht wurde, Testzahlen, Commit/Branch. Kein Pull Request, wenn der
Nutzer keinen verlangt hat.
