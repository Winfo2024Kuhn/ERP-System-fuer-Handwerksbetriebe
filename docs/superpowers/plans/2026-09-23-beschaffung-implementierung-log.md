# Beschaffung – Kontext-Log

Issue: #167
Feature-Branch: codex/beschaffung-konzept
Spec: docs/superpowers/specs/2026-09-22-beschaffung-konzept.md
Coding: GPT-6 Luna; Review: GPT-6 Sol

## Vorbereitung — Orchestrator

Zeit: 2026-09-23T10:16:00.350568+00:00
Branch: codex/beschaffung-konzept
Basis: 53718ad23c8ae0ab3174d34ab4cbfd48d9f067b3
Status: Umsetzung beauftragt, Implementierungsplanung läuft

- Nutzer hat die geprüfte Spec einschließlich nachgereichter Ergänzungen freigegeben und loese-problem bis PR/Merge aufgerufen.
- Spec und bestehendes Issue #167 werden weitergeführt; keine doppelten Konzept-Issues.
- Geprüfte Ausgangsbasis: Backend ohne Fehler (17 bestehende Skips), Desktop 1628 Unit- und 669 E2E-Tests, Mobile 346 Unit- und 24 E2E-Tests grün. Keine Produktänderung seit diesen Läufen.
- Remote main liegt unverändert auf 28d778d4; Branch enthält die beiden bereits geprüften Konzept-/E2E-Commits.
- Zusätzliche Abnahme: lokal startbarer Server mit restaurierter DB sowie manueller Anfrageversand/Antwortabruf nach Kontokonfiguration.
- Intaktes Produktionsbackup vom 09.09.2026 gefunden, Prüfsumme bestätigt. Neue isolierte MySQL8.0.44-Kopie erp-beschaffung-db lokal auf3309: 139Tabellen, FlywayV367, EventSchedulerOFF. Restore ohne Fehler. Originalbackup und Bestandscontainer unverändert.
- Produktserver noch nicht gestartet; Testprofil muss ausgehende Hintergrundaktionen unterbinden. Historische Uploads fehlen im SQL-Dump.
- Zugangsdaten/Dump außerhalb des Repositorys; automatisierte Tests ausschließlich mit Dummy-Daten.
- Vorhandene uncommittete Graphify-Ausgaben stammen aus vorheriger Synchronisierung und werden nicht in Taskcommits aufgenommen.

Bedenken / Abweichungen: keine. Explizit spätere Teilvergabe-UI/mobile Erweiterung werden nicht still als fertig behauptet; Umfang folgt Spec und Abnahmefällen.

## Vorbereitung — Restore-Abgleich

Zeit: 2026-09-23T10:19:53.582688+00:00
Status: erfolgreich

- Tabellenzahlen aller 139 Tabellen stimmen mit dem gesicherten Quellmanifest überein; insgesamt 122821 Datensätze. Keine Datensatzinhalte ausgegeben.
- Nachweise und Zugangsdaten bleiben außerhalb Git. Der reguläre lokale Serverstart erfolgt erst nach Implementierung des kontrollierten Testprofils.

## Abschnitt 1 — Task 1 (Rolle: Coding-Agent)

Zeit: 2026-09-23T11:07:54Z
Branch: codex/beschaffung-task-1
Commit(s): 892a2501
Status: fertig

Was gemacht wurde:
- Einkaufsrechte in aktiven Benutzerprofilen ergänzt; Berechtigungsservice lädt Profil und Grants bei jeder Aktion frisch, Admins erhalten alle Rechte implizit. GET/PUT-Endpunkte nutzen bestehende Authentifizierung, Admin-Einstellungsschutz und CSRF.
- V377 ergänzt die profilbezogene native ENUM-Granttabelle sowie den Audit-Speicher. Audit-Schreibzugriffe laufen nur innerhalb einer bestehenden Fachtransaktion.
- Security- und Service-Tests decken anonymous 401, fehlende Rechte 403, Admin-Vollzugriff, Sessionrollen gegen aktuellen Profilstand, inaktive Profile, gefälschte Request-IDs und CSRF ab.
- Root hat den Taskscope zusätzlich um den vorbestehenden PC-Unit-Testfehler erweitert: jsdom fehlten DOM-Geometriemethoden für ProseMirror. Gemeinsame Range-/elementFromPoint-Stubs plus Regressionstest beseitigen den unhandled Fehler; das bisher lokale Duplikat wurde entfernt.
- Verifikation: fokussiert 10 Backendtests grün; vollständiges Backend 3156 Tests, 0 Fehler, 17 bestehende Skips. PC 141 Dateien/1629 Tests grün, Lint und Build grün. PC-E2E Port 5182: 669 passed; Mobile-E2E Port 5183: 24 passed. Mobile Unit/Lint/Build gemäß Task2-Nachweis auf unverändertem Frontend grün. Graphify aktualisiert.
- Logs: `/tmp/beschaffung-task1-targeted.log`, `/tmp/beschaffung-task1-full-maven.log`, `/tmp/beschaffung-task1-pc-unit-final2.log`, `/tmp/beschaffung-task1-pc-lint-final2.log`, `/tmp/beschaffung-task1-pc-build-final2.log`, `/tmp/beschaffung-task1-pc-e2e.log`, `/tmp/beschaffung-task1-mobile-e2e.log`.

Bedenken / Abweichungen:
- `./graphify` fehlte anfangs im Worktree; nach Bereitstellung des Symlinks wurde das Update erfolgreich ausgeführt. Graphify meldete eine Syntax-Extraktionswarnung in der unveränderten `react-pc-frontend/src/components/TiptapEditor.tsx`.
- Gemeinsame Testsetup-Dateien wurden nur auf Root-Anweisung in den Task aufgenommen. Keine Frontend-Produktdateien geändert.

## Abschnitt 1 — gestartet

Zeit: 2026-09-23T10:47:22.017763+00:00
Basis: 1550057e

- Plan und Spec nach begrenztem Sol-Vorabreview grün, Planung committet.
- Task1 und Task2 laufen auf GPT-6 Luna in getrennten Worktrees. Task38-Worktree vorbereitet; Agentenstart folgt, sobald ein Threadfenster frei wird (Tool meldet aktuell Threadlimit trotz abgeschlossenem Altplaner).
- Historische Migrationsvorbereitung: 13 fehlende Dateien außerhalb Git aus belegten Gitständen mit passenden Prüfsummen rekonstruiert. V208/V209 fehlen auch in Gitgeschichte und historischem Servercheck-JAR; V254-Checksum0 bleibt unbelegt. Kein DB-Metadatenupdate, kein blanket repair. Task39 übernimmt den gezielten lokalen Abgleich nach Snapshot.

## Abschnitt 1 — Task 2 (Orchestrator-Abschluss nach Coding-Agent)

Zeit: 2026-09-23T11:07:48.459670+00:00
Branch: codex/beschaffung-task-2
Commit: 25141ffa
Status: implementiert, Abschnittsreview ausstehend

- Luna implementierte transaktionsunabhängige atomare PA-/B-/Verkaufsnummern mit MySQL-Upsert und Zeilensperre. Echter MySQL-Test für30parallele Erstzugriffe und Außenrollback grün.
- Testcontainers1.21.4 explizit einschließlich Core erforderlich wegen Spring-Boot-BOM und DockerEngine29; Tests mit DOCKER_HOST=unix:///Users/marvinkuhn/.docker/run/docker.sock.
- Eigene volle Backend-Suite3149Tests, keine Fehler,17bestehende Skips; PC1628/Mobile346Unit, Lint/Build grün.
- Gemeinsame E2E-Nachweise identischer unveränderter Produktfrontends: Task1 PC669/Mobile24 grün. Logs /tmp/beschaffung-task1-pc-e2e.log und /tmp/beschaffung-task1-mobile-e2e.log. Task1 behebt zusätzlich einen sporadischen JSDOM-Range-Fehler und hat PC1629fehlerfrei bestätigt.
- Root übernahm nach gemeinsamem E2E-Grün den expliziten Task2-Commit; keine Graphify-Ausgaben oder lokalen Symlinks gestaged.

## Task 1 — Nachbesserung (erster Code-Review-Befund)

Zeit: 2026-09-23T11:13:55Z
Branch: codex/beschaffung-task-1
Commit(s): ecd16e04
Status: fertig

Was gemacht wurde:
- Adminrechte für `GET/PUT /api/settings/einkauf-berechtigungen/{profileId}` werden jetzt im Service bei jeder Anfrage anhand des aktuellen `FrontendUserProfile` im Principal erneut geprüft. Der Aufrufer muss aktiv sein und aktuell die Rolle ADMIN besitzen; Sessionrollen allein reichen nicht.
- Regressionstests decken eine herabgestufte, aber alte ADMIN-Session, ein deaktiviertes Adminprofil und einen aktiven aktuellen Admin ab.
- Verifikation: gezielte Service-/Securitytests 13/13 grün; volle Backendsuite 3159 Tests, 0 Fehler, 17 Skips. Log: `/tmp/beschaffung-task1-review-full-maven.log`.

Bedenken / Abweichungen:
- keine. Root-Kontextlog aktualisiert; keine Worktree-Kontextlogkopie geändert.

## Abschnitt 1 — Review und zweite Nachbesserung

Zeit: 2026-09-23T11:31:18.865956+00:00
Status: Task1/2 integriert und grün; Task38 zweite Nachbesserung läuft

- Sol schloss Task1-Stale-Admin-Befund nach ecd16e04. Kombinierter Backendtest Task1+2:3162 Tests,0Fehler,17bestehende Skips. Nummern-Merge d2505ac.
- Task38-Scope um servererreichbare direkte Mail-/Kontotest-/Archivierungspfade erweitert, vollständig im Plan dokumentiert; Produktionsverhalten bleibt außerhalb local-test erhalten. Policy regulär injiziert, kein globaler Zustand.
- Frühe Guard-Befunde (relaxierte Hikari-Unterproperties, effektives SSL, Bootstrap bei alternativer Profilaktivierung) in laufender Task38-Korrektur geschlossen. Eigene volle Task38-Backendsuite danach3162Tests fehlerfrei.
- Gebündelter Sol-Gesamtbefund für zweite Abschnitts-Nachbesserung: UnifiedEmailController.replyToEmail und LieferantenController.sendEmail verwenden noch Policy-losen EmailService-Konstruktor. Beide API-Pfade plus Regressionstests werden abgesichert. Zusätzlich gelbe Boot-ConfigData-/AFTER_COMMIT-Async-Testlücken schließen. Danach vollständiger Recheck; Skillgrenze maximal2Nachbesserungen beachten.

## Task 38 — zweite Nachbesserung

Zeit: 2026-09-23T11:45:00Z
Branch: codex/beschaffung-task-38
Commit(s): dc293d8c
Status: fertig

Was gemacht wurde:
- Lokales Testprofil mit fail-closed Datenbank-Guard nach ConfigData, Loopback-Bindung, TLS-/Hikari-/Flyway-Override-Prüfungen, deaktiviertem Scheduling/Startup-Maintenance und weiterhin verfügbarem Async eingerichtet.
- Injizierte LocalTestMailPolicy auf Spring-Mail-, Anbieter-, Import-, Test-, Archivierungs- und direkten Controllerpfaden durchgezogen. Alle EmailService-Konstruktionen erfordern jetzt eine Policy; Einkauf ist nur mit Opt-in erlaubt, HAUPT und DOKUMENTE bleiben gesperrt.
- Sol-Befunde für die Umgehungen in UnifiedEmailController.replyToEmail und LieferantenController.sendEmail mit Controller-Regressionen geschlossen. Zusätzliche Tests für echten Spring-CLI-Guardstart, Environment-/Hikari-Overrides und AFTER_COMMIT-Async bei Commit/Rollback ergänzt.
- Zieltests 82/82 grün. Kombinierter sauberer Backendlauf mit DOCKER_HOST: 3184 Tests, 0 Fehler, 17 Skips. Graphify aktualisiert; Frontend unverändert, gemeinsame Nachweise durch Root.

Bedenken / Abweichungen vom Plan:
- Scope wie vom Root genehmigt auf sämtliche servererreichbaren Mailpfade erweitert. Keine weiteren Abweichungen.

Berichtigung Task 38: Der Zeitstempel im unmittelbar vorherigen Block ist versehentlich in die Zukunft gesetzt worden. Tatsächlicher Append-Zeitpunkt: 2026-09-23T11:42:01Z.

## Abschnitt 1 — Review-Agent

Zeit: 2026-09-23T11:42:15Z
Branch: codex/beschaffung-konzept (geprüfter Stand codex/beschaffung-task-38)
Commit(s): ecd16e04, 25141ffa, d2505acb, dc293d8c
Status: fertig
Ampel: 🟢

Was gemacht wurde:
- Tasks 1, 2 und 38 auf kombiniertem Stand geprüft; Berechtigungen, Audit, atomare Nummernvergabe, lokaler Datenbank- und Mail-Netzschutz kontrolliert.
- Eigener vollständiger Backend-Test auf kombiniertem Stand: 3184 Tests, 0 Fehler, 17 bestehende Skips; MySQL-Testcontainer für parallele Nummernvergabe ausgeführt.
- Frühe kritische Befunde zu veralteter ADMIN-Session und zwei Mail-Versandpfaden wurden vor Abschluss behoben und erneut geprüft.

Bedenken / Abweichungen vom Plan:
- Keine verbleibenden kritischen Befunde. Der isolierte lokale Probebetrieb und spätere vollständige Einkaufsabläufe werden gemäß Plan in Task 39 beziehungsweise den Folgeabschnitten abgenommen.

## Abschnitt 2 — gestartet

Zeit: 2026-09-23T11:46:22.786541+00:00
Basis: b979ed74 (auf origin/codex/beschaffung-konzept gepusht)
Status: Tasks 3, 8 und 9 parallel auf GPT-6 Luna

- Abschnitt 1 ist nach zwei Nachbesserungsrunden Sol-grün und integriert. Keine offenen kritischen Befunde.
- Eigene Worktrees/Branches für technische Positionen und interne Artikelnummern (3), Lieferantenkontakte (8) sowie verschlüsselte Einkaufs-Mailkonten (9) vorbereitet.
- Zusätzlicher read-only Befund für Task 39: heutige V208-Schemanachbedingungen auf Clone vorhanden; V209 fordert nullable mitarbeiter_id/start_zeit, Clone hat noch NOT NULL. Vor lokalem Historienabgleich braucht es nach Snapshot eine belegte gezielte Angleichung dieser beiden Spalten. Externer Nachweis legacy-postconditions.json; keine Datenbankmutation.

## Abschnitt 2 — Task 8 (Rolle: Coding-Agent)

Zeit: 2026-09-23T00:00:00Z
Branch: codex/beschaffung-task-8
Commit(s): 07c29b40b7cda9473da32b23a28560e04e979354
Status: fertig

Was gemacht wurde:
- Strukturierte, versionierte Einkaufskontakte mit getrennten Standardkontakten für Anfrage und Bestellung; Änderungen sperren den Lieferanten, deaktivieren Kontakte statt sie zu löschen und schreiben Audit-Ereignisse.
- API mit aktuellen Einkaufsrechten sowie Empfängersnapshots mit explizit kopiertem Lieferanten, Kontakt, Name, Anrede, Adresse und eigener Kundennummer.
- Flyway-Migration V382 sowie Service- und Controller-Tests.
- Gezielte Tests: 6 bestanden. Backend komplett: 3190 bestanden, 17 übersprungen. Graphify synchronisiert; generierte Graphdateien nicht committet.

Bedenken / Abweichungen vom Plan:
- keine

Korrektur zu Zeitstempel Task 8: Der zuvor notierte Platzhalter `2026-09-23T00:00:00Z` war unzutreffend. Append-Zeitpunkt dieser Korrektur: `2026-09-23T11:55:47Z`.

## Abschnitt 2 — Task 3 (Rolle: Coding-Agent)

Zeit: 2026-09-23T11:58:26Z
Branch: codex/beschaffung-task-3
Commit(s): 33bf4ae002615db9368bdccfec26084ad6ea806d
Status: fertig

Was gemacht wurde:
- Gemeinsame Einkaufspositions-Records und die Positions-, Einheiten- und Dokumentarten ergänzt.
- Katalogpositionen werden anhand interner Artikel-ID und betrieblicher Artikelnummer eingefroren; fehlende interne Nummern bleiben als Nachpflegefall leer, Lieferantennummern werden nicht ersatzweise verwendet.
- Mengen/Technik validiert, Profilmeter aus Stückzahl mal Einzellänge berechnet, Teilmengen und stabile Bündelschlüssel umgesetzt.
- Artikel-Kurzanlage rückwärtskompatibel erweitert: explizite interne Nummer mit Konfliktprüfung, sonst kollisionsgeprüfte ART-ID-Nummer mit weiterem Suffix.
- Gezielte TDD-Fälle und vollständige Backend-Suite grün; Graphify aktualisiert. Keine Dienste gestartet.

Bedenken / Abweichungen vom Plan:
- Projektbezogene Kennung wird für Zeichnungsteile verlangt, kann in diesem rein technischen Task aber noch nicht über vorhandene Bedarfe hinweg auf Eindeutigkeit geprüft werden; Task 4 muss sie bei der persistenten Bedarfserstellung absichern.
- Anlagenversions-IDs werden als vorhandene Referenzen verlangt und normalisiert. Ihr Freigabestatus wird in Task 6 beziehungsweise vor Versand in Task 13 geprüft; Task 3 erfindet keinen Freigabestatus.

## Ablaufkorrektur — verbindliche Nutzersteuerung

Zeit: 2026-09-23T12:00:17.188905+00:00

- Nutzer beanstandet den Start von Review-Agenten vor Abschluss sämtlicher Coding-Tasks eines Abschnitts. Root hat den vorzeitigen Abschnitt-2-Review angehalten.
- Ab jetzt strikte Barriere: alle Coding-Agenten des Abschnitts fertig; Branches zusammenführen; erst anschließend Sol-Abschnittsreview starten. Keine Prüfung unfertiger Nachbar-Worktrees.
- Die frühen Abschnitt-2-Hinweise sind unbestätigte Vorbefunde, keine Abnahme und keine gezählte Nachbesserungsrunde. Abschnitt 2 beginnt seinen formalen Review erst auf dem vollständigen integrierten Stand.
- Abschnitt 1 wurde abschließend vollständig grün geprüft und bleibt übernommen.

## Abschnitt 2 — Task 9 (Rolle: Coding-Agent)

Zeit: 2026-09-23T12:06:18Z
Branch: codex/beschaffung-task-9
Commit(s): 3d3499c00b81a792602438570dd0c0396bfddb79
Status: fertig

Was gemacht wurde:
- Wiederverwendbare Mailkontoauflösung für HAUPT, DOKUMENTE und EINKAUF ergänzt; EINKAUF nutzt eigenen versionierten Speicher ohne stillen Fallback.
- SMTP- und IMAP-Zugänge getrennt validiert und Passwörter mit AES-GCM, zufälligem Nonce und versioniertem Ciphertext geschützt. Fehlender Schlüssel blockiert Zugangsspeicherung beziehungsweise aktive Konten; Antworten und toString geben nur Passwort-Präsenz aus.
- GET/PUT `/api/settings/einkauf-mail` mit aktiver ADMIN-Prüfung ergänzt. Die Prüfung liegt zentral in `EinkaufBerechtigungService`; bestehende Rechteverwaltung nutzt denselben Guard.
- V383 und gezielte Verschlüsselungs-, Resolver-, Aktivierungs- und Sicherheitsprüfungen hinzugefügt.
- Gezielte Tests bestanden (22), vollständige Backend-Suite bestanden (3197 Tests, 0 Fehler, 17 übersprungen); Graphify aktualisiert.

Bedenken / Abweichungen vom Plan:
- Keine. Netzwerk- und Testmail-Endpunkte bleiben wie vorgesehen Task 10.

## Abschnitt 2 — Review-Agent (Runde 0, integrierter Root-Stand)

Zeit: 2026-09-23T12:35:00Z
Branch: codex/beschaffung-konzept
Commit(s): 33bf4ae0, 07c29b40, 3d3499c0 (gemeinsam staged, Merge-Commit noch offen)
Status: blockiert
Ampel: 🔴

Was gemacht wurde:
- Den gemeinsam integrierten Root-Stand der Tasks 3, 8 und 9 gegen Plan, Spec, Architektur-, Test- und Sicherheitsvorgaben geprüft; keine Nachbar-Worktrees betrachtet.
- Backend vollständig auf Root getestet: `DOCKER_HOST=unix:///Users/marvinkuhn/.docker/run/docker.sock ./mvnw -B test`, BUILD SUCCESS, 3215 Tests, 0 Fehler, 17 übersprungen. `git diff --cached --check` sauber. Frontends unverändert; vorhandene grüne PC-/Mobile-Nachweise bleiben gültig.
- Integrationsgate bestätigt: Task 3 prüft technische Identität und Anlagenreferenzen. Task 4 muss Projektzuordnung und eindeutige projektbezogene Kennung sicherstellen, Task 6 freigegebene Anlagenversionen nachweisen; Task 13 muss beides zwingend vor Versand prüfen. Positive ID allein ist keine Freigabe.

Kritische Befunde:
- `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufMailkontoController.java:27` / `service/mail/MailkontoService.java:48-53,86-107`: PUT leitet Eingabe- und Versionsfehler als `IllegalArgumentException` bzw. `IllegalStateException` weiter. Der globale RestExceptionHandler behandelt beide Typen nicht; reale API antwortet 500 statt 400 bzw. 409. Explizite Statuszuordnung und HTTP-Tests ergänzen.
- `src/main/java/org/example/kalkulationsprogramm/service/mail/MailkontoService.java:83`: Bei vorhandenem Konto wird ein verwaltetes `@Version`-Entity mit `repository.save` zurückgegeben und die Response vor Transaktions-Flush gebaut. Der erhöhte Versionswert entsteht erst beim Flush/Commit; PUT liefert daher die alte Version, die unmittelbar folgende Änderung scheitert. Vor Response flushen und Version im Persistenztest prüfen.

Hinweise:
- `src/main/java/org/example/kalkulationsprogramm/service/einkauf/LieferantEinkaufKontaktService.java:107-116`: Beim Wechsel des Standardkontakts werden auch Kontakte mit bereits `false` gesetztem Standard erneut gespeichert und auditiert; auf tatsächlich geänderte Kontakte begrenzen.

Korrektur zum Zeitstempel des unmittelbar vorstehenden Abschnitt-2-Review-Blocks: Der angegebene Zeitpunkt 12:35:00Z war ein Schreibfehler. Der tatsächliche Append-Zeitpunkt war 2026-09-23T12:11:22Z.

## Abschnitt 2 — erste formale Nachbesserungsrunde

Zeit: 2026-09-23T12:14:43.705594+00:00
Status: Korrekturen Task 8 und Task 9 laufen; Review ruht bis beide fertig und integriert

- Alle drei Coding-Tasks abgeschlossen und per Octopus-Merge ohne Commit zusammengeführt; anschließend frischer vollständiger Sol-Review auf Root.
- Regulärer Review Runde 0: Backend 3215 Tests ohne Fehler, 17 bekannte Skips, trotzdem rote Fachbefunde in Task 9: API-Status 500 statt 400/409 und veraltete Antwortversion vor Flush.
- Task 9 behebt beide Fehler mit HTTP- und echtem Persistenztest. Task 8 behebt parallel den gelben Hinweis unnötiger Saves/Audits unveränderter Kontakte.
- Nächster Review erst nach Abschluss beider Coding-Agenten und erneuter Zusammenführung der korrigierten Branches.

## Abschnitt 2 — Task 8 Nachbesserung (Rolle: Coding-Agent)

Zeit: 2026-09-23T12:15:59Z
Branch: codex/beschaffung-task-8
Commit(s): ed064e7193fd99564cfd897daaf957649658ccf8
Status: fertig

Was gemacht wurde:
- Standardwechsel speichert und auditiert nur Kontakte, deren Standardflag für den betroffenen Zweck tatsächlich gesetzt war.
- Regression A→B bestätigt: Nur IDs 1 und 2 werden gespeichert/auditiert; ein unveränderter Kontakt und sein Bestellstandard bleiben unberührt.
- Gezielte Tests: 7 bestanden. Backend komplett: 3191 bestanden, 17 übersprungen. Graphify aktualisiert.

Bedenken / Abweichungen vom Plan:
- keine

## Abschnitt 2 — Task 9 (Rolle: Coding-Agent, Nachbesserung Review R0)

Zeit: 2026-09-23T15:30:00Z
Branch: codex/beschaffung-task-9
Commit(s): 92ee387a
Status: fertig

Was gemacht wurde:
- Mailkonto-HTTP-Fehler werden für ungültige Eingaben als 400 und Versions-/Statuskonflikte als 409 mit generischen, geheimnisfreien Meldungen beantwortet; die gemeinsame Advice-Registrierung deckt nun beide Einkauf-Controller ab.
- Mailkonto-Speicherung flusht vor dem Erzeugen der Antwort und wandelt einen echten Optimistic-Lock-Fehler in den sicheren Konfliktstatus um.
- Ergänzt: HTTP-Regressionen für 400/409 ohne Provider-Secret-Leak und H2-Persistenztests für Antwortversion → direktes Folgeupdate sowie echte stale-version-Kollision.
- Erweiterte Task9-Tests: 26 fokussierte Tests grün. Vollständige Backend-Suite: 3201 Tests, 0 Fehler, 17 übersprungen; BUILD SUCCESS. Graphify synchronisiert (bekannte TiptapEditor-Syntaxwarnung außerhalb des Task9-Bereichs).
- Gemeinsame aktuelle ADMIN-Prüfung wurde bereits in dieser Task-9-Umsetzung zentral über EinkaufBerechtigungService.verlangeAktivenAdmin wiederverwendet; die jetzige Nachbesserung hat diesen Pfad nicht verändert.

Bedenken / Abweichungen vom Plan:
- Keine. Frontend unverändert. Keine Dienste gestartet.

## Abschnitt 2 — Review-Agent (Nachbesserungsrunde 1, integrierter Root-Stand)

Zeit: 2026-09-23T12:27:56Z
Branch: codex/beschaffung-konzept
Commit(s): 33bf4ae0, ed064e7193fd99564cfd897daaf957649658ccf8, 92ee387a (gemeinsam staged, Merge-Commit noch offen)
Status: fertig
Ampel: 🟢

Was gemacht wurde:
- Nach abgeschlossenen Coding-Tasks den erneut integrierten Root-Stand geprüft; keine Nachbar-Worktrees oder Produktcode-Änderungen.
- Beide formalen Task-9-Blocker behoben bestätigt: Mailkonto-PUT liefert bei ungültiger Eingabe HTTP 400 und bei Versionskonflikt HTTP 409 ohne Credential-Details; saveAndFlush liefert die persistierte Antwortversion, die ein Folge-PUT verwenden kann. HTTP- und JPA-Persistenztests liegen vor.
- Task-8-Hinweis behoben bestätigt: Der Standardwechsel speichert und auditiert nur Kontakte, deren Standard-Flag tatsächlich geändert wird.
- Backend auf Root vollständig getestet: DOCKER_HOST=unix:///Users/marvinkuhn/.docker/run/docker.sock ./mvnw -B test, BUILD SUCCESS, 3220 Tests, 0 Fehler, 17 bekannte Skips. git diff --cached --check sauber. Frontends unverändert; vollständige grüne PC-/Mobile-Nachweise der geprüften Basis bleiben gültig.
- Integrationsgate bleibt verbindlich: Task 4 prüft Projektzuordnung und Eindeutigkeit der Zeichnungsteilkennung, Task 6 freigegebene Anlagenversionen, Task 13 beide Nachweise zwingend vor Versand.

Bedenken / Abweichungen vom Plan:
- Keine kritischen Befunde oder Hinweise in dieser Runde.


## Abschnitt 2 — abgenommen nach Nachbesserung 1

- Alle Coding-Agenten waren vor Reintegration und erneutem Sol-Review vollständig fertig; die korrigierte Reihenfolge ist im Plan explizit festgehalten.
- Integriert: Task3 `33bf4ae0`, Task8 einschließlich `ed064e71`, Task9 einschließlich `92ee387a`.
- Sol-Endreview: GRÜN, keine kritischen Befunde oder Hinweise. Eigener vollständiger Root-Backendlauf: 3220 Tests, 0 Fehler, 17 bekannte unveränderte Skips. Unveränderte Frontendtrees behalten die vollständigen grünen Unit-/E2E-/Lint-/Build-Nachweise.
- Gegenstand: gemeinsame Positions-/Identitätsvalidierung, Lieferantenkontakte und geschützte Mailkontoeinstellungen. Projekt-/Anlagenintegration folgt verbindlich in Tasks4/6/13.
- Nächster Abschnitt: Tasks4,10,12 erst auf dem übernommenen geprüften Feature-HEAD.

Korrektur zum Task-9-Nachbesserungszeitstempel: `15:30:00Z` ist unzutreffend. Der belegte Commitzeitpunkt von `92ee387a` ist `2026-09-23T14:25:35+02:00` (12:25:35Z); der genaue spätere Appendzeitpunkt wurde nicht verlässlich erfasst.
