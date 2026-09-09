# Kontext-Log: Zeitkonto-Vorlagen und Historisierung

## Fortsetzung am 09.09.2026 — Orchestrator

Branch: feature/zeitkonto-vorlagen-und-historisierung
Basis: 48d25186
Status: Implementierung vom Nutzer ausdrücklich freigegeben; Planung und Umsetzung laufen.

Bestand: Spec und Issue #98 vorhanden. Keine ursprünglichen Produktänderungen. Fremde Änderungen an static/index.html und lokalen Skill-/Codex-Dateien bleiben unberührt.

Baseline vor ersten Codeänderungen:
- Backend: 2611 Tests, 0 Failures, 4 Errors. AuditChainRepairIntegrationTest und AuditHashRoundtripDiagnoseTest: MySQL-Treiber akzeptiert H2-Test-URL nicht.
- PC: 1133 bestanden, 18 fehlgeschlagen in LieferantDokumentModal.test.tsx (Blob.stream fehlt im Testkontext); 91/92 Dateien bestanden.
- Mobile: 146/146 Tests, 11/11 Dateien bestanden.

Umsetzung: isolierte Worktrees, höchstens drei disjunkte Tasks pro Runde, anschließender Review. Modellnamen der früheren Claude-Rollen sind in dieser Codex-Sitzung nicht verfügbar; Subagenten verwenden das Sitzungsmodell bei gleicher Aufgabenverteilung.

Fachliche Präzisierungen für die Umsetzung:
- Historisierung schützt auch offene Altmonate. Der Monatsabschluss ist eine zusätzliche Sperre für den gespeicherten Monatswert, keine zugesicherte rechtliche GoBD-Zertifizierung.
- Abwesenheitsstunden bleiben wie entschieden unverändert. Bei rückdatiertem Wechsel innerhalb offener Monate muss die Vorschau vorhandene Abwesenheitsgutschriften sichtbar nennen; sie werden nicht still neu berechnet. Eine Änderung der Stundengutschrift ist nicht automatisch eine Änderung des Urlaubsanspruchs in Tagen.
- Akteur des Monatsabschlusses wird aus authentifiziertem FrontendUserPrincipal und zugeordnetem Mitarbeiter bestimmt. Frei übermittelte Mitarbeiter-IDs sind keine Berechtigungsnachweise. Das neue Recht bleibt standardmäßig aus; kein impliziter Admin-Bypass.
- Schema wird in nachvollziehbare Migrationen geteilt. Alte Tabelle bleibt nur während des internen Umbaus bestehen und entfällt vor Abschluss; keine produktive Datenbank wird für Entwicklungsprüfungen geändert.


## Abschnitt 1 — Datenfundament (Coding + Orchestrator-Review)

Branch: codex/zeitkonto-schema
Commits: b5f9628f, de8ed054
Status: fertig; im Feature-Branch übernommen.

Neue Mitarbeiterart, Kontoflag, Abschlussrecht, versionierte Arbeitszeit-/Vorlagentabellen und Abschluss-Audit angelegt. V368/V369 übernehmen vorhandene Stundenwerte einschließlich NULL-Werten; Altmodell bleibt ausschließlich als interner Übergang erhalten.

Verifikation: Compile und 6 gezielte H2-Tests grün. Zusätzlich V368 und V369 in isoliertem MySQL 8 zweimal auf demselben Dummy-Bestand ausgeführt: keine Duplikate; Saldozahlen und gueltig unverändert; SYSTEM aktiv/ohne Konto; Recht false; Herkunft leer. Fallback ohne Datum/Belege 1000-01-01 ist technischer Gültigkeitsanker, niemals Ausgangspunkt für die Benachrichtigungs-Historie.

Review: Entities/DDL/Repository-Verträge geprüft, Test-Fixtures auf parametrisierte Inserts nachgebessert. Noch keine fachliche Abschlusssperre im Service, das folgt in Abschnitt 2.

Orchestrierung: Nutzer hat ausdrücklich flexible Codex-Orchestrierung bestätigt. Hauptagent übernimmt Integration und Querschnittsprüfung; unabhängige Aufgaben laufen parallel. Backend-Tasks 2 (Abschluss/Rechte), 3 (Versionen/Vorlagen), 4 (Mitarbeiterfilter) gestartet, disjunkte Worktrees.

## Wiederaufnahme nach Berechtigungswechsel

Uneingeschränkter Zugriff ist aktiv. Task 4 nach Codeprüfung zusammengeführt; 102 gezielte Backend-Tests auf dem integrierten Feature-Branch erneut erfolgreich. Mitarbeiter-DTOs enthalten art/fuehrtZeitkonto, Menschenlisten trennen SYSTEM, Token-Anmeldung schließt SYSTEM aus. Atomarer Kontowechsel folgt in Task 5. Task 2/3 wurden nach App-Neustart wieder aufgenommen. Task 7 hat Verrechnungslohn committed; ZeitbuchungRepository wurde ihm für die offene-Buchungen-Abfrage ausdrücklich zusätzlich zugewiesen.

## Integrierter Stand und Modellzuordnung

Seit Nutzerkorrektur: Coding-Subagenten GPT-5.6 Terra, Reviews und Orchestrierung GPT-6 Astra. Bereits laufende Astra-Codingtasks wurden mit erhaltenem Worktree an Terra übergeben. Tasks 2, 3, 4, 5-API, 6, 7 und Frontend 8/9/9b/10 sind im Feature-Branch integriert. Task 11 entfernt das Altmodell. Keine automatische Produktionseinspielung.

Backend-Rundenreview: zwei P2-Befunde (echte Vorschauwerte; Sperrreihenfolge bei Mehrfachübernahme), erste Nachbesserung läuft. Integrationsprüfung fand zusätzlich einen veralteten JPA-Versionsstand nach Bulk-Invalidierung; vor Lock-Upgrade wird der Saldo aktualisiert. Der Regressionstest mit echten Repositorys bestätigt unveränderten Abschluss und Urlaub sowie neu berechneten offenen Monat.

Frontend-Rundenreview: fünf P2-Befunde; erste Nachbesserung auf zwei Terra-Tasks verteilt (veraltete Vorschau, historische Feiertage, Vorlagenherkunft, Abwesenheitswarnung). Gemeinsamer Zwischenstand: PC 1148 Tests bestanden, die bekannten 18 Blob.stream-Fehler unverändert; Mobile 147/147 bestanden. PC-Playwright 21/21, Mobile 2/2 bestanden. Builds erfolgreich. Zwei neue Lintfehler behoben.

MySQL-Migrationen V370 und V371 zweimal auf isoliertem Dummy-Bestand ausgeführt: drei historische Versionen erhalten, Alttabelle entfernt, gespeicherte Ist-/Sollwerte und gueltig unverändert. Acht MySQL-Abschluss-/Konkurrenztests erneut erfolgreich.

Designprüfung am integrierten Zwischenstand: 1440er Zeitkontovorschau, 1920er Mitarbeiterdialog und Handyansicht geöffnet. Hinweise, Fehler und Aktionen farblich unterscheidbar; Rose-Akzent und bestehende Komponenten; Dialoge ruhig ausgerichtet; Primäraktion und Ergebnis sichtbar; Aktionen im Dialog ohne Scrollen erreichbar; keine Überlagerung oder horizontaler Überlauf. Mehrfachdetails und Vorschauwerte werden nach Reviewkorrektur erneut geprüft. Screenshots unter react-pc-frontend/test-results/design und react-zeiterfassung/test-results.

## Gesamtprüfung und letzte Reviewkorrekturen

Alle elf Tasks sind integriert. Das Altmodell samt Repository und Kompatibilitätsmethoden ist entfernt (V371). Backend-Nachbesserungen 52564aeb/58a96780 verwenden dieselbe Tagesberechnung für Vorschau und Übernahme und eine feste Sperrreihenfolge für Mehrfachzuweisungen. Astra hat den integrierten Backend-Stand 6962206e freigegeben. Mitarbeiterkorrektur 330586da ist ebenfalls freigegeben: veraltete Antworten werden verworfen, Vorlagenherkunft bleibt bei eigenen Stunden erhalten. Die übrigen Frontendkorrekturen werden separat abschließend geprüft.

Abschließende Nachweise:
- Backend-Gesamtlauf: 2743 Tests, 0 Failures, 4 bereits vorhandene Audit-Errors, 6 übersprungene umgebungsabhängige Tests. Nach letztem Backendfix zusätzlich 38 Vorschau-/Wechseltests bestanden. Paketierung erfolgreich.
- Acht echte MySQL-Abschluss-/Konkurrenztests bestanden. Migrationen V368–V371 auf isoliertem MySQL-Dummy-Bestand geprüft, einschließlich wiederholter Ausführung und unveränderter Salden.
- Vollständiger Anwendungsstart gegen eine separate lokale MySQL-Testdatenbank erfolgreich, API erreichbar (401 ohne Anmeldung). Produktion wurde nicht verwendet.
- Desktop-Gesamtlauf: 1148 bestanden, ausschließlich dieselben 18 Lieferantendokument-Fehler wie vor Beginn. Mobile: 147 bestanden. Beide Builds erfolgreich; Lint ohne Fehler.
- Integrierte Browserprüfungen: Desktop 21/21, Mobile 2/2 bestanden. Die spätere Frontendkorrektur erhält einen zusätzlichen gezielten Lauf.

Zwischenzeitliche Änderungen auf origin/main (afef5bb7) sind übernommen. Die bereits vorhandene lokale LF-Normalisierung von static/index.html entspricht bytegenau der inzwischen auf main veröffentlichten Korrektur; der finale PR fügt daher keine eigene Build-Dateiänderung hinzu. Andere lokale Dateien bleiben außerhalb des Commits.

## Freigabe des integrierten Codes

Astra-Abschlussreview auf 9393ce03: grün. Alle fünf ursprünglichen Frontendbefunde und beide Export-Restbefunde behoben. Enthalten: 330586da, 8d75de33, 8e33d427. Das Backend liefert die tatsächliche Tages-Feiertagsgutschrift direkt an den Export; dessen durchschnittliches Tagessoll stammt aus den Versionen des ausgewählten Monats. Auch der Ladezustand nach veralteter Vorschau wird freigegeben.

Nach Integration: zehn betroffene Desktop-Tests und zwei Controller-Tests erfolgreich; Desktop-Build erfolgreich; Lint 0 Fehler/4 Warnungen (keine zusätzlichen Fehler). Die Gesamt-Testläufe bleiben mit ihren vorbestehenden Fehlern separat dokumentiert. Eigene MySQL-Testcontainer wurden entfernt.

Finale Desktop-Browserprüfung auf dem integrierten Stand: erneut 21/21 bestanden (1440×900, 1536×960, 1920×1080). Vorschau-Screenshot geöffnet: Eingaben, Herkunftshinweis, Monatsvergleich und Primäraktion sichtbar, keine Überlagerungen. Mobile blieb unverändert bei 2/2.

Graphify abschließend aktualisiert: 14833 Knoten/50835 Kanten. Ein Korrekturlauf schließt die unversionierten lokalen Skill-/Codex-Dateien aus dem veröffentlichten Graphen aus; diese Dateien selbst und die Ignore-Konfiguration bleiben unverändert. Parser meldet eine teilweise Extraktion in der unveränderten TiptapEditor.tsx; TypeScript-Build ist erfolgreich. Kein Semantik-/LLM-Neulabeln erforderlich. Nur Graph-Artefakte und aufgabenbezogene Dokumente werden zusätzlich committed. Generierte Build-Dateien sind bereinigt.
