# PR-Entwurf: Datensatz-Sperren & Inaktivitäts-Freigabe — Teilprojekt 1: Fundament

Schließt #82. Branch `claude/eloquent-ramanujan-gz0w2t` → `main`.

## Was das löst

Auf einem 14-Zoll-MacBook schloss der X-Button im Dokument-Editor den Tab nicht
zuverlässig, und weil die Freigabe der Sperre am Schließen der Komponente hing, blieb das
Dokument für Kollegen gesperrt. Dazu fehlte ein Inaktivitäts-Timer, das Sperr-System war
auf zwei Dokumenttypen festgenagelt, und wer als Zweiter speicherte, gewann kommentarlos.

## Was jetzt anders ist

**Backend**
- Verallgemeinertes Soft-Lock: `SperrbarerTyp` (Enum), Tabelle `datensatz_lock` (V363),
  `DatensatzLock`/`Repository`/`Service`/`Controller`/`Dto`. Route
  `/api/datensatz-locks/{typ}/{id}/acquire|heartbeat`, `DELETE /{typ}/{id}`. Ein neuer
  sperrbarer Typ kostet einen Enum-Wert.
- Optimistisches Sperren: `@Version` auf 14 Aggregate-Roots (V364). Versionskonflikt ⇒
  HTTP 409 mit Handwerker-Meldung, auf allen sechs Speicherwegen des
  Ausgangs-Controllers — vorher verschluckte ein `catch (RuntimeException)` ihn als 400.
- Altes System entfernt: `DokumentLock*` (fünf Klassen + Test) und `DROP TABLE dokument_lock`
  (V365) in **einer** Auslieferung — ein Zwischenstand mit Entity ohne Tabelle startet nicht.

**Frontend PC** — fünf wiederverwendbare Bausteine plus Hinweisseite
- `useDatensatzLock` (Lock halten, Modus lesen/bearbeiten, Übernahme, Freigabe; Invariante
  „nie Bearbeiten-Modus ohne gehaltenes Lock", als Test über alle Hook-Tests gelegt)
- `useIdleTimer` (5 Min, 60 s Vorwarnung, auf 1/s gedrosselt)
- `BearbeitenLeiste` (Bearbeiten/Fertig, Countdown amber, Verbindung-weg rot, Tooltip am
  deaktivierten Knopf, „Sie lesen nur mit.")
- `GesperrtHinweis` (ruhiger Nur-Lesen-Hinweis statt blockierendem Modal)
- `useKonfliktMeldung` (409 ⇒ „Nicht gespeichert — Neu laden", eigene Fehlschlag-Variante)
- `TabSchliessenHinweis` — der neue X-Button-Ablauf: Warnung ⇒ speichern ⇒ Sperre aktiv
  freigeben ⇒ Tab schließen ⇒ bleibt er offen, Hinweisseite.
- Beide Verbraucher umgestellt: `DocumentEditorPage` (Seite hält das Lock, Editor bekommt
  `readOnly`/`onLockFreigeben`, eigene Lock-Logik des Editors samt zweitem Heartbeat weg,
  Id nach dem Anlegen über den Router) und `LieferantDokumentModal`.
- Toasts liegen bei offenem Dialog unten links und über jedem Backdrop; Confirm-Dialog
  trägt `role="dialog"`; „Gebucht"-Badge nur noch bei wirklich gebuchten Rechnungen.

**Frontend Mobile** (`react-zeiterfassung`): unverändert — nur indirekt über `@Version`
abgesichert.

**Prüfung** — neu in diesem Branch, gilt ab jetzt für jede Frontend-Änderung
- Playwright-E2E mit fester Bildschirmgröße 14 Zoll (1440×900) und Monitor (1920×1080),
  `E2E_PORT` je Agent, Aufwärmen des Dev-Servers vor dem ersten Test.
- `e2e/hilfen/design.ts`: Screenshots nach ausgeklungenen Übergängen, automatische Prüfung
  auf Überlauf (auch in Containern), abgeschnittenen Text, Überschneidungen (auch feste
  Elemente wie Toasts), Sichtbarkeit der Primäraktion.
- Skills: `playwright-design-pruefung`, Design-Reviewer als eigene Rolle, Coding-Agenten
  testen nur ihre Änderung.

## Zahlen (letzter Durchgang, vom Reviewer selbst gemessen)

| Gate | Ergebnis |
| --- | --- |
| Backend `./mvnw test` | siehe Abschluss-Code-Review im Kontext-Log |
| Frontend `npm test` | siehe Abschluss-Code-Review |
| Lint | 0 Fehler, 1 vorbestehende Warnung (`BelegeKasseEditor.tsx:1204`) |
| E2E | 114 Tests grün, beide Größen |

Die 4 bekannten Backend-Errors (`AuditChainRepairIntegrationTest`, `AuditHashRoundtripDiagnoseTest`)
brauchen eine echte Datenbank und sind unabhängig von diesem Branch.

## Bewusst NICHT in diesem PR (Restpunkte, je ein Folge-Vorhaben)

- Ausrollen der Sperr-Oberfläche auf die übrigen ~22 Editoren (Spec: Nicht-Ziel).
- 14-Zoll-Layout: PDF-Spalte im Lieferant-Modal, „Nicht speichern" zweizeilig, inline
  Fehlerband über Feldern — läuft in einer eigenen Sitzung.
- Leere 55-px-Leiste im Bearbeiten-Zustand auf 1920; Leertext „Nutzen Sie die Buttons oben…"
  auch im Nur-Lesen-Modus; „Fertig" auf gebuchten Rechnungen erklärungsbedürftig.
- Vorbestehende Design-System-Brüche außerhalb des Diffs (Emoji/blue in
  `LieferantDokumenteTab.tsx`, handgemaltes SVG in `DocumentEditorHeader.tsx:221`,
  sky/emerald in der `'info'`-Variante).
- `__pycache__/*.pyc` unter `.agents/skills/ui-ux-pro-max/` sind versioniert (schon auf
  `main`); `.gitignore`-Eintrag plus `git rm --cached` als eigener Commit.
- Deploy-Hinweis: laufende Sperren gehen beim Update einmalig verloren (Tabelle wird
  ersetzt) — schlimmstenfalls einmal neu auf „Bearbeiten" klicken.

## Nachvollziehbarkeit

Plan, Kontext-Log (jeder Task, jeder Review mit roten Meldungen und Mutationsproben) und
die aus Issue #82 zurückgeholte Spec liegen versioniert unter `docs/superpowers/`.
