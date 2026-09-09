# Plan: Zeitkonto-Vorlagen und Historisierung

Issues: #98 / #94 / #95 / #96 / #93
Feature-Branch: feature/zeitkonto-vorlagen-und-historisierung (bestehend; beibehalten)
Kontext-Log: /Users/marvinkuhn/Documents/GitHub/ERP-System-fuer-Handwerksbetriebe/docs/superpowers/plans/2026-09-09-zeitkonto-vorlagen-und-historisierung-log.md (bestehend; nicht überschreiben)

## Global Constraints

- Maximal drei Tasks pro Runde; Schreibbereiche innerhalb jeder Runde strikt disjunkt, zugehörige Tests beim jeweiligen Dateieigentümer.
- Jede Runde vollständig zusammenführen und reviewen, bevor abhängige Tasks starten. Schema ausdrücklich reviewen, bevor Runde 2 beginnt. Alle Task-Branches/Worktrees starten vom geprüften, zusammengeführten Feature-Branch der vorherigen Runde; Task 1 bleibt auf codex/zeitkonto-schema.
- Graphify vor weiteren Code-Suchen; BACKEND_ARCH.md, FRONTEND_UI.md und TESTING_SECURITY.md beachten. Frontend: Design-Skill und Playwright-Prüfung.
- Dateien unten relativ zum Hauptrepo /Users/marvinkuhn/Documents/GitHub/ERP-System-fuer-Handwerksbetriebe; Worktrees und Branches sind pro Task konkret angegeben. In jedem Worktree gelten dieselben relativen Dateipfade.
- Java-Basis B = src/main/java/org/example/kalkulationsprogramm; Testbasis T = src/test/java/org/example/kalkulationsprogramm.
- Testbesitz folgt expliziten Übergaben: Task 3 besitzt nur TagesSollServiceTest, ZeitkontoServiceTest und TagesSollCharakterisierungZeitkontoTest. Übrige Charakterisierungstests gehören nach geprüftem Aufrufer exklusiv Task 5/6/7; Mehrverbraucher-Tests Task 5. Vor Runde 3 konkrete Testdateiliste am Gate festhalten, keine gemeinsame Fixture-Datei parallel ändern.
- Gemeinsame PC-Typen/API-Dateien, falls erforderlich, vor Runde 4 im Task-5-Gate konkret benennen, Task 5 zuweisen und geprüft bereitstellen. Task 8 konsumiert keine erst parallel in Task 9 entstehenden Dateien; bestehende Komponenten nicht zusätzlich auslagern.
- Historisches Soll liest gültige Versionen unabhängig von fuehrtZeitkonto. Das aktuelle Flag steuert heutige UI, neue Stempelstarts und Warmup.
- Ausschalten schließt die offene Version. Wiedereinschalten erfordert explizite Arbeitszeitzuweisung und eine neue Version ab heute.
- Lücken ausschließlich für persistent dokumentierte Zeit ohne Konto zulassen; keine pauschale Lückenfreigabe. Gleichzeitige/gleichdatierte Toggle-Operationen konsistent validieren.
- Abwesenheitsgutschriften niemals umrechnen; Wechsel in offenen Monaten warnt vor bestehenden Snapshots. Historisierung schützt auch offene Altmonate.
- Nur vergangene Monate regulär abschließen; laufende/zukünftige Monate mit verständlichem 409 ablehnen. Abschluss bewusst im Kalender/Monatsbereich.
- Akteur ausschließlich FrontendUserPrincipal.id -> FrontendUserProfileRepository -> Mitarbeiter/Abteilungen. Übermittelte MitarbeiterId bezeichnet nur Zielpersonen.
- Abschlussflag Default false, auch Admin ohne Bypass. Berechtigungseditor-API insbesondere PUT administrativ schützen. Abschluss/Öffnung dauerhaft mit Akteur/Zeit protokollieren.
- Neue Aggregate mit @Version; parallele Erstzuweisungen zusätzlich je Mitarbeiter serialisieren. SYSTEM führt kein Zeitkonto.
- Bestehende Salden durch Migration nicht verändern; Alttabelle bis zum letzten Umbau erhalten. Keine GoBD-Garantie behaupten.
- Code-Commits und Pipeline sind jetzt freigegeben; Produktion nicht automatisch deployen. Den Plan-Commit übernimmt der Elternagent. Keine zusätzliche Auslagerung bestehenden Codes ohne bereits vorliegende Freigabe.

## Runde 1 — Datenfundament (gestartet)

### Task 1: Schema und Migrationskopie
- Branch: codex/zeitkonto-schema
- Worktree: /Users/marvinkuhn/Documents/GitHub/ERP-System-fuer-Handwerksbetriebe/.claude/worktrees/zeitkonto-schema
- Files: B/domain/{MitarbeiterArt,Mitarbeiter,Abteilung,MonatsSaldo,MonatsabschlussAudit,ZeitkontoVersion,Zeitkontenmodell}.java; B/repository/{ZeitkontoVersionRepository,ZeitkontenmodellRepository,MonatsabschlussAuditRepository}.java; src/main/resources/db/migration/V368__*.sql und V369__*.sql; zugehörige Schema-/Migrationstests.
- Consumes: bestehendes Datenmodell und Spec; keine anderen Codingtasks.
- Produces: Entities, neue Repository-Signaturen, V368 Schema/Flags und V369 Migrationskopie; alte Zeitkonto-Tabelle bleibt erhalten. Keine Services/DTOs/Controller.
- Steps: SYSTEM false; native ENUMs, @Version einschließlich MonatsSaldo, Abschluss-/Auditfelder, kopierte Versionen und erforderliche Indizes bereitstellen.
- Prüfungen: MySQL-Migration/Hibernate-Validierung, unveränderte Bestandssalden, exakte Stundenkopie, keine Systemversion, keine automatisch erzeugten Vorlagen oder Abschlüsse.
- Gate: Schema-Agent liefert tatsächliche Repository-Signaturen für Versionsabfrage am Tag/im Zeitraum, Vorlagenzugriff/Referenzprüfung und Audit-Speichern/-Lesen; Elternagent reviewed Schema und Tests vor Runde 2. Methoden und Rückgabetypen im Übergabevertrag festhalten. Kontopausen bleiben ausschließlich Task 3/V370; erforderliche Task-1-Repository-Ergänzungen vor diesem Gate erledigen.

## Runde 2 — Drei disjunkte Backend-Pakete

### Task 2: Monatsabschluss, Rechte und Erinnerung (#98)
- Branch: codex/zeitkonto-task-2
- Worktree: /Users/marvinkuhn/Documents/GitHub/ERP-System-fuer-Handwerksbetriebe/.claude/worktrees/zeitkonto-task-2
- Files: B/service/{MonatsSaldoService,MonatsSaldoWarmupService}.java; B/repository/MonatsSaldoRepository.java; B/controller/{MonatsabschlussController,AbteilungBerechtigungController,NotificationController}.java; B/dto/{MonatsabschlussDto,AbteilungBerechtigungDto}.java; zugehörige Tests unter T.
- Consumes: reviewed Task 1, insbesondere Abschluss-/Audit-Schema und Repository-Signaturen; bestehende Principal-/Profilauflösung.
- Produces: Abschluss-/Öffnungs-API und Audit, geprüfte Rechte, Monatsstatus und Erinnerungskategorie für nachfolgende APIs/UI.
- Steps: Abschluss vor Live-/Cache-Weiche prüfen; alle Invalidierungen, Warmup und saveMonatsSaldoCache schützen; Abschluss/Öffnung atomar protokollieren. Bestehende Monatsberechnung zunächst erhalten, keine neuen Task-3-Signaturen oder Task-4-Filtermethoden konsumieren.
- Übergabe: Nach Review von Runde 2 gehen MonatsSaldoService.java und sämtliche zugehörigen Tests exklusiv an Task 5 zur vollständigen Versionenumstellung; Repository/Warmup-Verträge vorher vollständig bereitstellen.
- Steps: Berechtigungs-PUT admin schützen; Abschlussflag ohne Admin-Bypass prüfen; laufende/zukünftige Abschlussanfragen mit 409 beantworten.
- Steps: Erinnerung mengenorientiert auch für fehlende Cachezeilen ermitteln; Kandidatenzeitraum aus Eintritt/realen Daten, kein Kalender ab Jahr 1000; keine Mitarbeiter×Monate-Service-Schleife.
- Prüfungen: paralleler Abschluss/Cache-Write einschließlich erster Monatsanlage; alle Invalidierungspfade; gefälschter Akteur; Admin ohne Flag; fehlende Cachezeile; laufender Monat ohne Hinweis; Wiederöffnung.

### Task 3: Versionen, Vorlagen und Kontowechsel (#93/#95/#96)
- Branch: codex/zeitkonto-task-3
- Worktree: /Users/marvinkuhn/Documents/GitHub/ERP-System-fuer-Handwerksbetriebe/.claude/worktrees/zeitkonto-task-3
- Files: B/service/{ZeitkontoService,TagesSollService,ZeitkontenmodellService}.java; B/controller/ZeitkontenmodellController.java; B/dto/{ZeitkontoVersionDto,ZeitkontenmodellDto,ZeitkontoWechselDto}.java; B/domain/ZeitkontoPause.java; B/repository/ZeitkontoPauseRepository.java; src/main/resources/db/migration/V370__zeitkonto_pausen.sql. Tests ausschließlich TagesSollServiceTest, ZeitkontoServiceTest und TagesSollCharakterisierungZeitkontoTest unter T.
- Consumes: reviewed Task 1 mit ZeitkontoVersion/Zeitkontenmodell und deren Repository-Signaturen; ausschließlich bereits bestehende Invalidierungssignaturen, keine neuen Interfaces aus Task 2/4. Integrierten Abschluss-Schutz nach Zusammenführung von Runde 2 prüfen.
- Produces: ZeitkontoService.versionAm(id,tag): Optional<ZeitkontoVersion>; versionenImZeitraum(id,von,bis): List<ZeitkontoVersion>.
- Produces: TagesSollService künftig ALLE öffentlichen Berechnungsmethoden ohne Konto-Parameter: (id,tag) bzw. (id,von,bis), einschließlich Summen-/JeTag-Methoden.
- Produces: Katalog-CRUD, atomare Zuweisungs-/Toggle-Operationen, persistierte Kontopausen, Versionenkopie und Konfliktvertrag für Runde 3. ZeitkontoWechselDto samt nötiger Testanpassungen geht nach Runde 2 exklusiv an Task 5.
- Abgrenzung: Task 3 besitzt Katalog-CRUD und atomare Versionsoperationen; Task 5 besitzt selektive Vorlagenübernahme, Vorschau, Neuberechnung und Ergebnisvergleich samt Endpoints im ZeitverwaltungController. Task 5 benötigt keine Änderung am ZeitkontenmodellController.
- Steps: TagesSoll liest gültige Versionen direkt aus Repository, einmal je Zeitraum; kein Flagfilter auf historische Berechnung und keine zyklische Service-Abhängigkeit.
- Steps: Vorlagenwerte kopieren, Herkunft dokumentieren; Überschneidungen/veraltete Änderungen mit 409 abweisen. Nur dokumentierte Kontolücken erlauben; explizite Wiederzuweisung erzwingen.
- Steps: Neue Signaturen zunächst ergänzend bereitstellen; temporäre alte Überladungen nur bis zur vollständigen Aufruferumstellung. Endzustand besitzt keine Konto-Parameter.
- Prüfungen: Aus-/Einschalten verändert historisches Soll nicht; parallele Erstzuweisung; Stichtagswechsel; dokumentierte vs. unerlaubte Lücke; Feiertage/Wiedereingliederung; konstante Query-Zahl; Vorlagenlöschkonflikt.

### Task 4: Menschenfilter und Mitarbeiterverträge (#94/#95)
- Branch: codex/zeitkonto-task-4
- Worktree: /Users/marvinkuhn/Documents/GitHub/ERP-System-fuer-Handwerksbetriebe/.claude/worktrees/zeitkonto-task-4
- Files: B/repository/MitarbeiterRepository.java; B/service/{MitarbeiterService,AnfrageFunnelService,WebPushService,BelegService,SteuerberaterEmailProcessingService}.java; B/dto/Mitarbeiter/{MitarbeiterDto,MitarbeiterErstellenDto}.java; zugehörige Tests unter T.
- Consumes: reviewed Task 1, MitarbeiterArt und fuehrtZeitkonto; keine Interfaces aus gleichzeitig laufenden Tasks erforderlich.
- Produces: Menschen-/aktive-Menschen-Filter, vollständige DTO-Verträge, korrekte technische Mitarbeiteranlage.
- Steps: SYSTEM aus Menschenlisten entfernen, ausgeschiedene Menschen historisch erhalten, Funnel-Neuanlage als SYSTEM ohne Konto kennzeichnen.
- Steps: Kontoflag nicht unabhängig von einer Versionsoperation ändern; endgültige Schreibanbindung bewusst Task 5 zuweisen, keinen vorübergehend unsicheren Toggle freischalten. MitarbeiterService.java und zugehörige Tests nach Runde 2 exklusiv an Task 5 übergeben.
- Prüfungen: Listen/Zuordnung und Funnel mit Bestands-/Neuanlage; SYSTEM ausgeschlossen; ausgeschiedene Menschen sichtbar; DTO-Defaults korrekt.

## Runde 3 — Verbraucher vollständig umstellen

### Task 5: Zeitverwaltung, mobile API und Wechselworkflow
- Branch: codex/zeitkonto-task-5
- Worktree: /Users/marvinkuhn/Documents/GitHub/ERP-System-fuer-Handwerksbetriebe/.claude/worktrees/zeitkonto-task-5
- Files: B/controller/ZeitverwaltungController.java; B/service/{ZeiterfassungApiService,MitarbeiterService,MonatsSaldoService,ZeitkontoWechselService}.java; B/dto/{ZeitkontoStatusDto,ZeitkontoWechselDto}.java; zugehörige Tests unter T einschließlich übergebener MonatsSaldoService-/MitarbeiterService-/Wechsel-DTO-Tests und Charakterisierungstests dieser Aufrufer sowie Mehrverbraucher-Tests; gemeinsame PC-Vertragsdateien nur nach konkreter Gate-Zuordnung.
- Consumes: reviewed Tasks 2–4: Abschluss-/Rechtevertrag und geschützter MonatsSaldoService samt Tests, neue TagesSoll-Signaturen, versionAm/versionenImZeitraum, atomare Toggle-/Zuweisung, Kontopausen, Katalog-API, ZeitkontoWechselDto und Mitarbeiter-DTOs.
- Steps: MonatsSaldoService vollständig auf versionierte Auflösung/neue TagesSoll-Signaturen umstellen; alten Kontoaufruf entfernen, Abschluss-/Cache-Schutz unverändert wirksam halten und übergebene Tests anpassen. Keine Abhängigkeit auf parallel entstehende Task-6/7-Änderungen.
- Produces: schreibfreie Kontoabfragen, vollständig versionierter MonatsSaldoService, mobile Status-/Saldoverträge, Wechselvorschau/-übernahme einschließlich selektiver Vorlagenübernahme und tatsächlichem Änderungsergebnis; Endpoints dafür ausschließlich im ZeitverwaltungController.
- Gate vor Runde 4: Task 5 erzeugt und finalisiert die DTO-Verträge und, falls benötigt, die konkret zugewiesenen gemeinsamen PC-Typen/API-Dateien; alles vor Start von Task 8/9 zusammenführen und prüfen.
- Steps: getSaldo um bisher Zeile 1090: bei ms.festgeschrieben Randmonats-Rechenpfad umgehen und feste Summen verwenden, auch bei Eintritt nicht am Monatsersten.
- Steps: geprüft bis endet vor erster Abschlusslücke; offene Salden und Urlaub bleiben live sichtbar; kein Anzeige-Gate durch fehlenden Abschluss.
- Steps: Mitarbeiter-Toggle an atomare Versionsoperation anbinden; Wiederaktivierung ohne explizite Arbeitszeit ablehnen; neue Starts ohne gültiges Konto ablehnen, bestehende Buchungen weiterhin beenden können.
- Steps: Vorschau ohne Schreibeffekte; Übernahme validiert Versionsstände erneut, warnt vor Abwesenheitssnapshots und invalidiert offene Monate über invalidiereAlle. Ergebnis zeigt tatsächlich veränderte und geschützt gebliebene Monate.
- Prüfungen: kein GET erzeugt ein Konto; fester Randmonat; lückenhafte Abschlüsse; veraltete Vorschau 409; Toggle-Historie; Offline-Nachlieferung und laufende Buchung.

### Task 6: Abwesenheiten und Langzeitkrankheit
- Branch: codex/zeitkonto-task-6
- Worktree: /Users/marvinkuhn/Documents/GitHub/ERP-System-fuer-Handwerksbetriebe/.claude/worktrees/zeitkonto-task-6
- Files: B/service/{AbwesenheitService,UrlaubsantragService,LangzeitkrankmeldungService}.java; zugehörige Tests und exklusiv diesen Aufrufern zugeordnete Charakterisierungstests unter T (konkrete Liste am Runde-3-Gate; keine Task-3-/Mehrverbraucher-Tests).
- Consumes: reviewed Task 3, neue TagesSoll-Signaturen ohne Konto-Parameter und versionierte Leseschnittstellen.
- Produces: datumsbezogene Sollauflösung ohne implizite Konten.
- Steps: alle bisherigen Kontoaufrufe ersetzen; erforderliche Version für neue sollabhängige Buchungen validieren; historische Snapshots unverändert lesen.
- Prüfungen: gebuchter Urlaub vor Wechsel bleibt unverändert; historisches Soll trotz aktuellem false; fehlende Version mit fachlicher Antwort; Langzeitkrankheit. Charakterisierungserwartungen erhalten, nur Typen/Fixtures anpassen.

### Task 7: Auto-Stop und Verrechnungslohn
- Branch: codex/zeitkonto-task-7
- Worktree: /Users/marvinkuhn/Documents/GitHub/ERP-System-fuer-Handwerksbetriebe/.claude/worktrees/zeitkonto-task-7
- Files: B/service/{ZeitbuchungAutoStopService,VerrechnungslohnService}.java; zugehörige Tests und exklusiv diesen Aufrufern zugeordnete Charakterisierungstests unter T (konkrete Liste am Runde-3-Gate; keine Task-3-/Mehrverbraucher-Tests).
- Consumes: reviewed Tasks 3/4, Versionen und Menschenfilter; neue TagesSoll-Signaturen nur soweit fachlich passend.
- Produces: Ersatz der direkten ZeitkontoRepository-Zugriffe dieser beiden Verbraucher.
- Steps: Buchungsfenster anhand der Version des Buchungstags; offene Buchungen nach Deaktivierung nicht verwaisen lassen. Kalkulation zeitabschnittsweise auf Versionen umstellen, SYSTEM ausschließen.
- Prüfungen: Auto-Stop über Mitternacht/Stichtag; historische Versionen bei aktuellem false; gesonderte Kalkulationsregeln nicht unbeabsichtigt durch TagesSoll-Regeln ersetzen.

## Runde 4 — Drei disjunkte Frontend-Pakete

### Task 8: Mitarbeiterstamm
- Branch: codex/zeitkonto-task-8
- Worktree: /Users/marvinkuhn/Documents/GitHub/ERP-System-fuer-Handwerksbetriebe/.claude/worktrees/zeitkonto-task-8
- Files: react-pc-frontend/src/pages/MitarbeiterEditor.tsx und zugehörige Tests.
- Consumes: reviewed Runde 3, insbesondere Task 5 Status-/Toggle-/Wechselvertrag und Katalog-API aus Task 3; erforderliche gemeinsame PC-Typen/API-Dateien bereits am Task-5-Gate geprüft.
- Produces: Arbeitszeit erfassen, explizite Wiederzuweisung, Vorlagen, Historie, Abweichung und Wechselwarnung.
- Prüfungen: kein stilles Standardkonto; fehlende Einrichtung sichtbar; Aus-/Einschalten mit Arbeitszeitzuweisung; Vorher-/Nachher-Ergebnis; Konflikt-/Fehleranzeige; Vitest, Build und Playwright.

### Task 9: Desktop-Zeitverwaltung, Rechte und Glocke
- Branch: codex/zeitkonto-task-9
- Worktree: /Users/marvinkuhn/Documents/GitHub/ERP-System-fuer-Handwerksbetriebe/.claude/worktrees/zeitkonto-task-9
- Files: react-pc-frontend/src/pages/{ZeiterfassungZeitkonten,ZeiterfassungKalender,ZeiterfassungSteuerberater,AbteilungBerechtigungenEditor}.tsx; react-pc-frontend/src/components/layout/{NotificationBell.tsx,notification-helpers.ts}; zugehörige Tests.
- Consumes: reviewed Runde 3, Abschluss-/Rechte-/Erinnerungsvertrag aus Task 2, Katalog-API aus Task 3 und Wechsel-/Saldo-/selektiver Übernahmevertrag aus Task 5; erforderliche gemeinsame PC-Typen/API-Dateien bereits am Task-5-Gate geprüft.
- Produces: Vorlagenpflege/selektive Übernahme, bewusster Abschluss/Öffnung im Kalender, Audit, Rechte, Glocke und Exportfilter.
- Prüfungen: Abschlusslücken korrekt darstellen; keine laufenden/zukünftigen Abschlüsse; Deep-Link; fehlende Cachezeilen als Hinweise; Snapshot-Warnung; Export ohne SYSTEM/ausgeschaltete Mitarbeiter; Vitest, Build und Playwright.

### Task 10: Mobile App
- Branch: codex/zeitkonto-task-10
- Worktree: /Users/marvinkuhn/Documents/GitHub/ERP-System-fuer-Handwerksbetriebe/.claude/worktrees/zeitkonto-task-10
- Files: react-zeiterfassung/src/pages/{DashboardPage,ZeiterfassungPage,SaldenPage}.tsx und zugehörige Tests.
- Consumes: reviewed Task 5 Status-/Saldovertrag; vollständige Backend-Umstellung aus Runde 3.
- Produces: kontolose Stempel-/Saldoansichten einschließlich Direktaufrufen korrekt behandelt; Projekte/Belege/Notizen bleiben erreichbar.
- Prüfungen: fehlende Arbeitszeit erklären; heutiger Flag-/Versionsstatus bestimmt UI; gültiges Konto zeigt offene Salden und Urlaub live; geprüft bis überspringt keine Lücke; Vitest, Build und Playwright.

## Runde 5 — Altmodell entfernen und Gesamtprüfung

### Task 11: Endgültige Umstellung und Review
- Branch: codex/zeitkonto-task-11
- Worktree: /Users/marvinkuhn/Documents/GitHub/ERP-System-fuer-Handwerksbetriebe/.claude/worktrees/zeitkonto-task-11
- Files: B/domain/Zeitkonto.java und B/repository/ZeitkontoRepository.java entfernen; temporäre Kompatibilitätsmethoden in B/service/{ZeitkontoService,TagesSollService}.java entfernen; reservierte Drop-Migration src/main/resources/db/migration/V371__zeitkonto_altmodell_entfernen.sql (V370 ausschließlich Task 3); erforderliche Integrationstests; Kontext-Log nur ergänzen; abschließende Graphify-Artefakte.
- Consumes: alle reviewed Tasks 1–10; sämtliche Verbraucher und Frontends verwenden endgültige Verträge, insbesondere der vollständig migrierte MonatsSaldoService samt übergebenen Tests aus Task 5. Keine ausstehende Verbraucherumstellung in Cleanup verschieben.
- Produces: ausschließlich versioniertes Zeitkonto-Modell, vollständiger Prüfbericht und synchronisierter Graph.
- Steps: keine Produktionsreferenz auf alten Zeitkonto-Typ/Repository/getOrCreateZeitkonto; TagesSoll ausschließlich (id,tag)/(id,von,bis). Erst danach Alttabelle entfernen; keine Kompatibilitätssicht.
- Prüfungen: Migration mit Dummy-Bestand, unveränderte Salden/Metadaten, korrekte Versionskopien, SYSTEM false ohne Version.
- Prüfungen: Urlaub -> Abschluss -> Wechsel -> unveränderter Abschluss; offener Altmonat; deaktiviertes Konto mit unverändertem historischem Soll; dokumentierte Lücke -> explizite Wiederaktivierung.
- Prüfungen: Rechte, fehlende Cachezeilen, fester Randmonat, Abschlusslücken, parallele Cache-Writes und Snapshot-Warnung ausdrücklich abnehmen.
- Checks: ./mvnw test; npm test und npm run build in beiden Frontends; Playwright-End-to-End; git status/diff prüfen; ./graphify update . einmal abschließend.
- Baseline: Backend 2611 Tests, 0 Failures/4 Errors in AuditChainRepairIntegrationTest und AuditHashRoundtripDiagnoseTest (MySQL Driver gegen H2 URL); PC 1133 bestanden/18 Fehler LieferantDokumentModal (Blob.stream); Mobile 146 bestanden.
- Bekannte Baselinefehler sichtbar separat ausweisen, nicht als bestanden werten. Neue Fehler sind Regressionen; Baseline nicht stillschweigend als Ausnahmeliste erweitern.

## Begründete Detailentscheidungen

- Vorlagenlöschung bei jeder historischen Referenz sperren: Herkunft bleibt nachvollziehbar.
- Individuell Abweichende als Übernahmekandidaten anbieten, nichts automatisch vorauswählen: individuelle Werte werden nicht still überschrieben.
- Versionskorrekturen nur über weiteren zulässigen Stichtag; Toggle-Grenzfälle dürfen keine leeren oder überlappenden Intervalle erzeugen.
- Migrationsfallback ohne Eintritt/Buchung im Schema-Review explizit festlegen. Ein technischer historischer Ersatzwert darf niemals den Beginn der Notification-Kandidatenhistorie bestimmen.
- Keine automatische Kopplung an Lohnabrechnung; keine Rundungsregeln oder Abteilungs-Vorlagenvererbung.

## Log

Rundenstatus und Befunde im bestehenden Kontext-Log ergänzen, nicht überschreiben. Hier ausschließlich kurze Abschlusszusammenfassungen des Reviewers je Runde nachtragen.

## Abschluss der Implementierung am 09.09.2026

Runden 1–5 und Tasks 1–11 sind umgesetzt und integriert. Task 9 wurde zur disjunkten Bearbeitung in Kalender/Rechte/Glocke und Vorlagen/Export geteilt. Coding erfolgte nach Nutzerfestlegung mit GPT-5.6 Terra, unabhängige Reviews mit GPT-6 Astra.

Backend- und Frontend-Abschlussreview sind grün. Die erste Backend-Nachbesserung plus Integrationskorrektur sowie zwei Frontend-Nachbesserungsdurchgänge haben Vorschauwerte, Sperrreihenfolge, verspätete Antworten, Vorlagenherkunft, Abwesenheitswarnungen und historische Exportwerte abgesichert. Alle ursprünglichen Reviewbefunde sind erledigt. Bekannte Baseline-Testfehler und konkrete Nachweise stehen im Kontext-Log.
