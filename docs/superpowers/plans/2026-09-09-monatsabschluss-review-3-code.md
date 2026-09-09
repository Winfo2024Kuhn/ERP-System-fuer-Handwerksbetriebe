# Abschnitt 3 – unabhängiger Code-Review

Stand: 09.09.2026, geprüfter Featurestand 68e43eeb, Diff 81799a00..68e43eeb. Tasks 5, 8 und 9 einschließlich Fixturekorrektur 4445f1f1. Die vier Abschnitts-Merge-Commits sind vorhanden. Eigener Review-Worktree `codex/monatsabschluss-review-3-code`; kein Produktcode geändert.

**Code-Ampel: 🟡. Kein belegter Produkt-, Sicherheits- oder Architekturfehler. Die ausdrücklich gewünschte gemeinsame Arbeitszeitlogik und Testrobustheit werden vom Orchestrator als konkrete Nachbesserung vor Abschnittsabnahme eingeplant.** Design- und E2E-Abnahme erfolgen unabhängig; dieser Bericht ersetzt sie nicht.

## Konkrete Nachbesserung gemäß Nutzerauftrag

- `react-pc-frontend/src/pages/MitarbeiterEditor.tsx:117` und `:286` sowie `react-pc-frontend/src/pages/ZeiterfassungZeitkonten.tsx:24` und `:28`: Entwurfskonvertierung, sieben Wochentage mit Pflichtwerten 0–24 Stunden und Prüfung optionaler Zeitfenster sind zweimal implementiert. Der Nutzer hat ausdrücklich Auslagern und Wiederverwenden verlangt. Gemeinsam verantwortete Entwurfs-/Validierungsfunktionen und geeignete Stundenfelder auslagern; fachliche Unterschiede wie Vorlagenauswahl, individuelle Abweichung, Versionsprüfung und Vorschauinvalidierung in ihren Abläufen erhalten. Alle Konsumenten weiter mit demselben gemeinsamen DecimalInput/TimeInput bedienen. Die Duplikation hat aktuell keinen nachgewiesenen Fachfehler; die Umsetzung ist dennoch ein konkreter Nutzerauftrag, keine Naming-Präferenz.
- `react-pc-frontend/src/pages/Monatsabschluss.test.tsx:30`: Der 500er-Auswahl-/Teilerfolgstest lief im ersten vollständigen Standardlauf über das lokale 5-Sekunden-Limit. Derselbe unveränderte Test besteht isoliert in 1,63 Sekunden und mit begrenzter Volllaufparallelität. Gezielte beschriftungsbasierte bzw. auf Regionen begrenzte Abfragen vermeiden teure globale DOM-Scans. Auswahlumfang 500, Seitenwechsel, Teilerfolg und verbleibende Fehlerauswahl vollständig weiter prüfen; keine Assertions streichen.

## Selbst ausgeführte Prüfungen

| Prüfung | Ergebnis | Log |
| --- | --- | --- |
| PC vollständiges `npm test` | 1229 bestanden, 1 Timeout im neuen 500er-Test | `/tmp/review3-pc-test.log` |
| PC Monatsabschluss-Datei unverändert separat | 9/9 bestanden | `/tmp/review3-pc-month-retry.log` |
| PC vollständiges `npm test -- --maxWorkers=2` | 1230/1230 bestanden | `/tmp/review3-pc-test-workers2.log` |
| PC `npm run lint` | Exit 0 | `/tmp/review3-pc-lint.log` |
| PC `npm run build` | Exit 0 | `/tmp/review3-pc-build.log` |
| Mobile vollständiges `npm test` | 185/185 bestanden | `/tmp/review3-mobile-test.log` |
| Mobile `npm run lint` | Exit 0 | `/tmp/review3-mobile-lint.log` |
| Mobile `npm run build` | Exit 0 | `/tmp/review3-mobile-build.log` |

Die zuvor dokumentierten 18 LieferantDokumentModal-Fehler sind mit der Response-Fixturekorrektur behoben; alle 19 Tests dieser Datei bestanden im vollständigen Lauf. Beide Builds melden die bekannten großen Bundles; keine Buildfehler. Eigene erzeugte statische Buildartefakte zurückgenommen. Abhängigkeiten wurden aus vorhandenen Task-Worktrees verlinkt, keine Paket-/Lockdatei geändert. Das verfügbare Shellwerkzeug bietet keinen `timeout`-Parameter; Prozesse wurden mit Dateilogs gestartet und Sessions ausdrücklich bis zum Exit abgefragt.

Backend/SQL/Migrationen sind gegenüber dem bereits abgenommenen Abschnitt 2 unverändert (Diff geprüft). Gemäß Reviewauftrag gelten dessen selbst ausgeführte Nachweise weiter: 2803 Backendtests mit vier bekannten Konfigurationsfehlern; anschließender Audit-Nachlauf mit passendem MySQL-Dialekt ohne Fehler, DATEV-MySQL 6/6 und Sammelabschluss-MySQL 3/3 bestanden. Details und Logs im [Abschnitt-2-Bericht](2026-09-09-monatsabschluss-review-2-code.md). Keine erneute Behauptung einer vollständig grünen Backend-Standardsuite.

## Codebeurteilung

- Monatsseite prüft Abschlussrecht vor personenbezogenen Datenzugriffen; serverseitige Rechte bleiben maßgeblich. Historie lädt erst bei Bedarf. Mitarbeiter-/Abteilungslisten sowie Übersicht/Vergleich laden parallel, Abbrüche schützen vor verspäteten Ergebnissen.
- Filterwechsel entfernen alte Auswahlen; Gesamtauswahl verwendet die vollständigen begrenzten Referenzen statt der sichtbaren Seite. Bestätigungen werden bei Kontextwechsel bzw. Unmount invalidiert; Ref verhindert doppelte Sammelrequests. Teilergebnisse bleiben sichtbar, Fehlerauswahl erhalten, Ergebnisseite und Verlauf werden nach Abschluss zurückgesetzt.
- Deutsche Texteingaben werden vollständig validiert, bevor Zahlen an APIs gehen. Leere Pflichtfelder bleiben ungültig, Kommazwischenstände werden nicht still zur Null. Bestehende unveränderte Buchungssekunden bleiben erhalten; optional leeres Ende ergibt null. Mehrere geänderte Buchungen werden vor dem ersten Schreibrequest auf Eingabefehler geprüft.
- Mobile UI-Bausteine kapseln Zahlen, Auswahl, Meldungen und Bestätigungen. Bestätigungen behandeln Abbruch/Unmount/konkurrierende Aufrufe; temporäre Ereignislistener und Toasttimer werden aufgeräumt. React-Ausgabe bleibt escaped, neue API-Adapter enthalten keine dynamischen fremden Hosts oder Geheimnisse. Keine neuen personenbezogenen Diagnose-Logs gefunden.
- Nicht vollständig migrierte andere Seiten und noch fehlende DATEV-Bedienoberfläche sind explizite Folgetasks 6 und 10–14; kein Abschnittsregressionsbefund. Tatsächlicher DATEV-Import bleibt außerhalb der verfügbaren Verifikation.

## Nachprüfung der ersten gebündelten Korrektur

Geprüft nach Merge des korrigierten Featurebranches in eigenen Reviewstand `da865317`: 41d97bc9 (Arbeitszeit-Wiederverwendung/Testrobustheit), fb65a7d3 (Testmigration/Netzhelper), 5e267a35 (gemeinsamer Dialogfokus) und 322e965e (Auswahlfeld-Akzentfarben).

**Code-Ampel nach Korrektur: 🟢 – keine offenen Codebefunde.** Die unabhängige Designabnahme bleibt erforderlich.

- `features/zeitkonto/arbeitszeitInput.ts` und `ArbeitszeitFelder.tsx` kapseln nun gemeinsam Wochentage, Entwurfskonvertierung, Pflicht-/Grenzvalidierung und Zeitfenster. Beide Editoren nutzen sie tatsächlich; Vorlagenwahl, individuelle Abweichung, Versionsdaten und Invalidierung laufender Vorschauen bleiben bei den Konsumenten erhalten. Unterschiedliche Beschriftungen ändern keine Fachregeln.
- Der 500er-Test behält sämtliche Fachassertions bei. Beschriftungsbasierte Abfragen und auf den Header begrenzte Rollensuche reduzieren DOM-Scanning; kein erhöhter Timeout und kein verringerter Auswahlumfang.
- Der zentrale Dialog berücksichtigt den obersten sichtbaren Modalzustand, verschachtelte Dialogtiefe und über aria-controls zugehörige Pickerportale. Tab/Shift-Tab, Escape, Fokusabwehr außerhalb, Rückgabe an den Öffner und Listener-Cleanup sind gezielt geprüft. Externe darüberliegende Bestätigungen behalten den Fokus. Geschlossene/abgebaute Portale und entfernte Öffner werden vor verzögerter Fokusrückgabe geprüft.
- Eigene zusätzliche temporäre Tests unter React.StrictMode prüfen sowohl initial geöffneten Dialog als auch Öffnung per Button, jeweils autoFocus und Microtask-Cleanup/Fokusrückgabe nach Unmount. Beide bestanden. Prüfquelle zur Nachvollziehbarkeit: `/tmp/review3-dialog-strictmode-probe.test.tsx`; kein zusätzlicher Produkt- oder Testcode im Commit.
- Zentrale Checkbox-/Radiofarben beider Apps ändern nur Styling. E2E-Testmigrationen und Fremdhost-Sperre sind statisch geprüft; Browserausführung bleibt beim Designreview.

| Eigene Nachprüfung | Ergebnis | Log |
| --- | --- | --- |
| Dialog, Datum/Select/Zahl/Uhrzeit, gemeinsame Arbeitszeitfelder/-validierung, beide Editoren, Monatsabschluss | 91/91 Tests in 11 Dateien bestanden | `/tmp/review3-correction-tests.log` |
| Zusätzliche StrictMode-Proben | 2/2 bestanden | `/tmp/review3-dialog-strictmode.log` |
| PC vollständiger Lint | Exit 0 | `/tmp/review3-correction-lint.log` |
| PC Produktionsbuild | Exit 0 | `/tmp/review3-correction-build.log` |

Keine identische Wiederholung der vorher vollständig belegten Suiten; gezielte Nachprüfung entsprechend Korrekturumfang. Mobile betrifft in dieser Korrektur nur CSS und E2E-Hilfen, keine Anwendungslogik. Eigene Buildartefakte wieder entfernt/zurückgenommen.
