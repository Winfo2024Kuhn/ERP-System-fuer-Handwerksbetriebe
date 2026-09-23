# Beschaffung – Implementierungsplan

> Umsetzung über die freigegebene loese-problem-Pipeline; Aufgaben einzeln testgetrieben bearbeiten. Der geprüfte Parallelplan gliedert 39 Tasks in 23 Abschnitte mit festen Branch-, Worktree- und E2E-Port-Zuordnungen. Keine erneute fachliche Freigabe erforderlich.

Issue: #167 (https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/167)
Feature-Branch: `codex/beschaffung-konzept` (bestehenden freigegebenen Branch weiterführen)
Ausgangsbranch: `codex/beschaffung-konzept`, untersuchter Produktcode `53718ad2`.
Spec: `docs/superpowers/specs/2026-09-22-beschaffung-konzept.md`, einschließlich genehmigtem Status und §14 vom 23.09.2026.
Kontext-Log: `docs/superpowers/plans/2026-09-23-beschaffung-implementierung-log.md` (bereits vorhanden; append-only weiterführen).

**Ziel:** Ein vollständiger Desktop-Einkaufsablauf von persistentem Bedarf über echte Mehrlieferantenanfragen, Vergleich und Bestellung bis zu Lieferung, Unterlagen und Rechnungsprüfung, lokal mit einer isolierten Datenbankkopie ausprobierbar.

**Architektur:** Einkaufsaggregate ergänzen Artikel, Projekte, Lieferanten und vorhandene Belegketten. Ein gemeinsamer Mengenservice schützt alle Dispositionsaktionen. Unveränderliche Revisionen, belegte Preisbestandteile und eine persistente Mail-Outbox verhindern historische Verfälschung und doppelte Bestellung. E-Mail-Center, Einstellungen, Textvorlageneditor, SMTP/IMAP-, PDF- und KI-Bausteine werden erweitert.

**Technik:** Java/Spring Boot/JPA, MySQL/Flyway, Jakarta Mail, OpenPDF/PDFBox, React/TypeScript, Vitest/Testing Library, Playwright. Für XLS/XLSX Apache POI hinzufügen; kein externes CAD-Programm erforderlich.

## Global Constraints

- Vor Änderungen `AGENTS.md`, `.agents/skills/loese-problem/references/kriterien.md`, passende `docs/agent instructions/docs/BACKEND_ARCH.md`, `FRONTEND_UI.md`, `TESTING_SECURITY.md` lesen. Frontend zusätzlich `handwerkerprogramm-design` und `playwright-design-pruefung`. Graphify vor jeder neuen Codebase-Suche; bekannte Pfade direkt lesen.
- Coding ausdrücklich **GPT-6 Luna**, Code-/Design-/Abschnittsreviews **GPT-6 Sol**. Alte Claude-/Sonnet-/Opus-Vorgaben in Skills werden durch diese Nutzerentscheidung ersetzt. Kein Merge von `feature/en1090-echeck`.
- Alle Tasks erhalten eigene fachliche Tests; zuerst einen konkret unten genannten roten Fall ausführen, dann implementieren, dann gezielt grün prüfen. Vor jedem Commit/Push/PR **vollständig** `./mvnw test`, Desktop `npm test -- --run`, `npm run lint`, `npm run test:e2e`, `npm run build`; Mobile `npm test -- --run`, `npm run lint`, `npm run test:e2e` ebenfalls vollständig; Builds in betroffenen Frontends, bei gemeinsamem API-Effekt auch Mobile-Build. Kein Ignorieren vorbestehender Fehler. Abschluss `/review-and-ship`; Reviewer pro Abschnitt, Designreview bei Frontend. Graphify einmal nach Produktcode-Änderungen synchronisieren. Keine Commits dieses reinen Planungs-Tasks.
- Nur eigene Dateien stagen; keine Fremdänderungen zurücksetzen. Keine Secrets, Dumps, echten Personendaten, `uploads/`, `.env`, Schlüssel oder `application-local.properties` committen. Vor Commit explizite Dateiliste und staged Diff prüfen. Dummy-Daten in automatisierten Tests.
- Constructor Injection; DTOs statt Entities; parametrisierte Queries; paginierte Listen und Batch-Fetch statt N+1. Lange PDF-/Mail-/KI-Arbeit in Jobs außerhalb von Request-/Mengen-Transaktionen. IDs und Ereignisse strukturiert loggen, keine Adressen, Texte, Kennwörter oder Dokumentinhalte.
- Bestehende Migrationen unverändert. Höchste auf diesem Stand: **V376**. Dieser Plan reserviert **V377–V395** eindeutig; vor erstem Coding aktuellen main abgleichen. Bei Kollision nur noch nicht angewandte neue Planmigrationen geschlossen umnummerieren und alle Tasks/Log aktualisieren. Keine alten Branchmigrationen übernehmen. MySQL-native ENUMs und mit Hibernate übereinstimmende Nullability/Precision; idempotente DDL entsprechend Bestandsmuster.
- Neue Einkaufs-APIs unter `/api/einkauf`; bestehende `/api/bestellungen`, `/api/bestellungen-uebersicht`, Kundenanfragen und Verkaufsnummern kompatibel erhalten. Für in das neue Modell übernommene Positionen verhindert der alte Flag-Endpunkt die Umgehung der Mengen-/Freigaberegeln mit 409 und verständlicher Weiterleitung. Dies ist eine dokumentierte Sicherheitspräzisierung, kein stilles Abschalten historischer Vorgänge.
- Rose/Slate und Handwerker-Sprache; `<DetailLayout>`, `<Select>` aus `select-custom.tsx`, `<DatePicker>`, gemeinsame Dialoge/Toasts. Texteingaben für Dezimalzahlen, de-DE, Null bei Fokus leeren, vollständige Validierung vor Übernahme. Kennnummern bleiben Text. `htmlSanitizer.ts` für HTML/URLs verwenden.
- Angebotswahl, PDF-Download, Druck und Anfrageversand decken keinen Bedarf. Kein Einkaufspfad schreibt ungeprüft `ArtikelInProjekt.preisProStueck`; geplante Kosten sind von Rechnungskosten getrennt. Stornoanfrage gibt nichts frei; erst bestätigtes Storno bzw. belegte Klärung.
- Keine IDS/Punchout-Anbindung, automatischen Lieferantenportale, KI-Bestellungen, Lagerbestandsverwaltung, Zuschnittoptimierung oder normative EN-1090-Automatik. Gesamtvergabe eines gewählten Pakets ist enthalten; Teilmengenherkunft und mehrere spätere Bestellbezüge sind vorbereitet. Automatische Optimierung/feine Teilvergabe-UI sowie mobile Einkaufsverwaltung/Wareneingangs-UI bleiben die ausdrücklich späteren Ausbauten. Desktop-Lieferung und Zeugnisprüfung sind hier vollständig enthalten.
- Reale Lieferantenmails erst durch den Nutzer im konfigurierten lokalen UI; Agenten testen SMTP/IMAP ausschließlich mit Dummy-Empfängern und lokalen Testdiensten. Produktionsserver und bestehende lokale Container/Volumes nicht verändern.

## Gemeinsame fachliche und technische Verträge

Diese Verträge sind Bestandteil jedes konsumierenden Tasks. In `dto/Einkauf` werden öffentliche Records als verschachtelte Records der benannten DTO-Klasse geführt, damit Dateien und Besitz eindeutig bleiben. In der Beschreibung steht `PositionSnapshot` kurz für `EinkaufPositionDto.PositionSnapshot`; entsprechend die anderen explizit genannten Records.

- `EinkaufPositionDto`: `Mengenbasis(BigDecimal menge, Einheit einheit, BigDecimal stueckzahl, BigDecimal einzelLaengeMm, BigDecimal kgJeMeter, String faktorQuelle)`; `DokumentSoll(Dokumentart art, String grundlage, String grundlageVersion, boolean fachlichBestaetigt)`; `PositionSnapshot(Positionsart art, Long artikelId, String interneReferenz, String zeichnungsnummer, String zeichnungsrevision, String bezeichnung, String werkstoff, String abmessung, Mengenbasis basis, String schnittForm, String winkelLinks, String winkelRechts, String bearbeitung, String oberflaeche, List<DokumentSoll> dokumente, List<Long> anlageVersionIds)`; `Herkunft(Long bedarfId, long version, BigDecimal menge)`; `Liefergruppe(String lieferadresse, LocalDate bedarfstermin, Long projektId, String lagerzweck)`.
- Enums `Positionsart { ARTIKEL, ZEICHNUNGSTEIL }`, `Einheit { STUECK, METER, KILOGRAMM, TONNE, QUADRATMETER }`, `Dokumentart { ZEUGNIS_2_1, ZEUGNIS_2_2, ZEUGNIS_3_1, ZEUGNIS_3_2, LEISTUNGSERKLAERUNG, CE_NACHWEIS }`. Profilstückzahl ist ganzzahlig; Mengen/Umrechnungsfaktoren DECIMAL(19,6), Geldbestandteile DECIMAL(19,6), Summen EUR auf 2 Stellen am ausgewiesenen Rechenschritt; bestehende Preishistorie weiterhin 4 Stellen.
- `EinkaufBedarfDto`: `Create(PositionSnapshot position, Liefergruppe liefergruppe, Long artikelInProjektId)`; `Update(long version, PositionSnapshot position, Liefergruppe liefergruppe)`; `Mengenstand(BigDecimal bedarf, BigDecimal lagergedeckt, BigDecimal angefragt, BigDecimal reserviert, BigDecimal bestellt, BigDecimal geliefert, BigDecimal storniert, BigDecimal ungedeckt, BigDecimal disponierbar)`; `Response(Long id, long version, PositionSnapshot position, Liefergruppe liefergruppe, Mengenstand mengen, boolean nachpflegeErforderlich, String historischerHinweis)`.
- Invariante: `ungedeckt = bedarf - lagergedeckt - bestellt`; `disponierbar = ungedeckt - reserviert`; alle >= 0. Bestellt ist aktive bestätigte Bestellmenge **einschließlich bereits gelieferter Anteile**. Geliefert ist zusätzliche Fortschrittsgröße, wird niemals noch einmal abgezogen. Storno vermindert aktive bestellt/reserviert und erhöht separate kumulative storniert. Angefragt wird aus jeweils aktueller Anfragefassung einmal je Herkunft berechnet, niemals pro Lieferant; es darf sich über mehrere Anfragen überlappen und reduziert keine offene Menge.
- Sperrreihenfolge für alle Mengenaktionen: Bedarfs-IDs aufsteigend `PESSIMISTIC_WRITE`, dann Bestellkopf. Erwartete `version` prüfen; atomar ändern und Audit hinzufügen. Kein SMTP/PDF/AI innerhalb dieser Transaktion. Mutation enthält UUID `idempotenzKey`; Wiederholung liefert dasselbe Ergebnis, anderer Payload unter demselben Key ergibt 409.
- `EinkaufKontaktDto.Snapshot(Long lieferantId, Long kontaktId, String lieferantenname, String email, String name, String anrede, String eigeneKundennummer)` wird beim Vorbereiten gespeichert. Nur ein Lieferant je Versand; kein CC anderer Lieferanten.
- Standardantwort für paginierte Listen `Page<T>` wie `LieferantArtikelpreisService.suche`; Validierungsfehler 400 mit `{message,fieldErrors}`, fehlend 404, Version/Status/Mengenkonflikt 409, Rechte 403. Uploadlimit 10 MiB/Datei, konfigurierbares RFC-MIME-Gesamtlimit initial 20 MiB einschließlich Base64-Overhead. Übergröße blockiert; Downloadpaket plus bewusst dokumentierter externer Übermittlungsbeleg ist die Alternative, niemals Anhänge still weglassen.
- Ein Versand-Snapshot umfasst Vorlagen-ID/-Version, unveränderten Betreff/HTML, Empfänger, Mailkonto-ID, Message-ID, Referenzheader, PDF- und Anlagenbytes/-Hash. Vorschau und Versand müssen exakt denselben Snapshot verwenden. Änderung erzeugt eine neue Vorschau; Freigabe prüft Hash und fachliche Versionen erneut.

## Recherche und verbindliche Integrationspunkte

Die genannten Dateien wurden direkt gelesen; Graphify `query`, `explain EmailImportService` und `path BestellungService ArtikelInProjekt` wurden genutzt. Zeilen beziehen sich auf den Ausgangsstand und sind Orientierung nach vorgelagerten Tasks.

| Bestand | Befund und Verwendung |
|---|---|
| `src/main/java/org/example/kalkulationsprogramm/service/BestellungService.java:22,38,75` | Offene AIP-Liste, altes Flag mit Preisumschreibung, externe Nummer. Für neue Mengen nicht als Bestellmodell verwenden. |
| `src/main/java/org/example/kalkulationsprogramm/service/ProjektManagementService.java:950` | Erzeugt AIP einschließlich Profilmenge, Zuschnitt und Lagerflag. Hier neue Bedarfe synchronisiert erzeugen. |
| `src/main/java/org/example/kalkulationsprogramm/mapper/ProjektMapper.java:99,249`; `react-pc-frontend/src/pages/ProjektEditor.tsx:1011` | Lagerkosten über AIP, Rechnungskosten separat. Teilentnahmen brauchen separaten DTO-Wert ohne Doppelsumme. |
| `src/main/java/org/example/kalkulationsprogramm/domain/Artikel.java:186`; `service/ArtikelService.java:77` | Interne Artikelnummer existiert, kurzer Create-Pfad setzt sie bisher nicht. Erweiterung erforderlich. |
| `src/main/java/org/example/kalkulationsprogramm/service/ArtikelDokumentService.java:108` | Dateigrenzen, bereinigter Name, Pfadschutz und Artikelsperre als Vorbild; CAD-Anlagen benötigen eigene erlaubte Formate und unveränderliche Revisionen. |
| `src/main/java/org/example/kalkulationsprogramm/domain/Lieferanten.java:51,75`; `controller/LieferantReklamationController.java:74` | Vertreter/Kundennummer/E-Mail-Adressen vorhanden, strukturierte Einkaufsadressaten fehlen; Reklamation wiederverwenden. |
| `src/main/java/org/example/kalkulationsprogramm/service/SystemSettingsService.java:137,230,270,295`; `controller/SystemSettingsController.java:236` | Dokumentkonto-Fassade und write-only Passwortanzeige als Kompatibilitätsvorbild. Einkaufsfehler nie mit Hauptkonto auffangen. |
| `src/main/java/org/example/email/EmailService.java:41,326,498`; `service/mail/SentMailArchiver.java:72,84` | In-Memory-Anlagen und SMTP/Sent getrennt nutzbar; SSL derzeit fest gesetzt, präzise Ergebniszustände fehlen. |
| `src/main/java/org/example/kalkulationsprogramm/service/EmailImportService.java:129,157,324,663`; `domain/Email.java:35,61`; `repository/EmailRepository.java:58,338` | Hauptkonto, globale Message-ID, Betreff-Fallback. Einkauf benötigt Kontokontext und strenge separate Zuordnung. |
| `src/main/java/org/example/kalkulationsprogramm/service/EmailTextTemplateService.java:45,78,129`; `domain/EmailTextTemplate.java:20`; `controller/EmailTextTemplateController.java:40,68` | Typ bisher unique; Rendering ersetzt fehlende Werte mit leer. Varianten und striktes Einkaufsrendering ergänzen, Verkaufsrendering kompatibel halten. |
| `src/main/java/org/example/kalkulationsprogramm/service/FormularTemplateService.java:160`; `domain/DokumentnummerCounter.java:7` | Monatsschlüssel unique, read/modify/write ohne Sperre. Atomare Zähleroperation mit eigenen Schlüsseln `PA-2026`/`B-2026`; Verkaufsformat bleibt. |
| `src/main/java/org/example/kalkulationsprogramm/service/PreisUebernahmeService.java:146,385`; `service/LieferantArtikelpreisService.java:77`; `domain/LieferantenArtikelPreise.java:115` | Einheitendeutung und historische Preisstände vorhanden; zusätzliche Scope/Gültigkeit/Quelle erforderlich. Gleichpreis darf neue Belegquelle nicht verschlucken. |
| `src/main/java/org/example/kalkulationsprogramm/service/GeminiDokumentAnalyseService.java:542` | Bestehender API-Client mit Bytes/MIME/Prompt; keine zweite Zugangskonfiguration. Einkaufsanalyse darf nicht den Rechnungs-Buchungspfad aufrufen. |
| `src/main/java/org/example/kalkulationsprogramm/controller/BestellungsUebersichtController.java:63,463`; `service/LieferantDokumentService.java:299,349` | Historische Belegketten und zentrale Kosten-/Projektzuordnung bleiben; neue Bestellreferenzen ergänzen. |
| `src/main/java/org/example/kalkulationsprogramm/config/SecurityConfig.java:179`; `service/MonatsabschlussBerechtigungService.java:19` | Settings ADMIN, übrige APIs bisher nur authenticated. Aktuellen Principal serverseitig auflösen, neue Einkaufsrechte explizit erzwingen. |
| `react-pc-frontend/src/App.tsx:83`; `components/layout/RibbonNav.tsx:95`; `pages/BestellungEditor.tsx:875` | Bedarf bereits unter `/bestellungen/bedarf`; bisherigen Maildialog/Flag ersetzen, echte Bestellungen ergänzen, Belegketten erhalten. |
| `react-pc-frontend/src/pages/EmailTextvorlagenEditor.tsx:615`; `components/settings/sections/EmailSettingsSection.tsx:59`; `pages/EmailCenter.tsx:97` | Bestehende zentralen Oberflächen erweitern. |
| `react-pc-frontend/src/components/DetailLayout.tsx:10`; `components/artikel/ArtikelSuche.tsx:151`; `components/artikel/ArtikelAuswahlDialog.tsx:75`; `lib/numberDrafts.ts:4` | Layout, Artikelsuche, Auswahl und Zahlenprüfung wiederverwenden. Fehlenden Einkaufspreis nicht wie Verkaufsdialog zu 0 machen. |
| `react-pc-frontend/e2e/lieferant-dokument-modal.spec.ts:1`; `playwright.config.ts:46` | Dummy-API-/Design-Testvorbild, 1440×900, 1536×960, 1920×1080. Zusätzlich neue echte Backend-E2E gegen leere Test-DB/Testmailserver. |

## Ausführungsplan und Review-Gates

**23 Abschnitte, 39 Tasks.** Jeder Abschnitt beginnt erst nach geprüftem Merge und Abnahme aller vorherigen Abschnitte. Maximal drei Coding-Agenten gleichzeitig; Coding **GPT-6 Luna**, sämtliche Code-/Design-/Abschnittsreviews **GPT-6 Sol**. Tasknummern bleiben stabil und geben keine Ausführungsreihenfolge vor.

Der vorhandene Featurebranch `codex/beschaffung-konzept` bleibt Integrationsbasis. Taskbranches werden beim jeweiligen Abschnitt vom dann geprüften Featurestand erstellt; kein Checkout von main und keine vorgezogene Erzeugung aller Worktrees. Kontextlog bleibt append-only. Die nachfolgende Planung erzeugt noch keine Taskbranches/Worktrees und keinen Commit.

| Abschnitt | Tasks | Voraussetzung |
|---|---|---|
| 1 | 1, 2, 38 | Bestehender Ausgangsstand |
| 2 | 3, 8, 9 | Abschnitte 1–1 geprüft und integriert |
| 3 | 4, 10, 12 | Abschnitte 1–2 geprüft und integriert |
| 4 | 5, 6, 11 | Abschnitte 1–3 geprüft und integriert |
| 5 | 7, 13, 14 | Abschnitte 1–4 geprüft und integriert |
| 6 | 15 | Abschnitte 1–5 geprüft und integriert |
| 7 | 16 | Abschnitte 1–6 geprüft und integriert |
| 8 | 17 | Abschnitte 1–7 geprüft und integriert |
| 9 | 18 | Abschnitte 1–8 geprüft und integriert |
| 10 | 19, 20 | Abschnitte 1–9 geprüft und integriert |
| 11 | 21 | Abschnitte 1–10 geprüft und integriert |
| 12 | 22 | Abschnitte 1–11 geprüft und integriert |
| 13 | 24 | Abschnitte 1–12 geprüft und integriert |
| 14 | 25, 26 | Abschnitte 1–13 geprüft und integriert |
| 15 | 23 | Abschnitte 1–14 geprüft und integriert |
| 16 | 27 | Abschnitte 1–15 geprüft und integriert |
| 17 | 29, 30 | Abschnitte 1–16 geprüft und integriert |
| 18 | 31 | Abschnitte 1–17 geprüft und integriert |
| 19 | 33, 35 | Abschnitte 1–18 geprüft und integriert |
| 20 | 28, 32, 34 | Abschnitte 1–19 geprüft und integriert |
| 21 | 36 | Abschnitte 1–20 geprüft und integriert |
| 22 | 37 | Abschnitte 1–21 geprüft und integriert |
| 23 | 39 | Abschnitte 1–22 geprüft und integriert |

### Datei → Tasks: gemeinsame Schreibdateien

Die vollständigen Schreibmengen stehen unverändert in den jeweiligen `Files`- und `Explizite Testdateien`-Listen. Aus deren Zuordnung ergeben sich ausschließlich diese mehrfach besetzten Dateien; jede Zeile liegt über verschiedene Abschnitte verteilt. Alle übrigen dort genannten Dateien haben genau einen Task als Eigentümer.

| Datei | Tasks in Bearbeitungsreihenfolge | Abschnitte |
|---|---|---|
| `pom.xml` | 2, 7, 37 | 1, 5, 22 |
| `react-pc-frontend/src/App.tsx` | 31, 33, 34, 36 | 18, 19, 20, 21 |
| `react-pc-frontend/src/pages/EinkaufBestellungDetail.tsx` | 33, 34 | 19, 20 |
| `react-pc-frontend/src/pages/EinkaufsanfrageDetail.tsx` | 31, 32 | 18, 20 |
| `src/main/java/org/example/email/EmailService.java` | 38, 10 | 1, 3 |
| `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufKommunikationController.java` | 16, 35 | 7, 19 |
| `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufMailkontoController.java` | 9, 10 | 2, 3 |
| `src/main/java/org/example/kalkulationsprogramm/service/EmailImportService.java` | 38, 11, 16 | 1, 4, 7 |
| `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufKommunikationService.java` | 16, 22, 35 | 7, 12, 19 |

`BenutzerEditor.tsx` gehört ausschließlich Task29; Task1 liefert nur Backendberechtigungen. Die gemeinsamen Frontenddateien `features/einkauf/types.ts`, `api.ts`, `positionDrafts.ts` und Basiskomponenten gehören Task27. Folgetasks konsumieren diese geprüften Verträge. `vite.config.ts` gehört Task37; Task39 verwendet dessen Umgebungsflag. Falls eine Umsetzung zusätzliche Schreibdateien benötigt, muss der Orchestrator vor dem parallelen Start die Ownership und Konfliktfreiheit erneut prüfen.

### Präzisierte Abhängigkeiten aus Interfaces und Steps

- Die `Consumes-Gate`-Zeile jedes Tasks führt seine vor Start fertig geprüften Task-Abhängigkeiten vollständig auf, einschließlich impliziter Voraussetzungen. Bestehende Bestandsinterfaces bleiben wie in den Tasktexten beschrieben nutzbar. Transitive Voraussetzungen gelten zusätzlich.
- Tasks3/12/14/15/18/19/20 warten auch auf Task1 für gemeinsame Rechte/Fehler/Audit; Tasks4/7/10 auf Task2 für die gemeinsame Testgrundlage. Tasks9/10/11/15/16 konsumieren zusätzlich die zentrale LocalTestMailPolicy aus Task38; Task15 und Task22 konsumieren ausdrücklich das getrennte Scheduling-/Async-Profil aus Task38.
- Task26 verwendet den Quellenrecord aus Task20; Task29 benötigt Task10 für tatsächlich funktionierende Verbindungs-/Testmailaktionen. Task35 baut auf der von Task22 erweiterten Kommunikation auf.
- Task31 konsumiert den neutralen Entwurfvertrag aus Task13. Der Hinweis auf die später in Task28 erfolgende Navigation erzeugt keinen Import aus Task28. Task33 ist ohne Task32 über seine eigene Bestellliste/Detailroute und vorbereitete Testdaten prüfbar; die integrierte Auswahlkette wird nach Task32 in Task37 geprüft.
- Task32 wartet zusätzlich auf Task21 und die geprüfte Bestellroute aus Task33, damit „Als Bestellung vorbereiten“ ein erreichbares Ziel hat. Task28 wartet ebenfalls auf31/33. Tasks28/32/34 können danach parallel arbeiten: ihre Seiten und Dialoge sind dateidisjunkt und verwenden bereits geprüfte Basisbausteine.
- Task23 wartet auf die Zeugnisquery aus25. Task27 folgt vollständig geprüften Backendverträgen3–26. Task36 folgt allen Seiten27–35. Task37 prüft den gesamten Ablauf; Task39 folgt zusätzlich diesem Integrationsgate und verwendet den dann geprüften Vite-Proxy.
- Migrationen bleiben eindeutig V377–V395. Wegen früher Mailkonten-/Vorlagen-Tasks entstehen im Integrationsstand zunächst Versionslücken: hierfür je Abschnitt frische isolierte Dummy-DB verwenden, alle verfügbaren Migrationen in Versionsreihenfolge anwenden. Keine persistente Nutzerkopie vorziehen, kein Flyway-out-of-order/repair als Abkürzung. Task37 prüft die vollständige Reihe, Task39 allein migriert die reale Kopie.
- E2E-Ports sind `5181 + Tasknummer` (5182–5220). Bei ausschließlich serverseitigen Tasks bleiben sie reserviert. Playwright-Aufruf/Umgebung muss den zugeteilten Port und denselben baseURL verwenden; keine gemeinsame Änderung der Playwright-Konfiguration durch Coding-Tasks. Reale Backend-/DB-/Mailtestports separat dynamisch localhost-binden. Nutzerports5173/8080 bleiben während paralleler Tests frei.

## Abschnitt 1 (Tasks 1, 2, 38; disjunkte Dateien)

### Task 1: Einkaufsberechtigungen und serverseitige Aktionsgrenzen

- Branch: `codex/beschaffung-task-1`
- Worktree: `.claude/worktrees/beschaffung-task-1`
- E2E-Port: `5182`
- Consumes-Gate: keine neuen Tasks; bestehender Ausgangsstand. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufBerechtigung.java`, `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufBerechtigungService.java`, `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufBerechtigungController.java`, `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufBerechtigungDto.java`, `src/main/java/org/example/kalkulationsprogramm/exception/EinkaufExceptionHandler.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufAudit.java`, `src/main/java/org/example/kalkulationsprogramm/repository/EinkaufAuditRepository.java`, `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufAuditService.java` jeweils unter `src/main/java/org/example/kalkulationsprogramm/`; ändern `src/main/java/org/example/kalkulationsprogramm/domain/FrontendUserProfile.java`; Migration `src/main/resources/db/migration/V377__einkauf_berechtigungen.sql`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufBerechtigungServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/EinkaufBerechtigungSecurityTest.java`.
- Vorbild: `MonatsabschlussBerechtigungService.java:19`, `SecurityConfig.java:209`, `FrontendUserProfile.java:62`; Securitytests `FirmaControllerSecurityTest.java:45`.
- Interfaces – Produces: `Long verlange(Authentication auth, EinkaufBerechtigung recht)`; `Set<EinkaufBerechtigung> rechte(Authentication auth)`; `void protokolliere(String vorgangTyp,Long vorgangId,String aktion,Long akteurId,JsonNode vorher,JsonNode nachher,String grund)` in EinkaufAuditService; `GET /api/einkauf/berechtigungen`; `GET/PUT /api/settings/einkauf-berechtigungen/{profileId}` mit `Set<EinkaufBerechtigung>`. Consumes: bestehender `FrontendUserPrincipal` und Profile-Repository.
- Steps:
  - [ ] Rote Tests: anonymer Zugriff 401, USER ohne Recht 403, ADMIN erlaubt, manipulierte `frontendUserId`/Mitarbeiter-ID ohne Wirkung, sofort entzogene Rechte trotz alter Session, CSRF auf Schreibzugriffen.
  - [ ] Enum `LESEN, BEARBEITEN, ANFRAGE_SENDEN, BESTELLUNG_FREIGEBEN, ZEUGNIS_PRUEFEN`; ElementCollection `frontend_user_einkauf_recht` mit PK(profile_id,recht), native ENUM. ADMIN implizit alle Rechte; keine pauschale Freigabe für vorhandene USER. UI verwaltet Grants im Task29. V377 enthält außerdem append-only `einkauf_audit` mit Vorgangstyp/-ID, Aktion, Akteur, Zeitpunkt, Vorher-/Nachher-Snapshot und Grund; alle später als „auditiert“ bezeichneten Mutationen nutzen diesen Service in ihrer Fachtransaktion. Vorhandene Profiländerungen erhalten Grants, solange sie nicht ausdrücklich über den neuen Rechteendpoint geändert werden.
  - [ ] `verlange` lädt aktuelles aktives Profil aus Principal, niemals Requestakteur; liefert ID für Audit. Leserecht ist bei jedem Einkaufs-DTO/Download nötig; Schreib-/Sende-/Prüfrechte je Aktion. Verschachtelte IDs zusätzlich in jedem Domainservice auf gemeinsame Anfrage/Lieferant/Bestellung prüfen.
  - [ ] Gemeinsame 400/404/409-Behandlung mit konkreten deutschen Fehlern und Feldern; keine Stacktraces im Response. `./mvnw -Dtest=EinkaufBerechtigungServiceTest,EinkaufBerechtigungSecurityTest test` grün.

### Task 2: Atomare PA-/B-Nummern und unveränderte Verkaufsnummern

- Branch: `codex/beschaffung-task-2`
- Worktree: `.claude/worktrees/beschaffung-task-2`
- E2E-Port: `5183`
- Consumes-Gate: keine neuen Tasks; bestehender Ausgangsstand. Vor Start geprüft und in den Featurebranch integriert.
- Files: `src/main/java/org/example/kalkulationsprogramm/domain/DokumentnummerCounter.java`, `src/main/java/org/example/kalkulationsprogramm/repository/DokumentnummerCounterRepository.java`, `src/main/java/org/example/kalkulationsprogramm/service/FormularTemplateService.java`; neu `src/main/java/org/example/kalkulationsprogramm/service/DokumentnummerService.java`; Tests `src/test/java/org/example/kalkulationsprogramm/service/DokumentnummerServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/repository/DokumentnummerParallelTest.java`; `pom.xml` nur für gemeinsame MySQL-Testcontainers-Testabhängigkeiten.
- Vorbild: `FormularTemplateService.java:160`, `DokumentnummerCounter.java:7`; kein bestehender ausreichend abgesicherter Erstzähler.
- Interfaces – Produces: `String naechsteEinkaufsnummer(String nummernkreis, LocalDate datum)` (nur PA/B), `String naechsteVerkaufsnummer(YearMonth periode)`; Consumes: bestehende unique `month_key` (10 Zeichen reichen für PA-2026).
- Steps:
  - [ ] MySQL-Paralleltest mit 30 eigenen Transaktionen auf noch nicht vorhandenem Jahresschlüssel, alle Nummern unique; unabhängige B-/PA-Kreise und Jahreswechsel; Verkaufsformat `MM/00001` bleibt. Echter MySQL-Test rollt die äußere Fachtransaktion nach Nummernvergabe zurück und weist nach, dass die committed Nummer beim nächsten Aufruf nicht recycelt wird.
  - [ ] Über parametrisierte JDBC-Operation `INSERT ... ON DUPLICATE KEY UPDATE counter=counter` Zählerzeile sicher erzeugen; `SELECT ... FOR UPDATE`, erhöhen und lesen in einer eigenen kurzen `REQUIRES_NEW`-Transaktion über einen Spring-Proxy oder ein korrekt konfiguriertes TransactionTemplate (keine Self-Invocation). Unique Key bleibt bestehen. Kein MAX+1, kein Java-synchronized als Schutz.
  - [ ] `generateDokumentnummer()` an denselben Service delegieren, aber bestehenden Monatsschlüssel und Ausgabeformat erhalten. Reservierte Nummern nach Abbruch nicht recyceln. H2-Unitfälle plus echter MySQL-Locktest grün ausführen; Testcontainer ausschließlich Dummy-DB.

### Task 38: Sicheres reguläres Profil für lokalen Probebetrieb (früh ausführbar)

- Branch: `codex/beschaffung-task-38`
- Worktree: `.claude/worktrees/beschaffung-task-38`
- E2E-Port: `5219`
- Consumes-Gate: keine neuen Tasks; bestehender Ausgangsstand. Vor Start geprüft und in den Featurebranch integriert.
- Files: ändern `src/main/java/org/example/email/EmailService.java`, `src/main/java/org/example/kalkulationsprogramm/service/EmailImportService.java`, `src/main/java/org/example/kalkulationsprogramm/service/VendorInvoiceIntegrationService.java`; neu `src/main/java/org/example/kalkulationsprogramm/config/LocalTestMailPolicy.java`, `src/test/java/org/example/kalkulationsprogramm/config/LocalTestMailPolicyTest.java`; ändern `src/main/java/org/example/kalkulationsprogramm/KalkulationsprogrammApplication.java`, `src/main/java/org/example/kalkulationsprogramm/config/AsyncConfig.java`, `src/main/java/org/example/kalkulationsprogramm/config/SchemaFixConfig.java`, `src/main/java/org/example/kalkulationsprogramm/config/AuditChainBackfillRunner.java`, `src/main/java/org/example/kalkulationsprogramm/config/AuditChainRebuildRunner.java`, `src/main/java/org/example/kalkulationsprogramm/config/EmailThreadBackfillRunner.java`, `src/main/java/org/example/kalkulationsprogramm/service/MonatsSaldoWarmupService.java`, `src/main/java/org/example/kalkulationsprogramm/config/FrontendUserBootstrapInitializer.java`, `src/main/java/org/example/kalkulationsprogramm/service/LocalRagService.java`; neu `src/main/java/org/example/kalkulationsprogramm/config/BackgroundSchedulingConfig.java`, `src/main/java/org/example/kalkulationsprogramm/config/LocalTestDatabaseGuard.java`, `src/main/resources/META-INF/spring.factories`, `src/main/resources/application-local-test.properties`; Tests `src/test/java/org/example/kalkulationsprogramm/config/LocalTestIsolationTest.java`, `src/test/java/org/example/kalkulationsprogramm/config/LocalTestDatabaseGuardTest.java`.
- Autorisierte Scope-Erweiterung (Netzpfad-Inventur und Constructor Injection, 23.09.2026): `src/main/java/org/example/email/ImapAppendService.java`, `src/main/java/org/example/kalkulationsprogramm/controller/EmailController.java`, `src/main/java/org/example/kalkulationsprogramm/controller/UnifiedEmailController.java`, `src/main/java/org/example/kalkulationsprogramm/service/AnfrageBestaetigungVersandService.java`, `src/main/java/org/example/kalkulationsprogramm/service/AutoAuftragsbestaetigungVersandService.java`, `src/main/java/org/example/kalkulationsprogramm/service/AutoMahnVersandService.java`, `src/main/java/org/example/kalkulationsprogramm/service/SystemSettingsService.java`, `src/main/java/org/example/kalkulationsprogramm/service/mail/SentMailArchiver.java`, `src/main/java/org/example/kalkulationsprogramm/service/mail/SmtpHtmlMailSender.java`, `src/test/java/org/example/kalkulationsprogramm/config/FrontendUserBootstrapInitializerTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/EmailControllerTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/UnifiedEmailControllerTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/AnfrageBestaetigungVersandServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/AutoAuftragsbestaetigungVersandServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/AutoMahnVersandServiceSchedulerLaufTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/AutoMahnVersandServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/EmailImportServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/SystemSettingsServiceDateiOrdnerTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/SystemSettingsServiceDokumentMailTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/SystemSettingsServiceMailFromTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/VendorInvoiceIntegrationServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/mail/SentMailArchiverTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/mail/SmtpHtmlMailSenderTest.java`. Diese zusätzlichen Bestandszugriffe benötigen dieselbe Policy; die Testanpassungen erhalten das Produktionsverhalten. Eigenständige CLI-Hilfsprogramme außerhalb des Serverstarts bleiben ausgenommen.
- Ergänzte Abschluss-/Regressionsdateien aus der zweiten Nachbesserung: `src/main/java/org/example/kalkulationsprogramm/controller/LieferantenController.java`, `src/test/java/org/example/kalkulationsprogramm/config/LocalTestAfterCommitAsyncTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/LieferantenControllerTest.java`.
- Vorbild: `KalkulationsprogrammApplication.java:14`, `AsyncConfig.java:45`, `EmailImportService.java:129,157`, `LocalRagService.java:299`; unabhängiger lokaler Sol-Review mit konkret benannten Startup-Hooks im Kontextlog.
- Interfaces – Produces: `app.background-jobs.enabled` default true; `app.startup-maintenance.enabled` default true; Profil `local-test` setzt beide false, `ai.rag.enabled=false`, `server.address=127.0.0.1`, `server.ssl.enabled=false` für explizites lokales HTTP, `spring.flyway.validate-on-migrate=true`; getrennte `app.local-test.manual-mail.enabled=false` erst bewusst lokal aktivieren. `LocalTestMailPolicy.pruefeNetzwerkzugriff(String kontoId)` ist die zentrale fail-closed Policy vor jedem SMTP-/IMAP-/Vendor-Netzzugriff; sie benötigt keine DTOs späterer Tasks. Consumes: keine neuen Produkt-Tasks; daher als früher unabhängiger Task parallel zum Fundament geeignet. Outbox Task15/22 konsumiert diese Trennung.
- Steps:
  - [ ] Zentrale LocalTestMailPolicy in bestehenden EmailService-/EmailImportService-/VendorInvoiceIntegrationService-Netzpfaden tatsächlich aufrufen: im local-test standardmäßig alle manuellen und automatischen Mail-/Vendorzugriffe blockieren; nur nach explizitem `app.local-test.manual-mail.enabled=true` ist exakt kontoId `EINKAUF` erlaubt. HAUPT/DOKUMENTE/fehlende oder unbekannte Konto-ID sowie Vendor-Altabrufe bleiben auch mit Opt-in blockiert. Außerhalb local-test unveränderte Produktionsdefaults. Tests prüfen vor jedem Netzwerkaufruf null Calls bei Sperre und gezielte EINKAUF-Freigabe; neue Tasks9/10/11/15/16 übernehmen dieselbe Policy.
  - [ ] ApplicationContext-Test im Profil findet keinen `ScheduledAnnotationBeanPostProcessor`/registrierten Schedule, aber funktionierenden TaskExecutor/@Async. Test simuliert Nutzerfreigabe→AFTER_COMMIT gezieltes async Jobdispatch und manuelles IMAP ohne periodische Jobs. Test automatische Mahnung/Push/Vendorimport0Calls.
  - [ ] `@EnableScheduling` aus Hauptklasse in eigene `@ConditionalOnProperty(name="app.background-jobs.enabled",havingValue="true",matchIfMissing=true)`-Config; `@EnableAsync` erhalten. Nur Schedulerbean bedingt, Email-/Domainservices nicht deaktivieren. Kein pool.size=0-Hack.
  - [ ] Startup-Schemafix, Audit-/Mailthread-Backfills und Saldo-Warmup über `app.startup-maintenance.enabled` gate'n; lokale Flyway-Migrationen nicht unter dieses Gate stecken. Admin-Bootstrap nur explizit lokal konfigurierte Dummyzugänge, keine zufällige Veränderung von Bestandsprofilen. RAG-Startupthread sicher deaktiviert; weitere vom Sol-Review benannte externe Startupaktionen prüfen.
  - [ ] `LocalTestDatabaseGuard` vor DataSource/Flyway als EnvironmentPostProcessor mit expliziter Order **nach ConfigData-Auflösung**, aber vor DataSource/Flyway registrieren (zusätzliche exakte Datei `src/main/resources/META-INF/spring.factories`): im local-test nur Host127.0.0.1, Port3309, DBkalkulationsprogramm_db oder dedizierte localhost-Dummyintegration zulassen; keine Remotehosts, Failover/MultiHost/JDBC-URL-Parameter, die Hostwechsel erlauben. E2E-Dummyprofil separat; Guard nicht durch Requestparameter veränderbar. Effektive JDBC-URL und effektives `server.address` inklusive Environment-/CLI-Overrides prüfen; relaxiert gebundene Hikari-Treiber-Unterproperties und SSL-Overrides ebenfalls abweisen. Aktive Profile auch bei AdditionalProfiles/Include/Profilgruppen über `Environment.acceptsProfiles` erkennen; Serverhostbindung fail-closed. Tests für priorisierte CLI/env-Overrides und ConfigData-Reihenfolge, nicht nur rohe Profilwerte. Flyway-Validierung bleibt aktiv; den tatsächlich vorhandenen Althistorienbefund der lokalen Kopie behandelt Task39 vor Anwendung neuer Migrationen nachvollziehbar. Der Clone enthält keine V102-Zeile, daher keinen V102-Konflikt unterstellen.
  - [ ] Secrets nur ignored application-local.properties/externer lokaler Pfad. Profil selbst enthält weder Credentials noch Backupinhalte. Bestehende Produktionsdefaults bleiben unverändert, explizite lokale Mailaktionen greifen nur auf bewusst aktiviertes EINKAUF, nie aus Backup gestartete Hauptkontojobs. Tests grün.

## Abschnitt 2 (Tasks 3, 8, 9; disjunkte Dateien)

### Task 3: Gemeinsame technische Positionsfassung und interne Identität

- Branch: `codex/beschaffung-task-3`
- Worktree: `.claude/worktrees/beschaffung-task-3`
- E2E-Port: `5184`
- Consumes-Gate: 1. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufPositionDto.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/Positionsart.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/Einheit.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/Dokumentart.java`, `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufPositionService.java`; ändern `src/main/java/org/example/kalkulationsprogramm/dto/Artikel/ArtikelCreateDto.java`, `src/main/java/org/example/kalkulationsprogramm/service/ArtikelService.java`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufPositionServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/ArtikelServiceTest.java`.
- Vorbild: `Artikel.java:186`, `ArtikelService.java:77`, `ProjektManagementService.java:971`, `BestellungService.java:123` (bereits ein Schnittbild + zwei Winkel).
- Interfaces – Produces: oben definierte Records; `PositionSnapshot validiere(PositionSnapshot input, Long projektId)`; `String buendelSchluessel(PositionSnapshot p, Liefergruppe g)`; `PositionSnapshot mitTeilmenge(PositionSnapshot p,BigDecimal menge)`; ArtikelCreateDto zusätzlich `String artikelnummer`. Consumes: bestehende Artikel/Werkstoff/Kategorie-Repositories.
- Steps:
  - [ ] Rote Tests für gleiche interne Nummer bei unterschiedlichen externen Nummern, fehlende Nummer, Zeichnungsteil ohne Projekt/Revisionskennung, 4×6000 mm=24 m, verschiedene Oberfläche/Zeugnisse/Lieferorte/Termine nicht bündelbar.
  - [ ] Katalogposition lädt Artikel per ID und interne `artikelnummer`; keine externe Nummer als Ersatz. Bestehende fehlende Nummer markiert der Bedarf als Nachpflege, Versand blockiert. Kurzanlage zeigt eigene interne Nummer als Pflichtfeld; bestehenden DTO um optionales artikelnummer ergänzen, bei älteren Create-Callern ohne Angabe nach persistierter Artikel-ID eine kollisionssichere interne Nummer `ART-<id>` erzeugen (bei belegtem String reservierten weiteren Suffix verwenden). Explizite Nummer prüft DB-Unique-Konflikt als409. Historische fehlende Identitäten weiterhin bewusst nachpflegen, nicht rückwirkend raten.
  - [ ] Zeichnungsteil fordert Projekt, eindeutige projektbezogene Kennung, Zeichnungsnummer und Revision sowie freigegebene Anlage vor Versand. Technische Textfelder begrenzen; Mengen >0; STUECK und Profilstückzahl ganzzahlig; Winkel als validierter Text, nur eine `schnittForm`.
  - [ ] Bündelschlüssel aus stabil sortierten Dokumentanforderungen, Anlagenrevisionen, Identität/Technik und exakt kompatibler Liefergruppe erzeugen; Projektanteile bleiben separate Herkunftsrecords. Snapshot ist vom Stamm unabhängig. Gezielte Tests grün.

### Task 8: Strukturierte Lieferantenkontakte und Empfängersnapshots

- Branch: `codex/beschaffung-task-8`
- Worktree: `.claude/worktrees/beschaffung-task-8`
- E2E-Port: `5189`
- Consumes-Gate: 1. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/LieferantEinkaufKontakt.java`, `src/main/java/org/example/kalkulationsprogramm/repository/LieferantEinkaufKontaktRepository.java`, `src/main/java/org/example/kalkulationsprogramm/service/einkauf/LieferantEinkaufKontaktService.java`, `src/main/java/org/example/kalkulationsprogramm/controller/LieferantEinkaufKontaktController.java`, `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufKontaktDto.java`; `src/main/resources/db/migration/V382__lieferant_einkauf_kontakte.sql`; Tests `src/test/java/org/example/kalkulationsprogramm/service/LieferantEinkaufKontaktServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/LieferantEinkaufKontaktControllerTest.java`.
- Vorbild: `Lieferanten.java:51,75`, vorhandene Lieferanten-DTO/Controller. `vertreter` ist ausdrücklich kein strukturierter Personenstamm.
- Interfaces – Produces: `List<Kontakt> liste(Long lieferantId)`; `Kontakt speichern(Long lieferantId, Kontakt r)`; `Snapshot snapshot(Long lieferantId, Long kontaktId, String emailOverride, KontaktZweck zweck)`; `Kontakt(Long id,long version,String name,String anrede,String email,boolean standardAnfrage,boolean standardBestellung,boolean aktiv)`; GET/POST/PUT `/api/lieferanten/{id}/einkauf-kontakte`. Consumes1.
- Steps:
  - [ ] Test gesonderte Standardanfrage/-bestellung, neutral ohne Name, keine Übernahme aus Rechnungsadresse, führende Nullen in eigenerKundennummer, Kontakt eines anderen Lieferanten 400/404.
  - [ ] Lieferant unter Sperre aktualisieren, höchstens einen aktiven Standard pro Zweck, E-Mail-Längen-/Syntaxprüfung ohne Headerinjection. Snapshot kopiert explizit gewählte Adresse, Anrede, Name und eigeneKundennummer; nur Lieferanten-ID verknüpfen reicht nicht.
  - [ ] Bestehende kundenEmails/vertreter unverändert; alter Mailimport darf weiterhin Rechnungsadressen kennen. Änderungs-/Löschvorgang deaktiviert Kontakt, bereits gespeicherte Snapshots bleiben. Tests grün.

### Task 9: Wiederverwendbare Mailkonten und geschützte Einkaufszugänge

- Branch: `codex/beschaffung-task-9`
- Worktree: `.claude/worktrees/beschaffung-task-9`
- E2E-Port: `5190`
- Consumes-Gate: 1, 38. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufMailkonto.java`, `src/main/java/org/example/kalkulationsprogramm/repository/EinkaufMailkontoRepository.java`, `src/main/java/org/example/kalkulationsprogramm/service/mail/MailkontoService.java`, `src/main/java/org/example/kalkulationsprogramm/service/mail/MailSecretService.java`, `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/MailkontoDto.java`, `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufMailkontoController.java`; ändern `src/main/java/org/example/kalkulationsprogramm/service/SystemSettingsService.java`; `src/main/resources/db/migration/V383__einkauf_mailkonto.sql`; Tests `src/test/java/org/example/kalkulationsprogramm/service/mail/MailkontoServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/mail/MailSecretServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/EinkaufMailkontoSecurityTest.java`.
- Vorbild: `SystemSettingsService.java:230,270,295`, `SystemSettingsControllerDokumentMailTest.java:42`.
- Interfaces – Produces: `KontoZugang resolve(String kontoId)` (`HAUPT`,`DOKUMENTE`,`EINKAUF` stabile IDs); `MailkontoDto.Response lesen(String kontoId)`; `Response speichern(String kontoId,Update r)`; `KontoZugang(String id,boolean aktiv,String fromAddress,String fromName,ServerZugang smtp,ServerZugang imap,String inbox,String sent)`; `ServerZugang(String host,int port,String username,String password,Verschluesselung tls)` mit Enum `Verschluesselung { TLS, STARTTLS }` in MailkontoDto; `Update` enthält dieselben editierbaren Felder plus optionale smtpPassword/imapPassword und version; Response ausschließlich passwordSet-Flags, letzterAbruf/letzterFehler. GET/PUT `/api/settings/einkauf-mail`; POST `/test-verbindung`, `/testmail` werden erst von Task10 samt echtem Transport in demselben Controller ergänzt. Consumes1.
- Steps:
  - [ ] Geprüfte `LocalTestMailPolicy` aus Task38 in allen neuen kontoabhängigen Netzwerkpfaden vor Resolver-Fallback/SMTP/IMAP/Workerdispatch anwenden; keine Umgehung über Testmail, Verbindungsprüfung, manuellen Abruf oder Retry. Bei local-test ohne Opt-in null Netzwerkaufrufe, mit Opt-in ausschließlich EINKAUF.
  - [ ] Tests Passwort bleibt bei absent erhalten, kein Klartext in DTO/toString/Fehler, Aktivierung ohne vollständige SMTP/IMAP-Konfiguration scheitert, fehlerhaftes Einkaufskonto fällt nicht zurück, ADMIN notwendig.
  - [ ] Gemeinsamer Account-Resolver adaptiert HAUPT/DOKUMENTE aus bestehendem SystemSettingsService; bestehende Getter bleiben. EINKAUF eigene versionierte DB-Konfiguration. Separate SMTP/IMAP-Nutzer/Passwörter und Verschlüsselung, Ordnernamen/Ports validieren.
  - [ ] AES-GCM mit zufälligem Nonce und versioniertem Ciphertext; Schlüssel `mail.credentials.encryption-key` ausschließlich in ignored application-local.properties. Ohne Schlüssel ist Einkaufscredential-Speichern blockiert, kein Klartextfallback. Secret-Objekte nicht loggen; Antwort maskiert nur Präsenz.
  - [ ] Konfiguration liefert `VerbindungsTestRequest` und `TestmailRequest(String recipient)` sowie Kontostatus-Felder, Transport-/Test-Endpunkte gehören Task10. Kontostatus speichert bereinigten Fehlercode/Zeit; keine Providertexte mit Credentials weiterreichen. Resolver-/Secret-/Securitytests grün.

## Abschnitt 3 (Tasks 4, 10, 12; disjunkte Dateien)

### Task 4: Persistenter Bedarf, Herkunft und gemeinsamer Mengenschutz

- Branch: `codex/beschaffung-task-4`
- Worktree: `.claude/worktrees/beschaffung-task-4`
- E2E-Port: `5185`
- Consumes-Gate: 1, 2, 3. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufBedarf.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufMengenbuchung.java` im selben Verzeichnis; `src/main/java/org/example/kalkulationsprogramm/repository/EinkaufBedarfRepository.java`, `src/main/java/org/example/kalkulationsprogramm/repository/EinkaufMengenbuchungRepository.java`; `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufBedarfService.java`, `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufMengenService.java`; `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufBedarfDto.java`; `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufBedarfController.java`; ändern `src/main/java/org/example/kalkulationsprogramm/service/ProjektManagementService.java`, `src/main/java/org/example/kalkulationsprogramm/service/BestellungService.java`, `src/main/java/org/example/kalkulationsprogramm/repository/ArtikelInProjektRepository.java` jeweils unter `src/main/java/org/example/kalkulationsprogramm/`; `src/main/resources/db/migration/V378__einkauf_bedarf_mengen.sql`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufBedarfServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/repository/EinkaufMengenParallelTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/EinkaufBedarfControllerTest.java`, bestehende `src/test/java/org/example/kalkulationsprogramm/service/BestellungServiceTest.java`.
- Vorbild: `ProjektManagementService.java:950`, `ArtikelDokumentService.java:115` (PESSIMISTIC_WRITE), `Lieferanten.java:25` (@Version), `BestellungServiceTest.java:20`.
- Interfaces – Produces: `Page<Response> suche(String q, Long projektId, Pageable p)`; `Response anlegen(Create r, Long akteurId)`; `Response aktualisieren(Long id, Update r, Long akteurId)`; `void synchronisiereProjektposition(ArtikelInProjekt aip)`; `void buche(List<Herkunft> anteile, Mengenaktion aktion, String vorgangsschluessel, UUID idempotenzKey, Long akteurId)`; `Mengenstand stand(Long bedarfId)`; `EinkaufMengenService.Mengenaktion` ist das unten definierte Enum, nested im Service (JPA-Buchungsentity speichert exakt dieses Enum). API `GET/POST /api/einkauf/bedarf`, `PUT /{id}`. Consumes: Tasks 1,3.
- Steps:
  - [ ] Rote Tests: Bedarf 10, Anfrage 4 lässt offen10; Reservierung4 lässt ungedeckt10/disponierbar6; Bestellung4 lässt offen6; Lieferung2 reduziert offen nicht erneut. Zwei Threads reservieren6+6 bzw. entnehmen6/reservieren6: genau eine Transaktion scheitert.
  - [ ] Bedarf mit @Version, technischem JSON-Snapshot, Projekt/Lagerzweck, nullable unique AIP-FK; Mengenbuchung append-only mit Aktion `RESERVIEREN, RESERVIERUNG_FREIGEBEN, BESTELLEN, LAGER_ENTNEHMEN, STORNO_BESTAETIGEN, LIEFERN`, Menge, Vorgangsreferenz, Key, Akteur/Zeit. Counterspalten nur unter gemeinsamer Sperre aktualisieren; keine frei beschreibbaren Mengen im PUT.
  - [ ] V378 übernimmt vorhandene **offene** AIP genau einmal; historische bestellt/ausLager bleiben als Altbestand kenntlich und erzeugen keine erfundene B-/Versandhistorie. Fehlende Artikelreferenz/Menge nicht raten, Nachpflegezustand persistieren. Lazy-Sync nicht bei GET schreiben. Neue AIP nach Save/Flush in derselben Transaktion synchronisieren; spätere Mengenänderung unter dieselbe Sperre und nicht unter aktive Deckung reduzieren.
  - [ ] Alte `setBestellt` für migrierte AIP mit 409 abweisen, historische unveränderte Fälle weiter ermöglichen. Leselisten mit fetch/projection/pagination. Einmalige Post-Deploy-Übernahme/Migration mit Legacy-Dummyfällen testen; kein Rücksetzen bestehender Preise.
  - [ ] API-Rechte, SQL/XSS/ID-/Längenfälle, Idempotenz und Rollback bei zweiter ungültiger Herkunft testen. Zieltests einschließlich echter MySQL-Parallelität grün.

### Task 10: Kontobewusster SMTP-Transport mit eindeutigem Ergebnis

- Branch: `codex/beschaffung-task-10`
- Worktree: `.claude/worktrees/beschaffung-task-10`
- E2E-Port: `5191`
- Consumes-Gate: 2, 9, 38. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/service/mail/KontoMailTransport.java`, `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/MailTransportDto.java`; ändern `src/main/java/org/example/email/EmailService.java`, `src/main/java/org/example/kalkulationsprogramm/service/mail/SentMailArchiver.java`, `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufMailkontoController.java`; Tests `src/test/java/org/example/kalkulationsprogramm/service/mail/KontoMailTransportTest.java`, bestehende `src/test/java/org/example/kalkulationsprogramm/service/mail/SentMailArchiverTest.java`.
- Vorbild: `EmailService.java:326,498` (Anlagen als Bytes), `SentMailArchiver.java:84`.
- Interfaces – Produces: `Versandergebnis senden(KontoZugang konto, Nachricht nachricht)`; `Nachricht(String messageId,String to,String subject,String html,String inReplyTo,List<String> references,List<EmailService.Attachment> anlagen)`; `Versandergebnis(Status status,String messageId,String fehlerCode,byte[] mime)`; Status `ANGENOMMEN, SICHER_FEHLGESCHLAGEN, UNKLAR`; `ArchivErgebnis archiviere(KontoZugang konto,byte[] mime)`; `ArchivErgebnis(boolean erfolgreich,String fehlerCode)`; `byte[] vorbereiten(KontoZugang konto,Nachricht nachricht)` friert MIME einschließlich Date/Message-ID ein; `Versandergebnis sendenVorbereitet(KontoZugang konto,byte[] mime)` sendet exakt diese Bytes (Outbox nutzt diese Variante, keine Neuaufbereitung). Consumes9.
- Steps:
  - [ ] Geprüfte `LocalTestMailPolicy` aus Task38 in allen neuen kontoabhängigen Netzwerkpfaden vor Resolver-Fallback/SMTP/IMAP/Workerdispatch anwenden; keine Umgehung über Testmail, Verbindungsprüfung, manuellen Abruf oder Retry. Bei local-test ohne Opt-in null Netzwerkaufrufe, mit Opt-in ausschließlich EINKAUF.
  - [ ] Fake SMTP/GreenMail Tests: Erfolgsantwort, Authfehler vor DATA, Verbindungsbruch nach DATA, IMAP-APPEND-Fehler nach Erfolg und STARTTLS. Message-ID vor Netzwerk festgelegt und MIME identisch zu gespeicherter Vorschau.
  - [ ] Gemeinsamen MIME-Aufbau aus EmailService auslagern/reuse statt dritte Kopie; alte Signaturen bleiben delegierend funktionsfähig. Transport unterstützt SSL/STARTTLS, Hostname-/Zertifikatsprüfung und Zeitlimits. Nur Fehler nachweislich vor möglicher Annahme als sicher fehlgeschlagen; Zweifel immer UNKLAR.
  - [ ] Task9-Testendpunkte jetzt vollständig anschließen: Verbindung prüft SMTPauth+IMAPauth ohne DATA, expliziter Testmail-Endpunkt verlangt bestätigten Empfänger und nutzt denselben Transport. ADMIN+CSRF, Status-/Fehlerrückgabe mit Dummyserver testen; kein kopierter SMTP-Code im Controller.
  - [ ] SMTP-Rückgabe und Sent-Archivierung strikt trennen. IMAP darf SMTP-Erfolg nicht in Versandfehler verwandeln; Archiv kann anhand Message-ID erneut versucht werden. Genau eine Empfängeradresse je Einkaufsmail und erlaubte Originaldateinamen.
  - [ ] GreenMail-/Fake-Transport-Tests grün, keine externen Mailserver in Tests. Bestehende Mailservice-/Sent-Kopie-Regression grün.

### Task 12: Einkaufsvorlagen mit Varianten und strengem Rendering

- Branch: `codex/beschaffung-task-12`
- Worktree: `.claude/worktrees/beschaffung-task-12`
- E2E-Port: `5193`
- Consumes-Gate: 1, 3. Vor Start geprüft und in den Featurebranch integriert.
- Files: ändern `src/main/java/org/example/kalkulationsprogramm/domain/EmailTextTemplate.java`, `src/main/java/org/example/kalkulationsprogramm/domain/EmailTextTemplateKategorie.java`, `src/main/java/org/example/kalkulationsprogramm/repository/EmailTextTemplateRepository.java`, `src/main/java/org/example/kalkulationsprogramm/service/EmailTextTemplateService.java`, `src/main/java/org/example/kalkulationsprogramm/service/EmailTextTemplateKategorien.java`, `src/main/java/org/example/kalkulationsprogramm/controller/EmailTextTemplateController.java`, `src/main/java/org/example/kalkulationsprogramm/dto/Email/EmailTextTemplateDto.java`; neu `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufVorlagenService.java`, `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufVorlagenDto.java`; `src/main/resources/db/migration/V385__einkauf_vorlagen_varianten.sql`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EmailTextTemplateServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/EinkaufVorlagenServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/EmailTextTemplateControllerTest.java`.
- Vorbild: `EmailTextTemplateService.java:78,129`, `EmailTextTemplateController.java:40,68`, `EmailTextTemplate.java:20`.
- Interfaces – Produces: `Gerendert rendern(Long templateId, VorlagenKontext kontext)`; `Gerendert(Long templateId,long version,String subject,String htmlBody,String hash)`; `VorlagenKontext(String typ,Map<String,String> skalare,List<PositionSnapshot> positionen,String rueckmeldecode)`; `List<Platzhalter> placeholders(String dokumentTyp)`; `Platzhalter(String token,String label,boolean pflicht,boolean imBetreffErlaubt)`; Legacy `render(String,Map)` wählt weiterhin Standard. Consumes3.
- Steps:
  - [ ] Rote Tests für PA statt Kundenanfrage, B nur ab Bestellentwurf, Sammelanfrage ohne Projekt, Direktbestellung ohne PA/Angebotsnummer, unbekannter/unzulässiger/fehlender Token, Positionsliste im Betreff, HTML-Injection in Lieferantenname und Vorschauhash.
  - [ ] Kategorie EINKAUF; Typen `EINKAUF_ANFRAGE`, `EINKAUF_BESTELLUNG`, `EINKAUF_DIREKTBESTELLUNG`, `EINKAUF_NACHFRAGE`, `EINKAUF_ZEUGNIS_NACHFORDERUNG`, `EINKAUF_BESTAETIGUNG_NACHFRAGE`, `EINKAUF_LIEFERUNG_NACHFRAGE`. Bearbeitbare Standards mit neutraler Anrede/Nummern; beim Seed keine bestehende Vorlage überschreiben.
  - [ ] `dokument_typ`-Unique lösen, @Version + standard-Flag; separate unique Standardzuordnung pro Typ (Tabelle `email_text_template_standard`, FK) verhindert zwei Defaults ohne nullable-Unique-Tricks. Alte Einträge werden Default; `findByDokumentTyp` bleibt als Default-Lookup eindeutig.
  - [ ] Einkauf strikt typisierte Allowlist aus Spec§5.2, gemeinsame Firmenfelder zulassen; `KUNDENNUMMER` niemals umdeuten. Skalare für HTML escapen, Betreff CR/LF blockieren, Body sanitisieren, strukturierte Listen ausschließlich HTML/PDF. Fehlende optionale Referenzen nur bei Vorlage ohne deren Token erlaubt. Geschützten Rückmeldecode auch ohne Token immer ergänzen, sichtbar in Vorschau.
  - [ ] Vergleichs-/Versandvorschau rendert einmal, Snapshot enthält Versionsnummer+Hash; Verkaufsrendering behält Vertrag. Tests grün.

## Abschnitt 4 (Tasks 5, 6, 11; disjunkte Dateien)

### Task 5: Teilentnahme aus Lager und korrekte Projektkosten

- Branch: `codex/beschaffung-task-5`
- Worktree: `.claude/worktrees/beschaffung-task-5`
- E2E-Port: `5186`
- Consumes-Gate: 1, 4. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufLagerentnahme.java`, `src/main/java/org/example/kalkulationsprogramm/repository/EinkaufLagerentnahmeRepository.java`, `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufLagerentnahmeService.java`, `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufLagerentnahmeDto.java`, `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufLagerentnahmeController.java`; ändern `src/main/java/org/example/kalkulationsprogramm/mapper/ProjektMapper.java`, `src/main/java/org/example/kalkulationsprogramm/dto/Projekt/ProjektResponseDto.java`; `react-pc-frontend/src/pages/ProjektEditor.tsx`, `react-pc-frontend/src/types.ts`; `src/main/resources/db/migration/V379__einkauf_lagerentnahme.sql`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufLagerentnahmeServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/mapper/ProjektMapperTest.java`; `react-pc-frontend/src/pages/ProjektEditor.lagerkosten.test.tsx`, `react-pc-frontend/e2e/projekt-lagerentnahmen-kosten.spec.ts`.
- Vorbild: `ProjektEditor.tsx:1011`, `ProjektMapper.java:249`, AIP-Lagerbewertung `ProjektManagementService.java:1033`.
- Interfaces – Produces: `EntnahmeDto bestaetigen(EntnahmeRequest r, Long akteurId)`; `EntnahmeRequest(Herkunft anteil, BigDecimal preisJeEinheit, String preisQuelle, Instant entnommenAm, UUID idempotenzKey)`; `BigDecimal bewerteteEntnahmen(Long projektId)`; POST `/api/einkauf/lagerentnahmen`, PUT `/{id}/bewertung`, GET `/api/einkauf/lagerentnahmen?projektId=`. ProjektResponseDto zusätzlich `lagerentnahmenKosten` und `lagerentnahmenBewertungOffen`. Consumes Tasks 1,4.
- Steps:
  - [ ] Test Entnahme3 von Bedarf10 zu2€/Stück ergibt6€ Lagerkosten/offen7; zweiter identischer Request keine weiteren Kosten; bereits reservierte Menge nicht entnehmbar. Fehlende Bewertung bleibt offen und nicht0€.
  - [ ] Append-only Entnahme mit Mitarbeiter aus aktuellem Profil, Zeit, Menge, explizit bestätigter Preisquelle/Einheit; ohne Preis zulässige physische Entnahme mit sichtbarer Bewertungsprüfung, keine erfundene Kostensumme. Spätere Bewertung als auditierte Ergänzung.
  - [ ] Neue Entnahmen in Projektmapper separat summieren, Frontend addiert genau diesen Wert zusätzlich zu historischen AIP `ausLager`; neue Teilentnahme setzt das AIP-Gesamtflag nicht. Historische AIP nicht erneut buchen. Geplante Angebots-/Bestellkosten hier niemals addieren.
  - [ ] Dieser Task liefert ausschließlich Entnahmedaten; der lesende PDF-Endpunkt wird in Task14 umgesetzt und Task28 verbindet den Druckknopf. Druck/Download hat keinerlei Mutationsaufruf. Projektkostenregression mit Lageranteil+Rechnungsteil testen; eigener Playwrightablauf auf bestehender Projektseite zeigt neue bestätigte Teilentnahmekosten, offenen Bewertungsstatus und unveränderte Rechnungsanteile nach Reload. Alle3Desktopgrößen/Designnachweis, Unit-/Component-/E2Etests grün.

### Task 6: Versionierte technische Anlagen und sichere Downloads

- Branch: `codex/beschaffung-task-6`
- Worktree: `.claude/worktrees/beschaffung-task-6`
- E2E-Port: `5187`
- Consumes-Gate: 1, 4. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufDatei.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufAnlageVersion.java`; `src/main/java/org/example/kalkulationsprogramm/repository/EinkaufDateiRepository.java`, `src/main/java/org/example/kalkulationsprogramm/repository/EinkaufAnlageVersionRepository.java`; `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufDateiService.java`; `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufDateiDto.java`; `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufDateiController.java`; `src/main/resources/db/migration/V380__einkauf_dateien.sql`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufDateiServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/EinkaufDateiControllerTest.java`.
- Vorbild: `ArtikelDokumentService.java:108`, `EmailService.java:41`; LieferantDokument/EmailAttachment-Dateireferenz statt Kopien.
- Interfaces – Produces: `AnlageDto hochladen(Long bedarfId, MultipartFile datei, String revision, Long akteurId)`; `Resource laden(Long dateiId, Authentication auth)`; `List<EmailService.Attachment> ladeVersandanlagen(List<Long> versionIds)`; `void pruefePaketgroesse(List<Long> ids, long pdfBytes)`. Consumes Tasks1,4.
- Steps:
  - [ ] Rote Tests für ../, Double-Extension, EXE statt PDF, ZIP-Bombe, 10MiB-Grenze, fehlende historische Datei, gleiche Bytes zweimal, Zugriff über fremde Positions-ID und Änderung einer schon versendeten Revision.
  - [ ] Dateien mit Hash, Byteanzahl, validiertem MIME/Format und UUID im lokalen Uploadroot ablegen; PDF/DXF/STEP sowie geprüfte PNG/JPEG-Schnittbilder erlauben. PDF-Signatur, DXF/STEP-Header prüfen; keine externen Referenzen ausführen. Dateipfade normalisieren/startsWith; sichere Content-Disposition.
  - [ ] `einkauf_datei` Hash unique, Anlageversion mit Positions-/Revisionsbindung. Bestand per EmailAttachment/LieferantDokument referenzieren, wenn vorhanden; keine physischen Duplikate. Gesendete Versionsreferenzen nie überschreiben oder löschen.
  - [ ] Paketprüfung einschließlich Base64-/Headerreserve vor Vorschau, tatsächliche MIME-Größe nochmals vor Queue. Fehlende Datei 409 „Anlage fehlt, bitte neu hochladen“; niemals still auslassen. Gezielte Tests grün.

### Task 11: Kontobezogener IMAP-Import und Mailidentität

- Branch: `codex/beschaffung-task-11`
- Worktree: `.claude/worktrees/beschaffung-task-11`
- E2E-Port: `5192`
- Consumes-Gate: 9, 10, 38. Vor Start geprüft und in den Featurebranch integriert.
- Files: ändern `src/main/java/org/example/kalkulationsprogramm/domain/Email.java`, `src/main/java/org/example/kalkulationsprogramm/repository/EmailRepository.java`, `src/main/java/org/example/kalkulationsprogramm/service/EmailImportService.java`, `src/main/java/org/example/kalkulationsprogramm/service/EmailOutboundPersistenceService.java`, `src/main/java/org/example/kalkulationsprogramm/service/BounceErkennungService.java`; neu `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EmailImportIdentitaet.java`, `src/main/java/org/example/kalkulationsprogramm/repository/EmailImportIdentitaetRepository.java` unter demselben Basispfad; `src/main/resources/db/migration/V384__email_kontobezug_importidentitaet.sql`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EmailImportServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/EmailKontoImportTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/BounceErkennungServiceTest.java`.
- Vorbild: `EmailImportService.java:157,324,663`, `EmailRepository.java:58,338`, `EmailOutboundPersistenceService.java:43`.
- Interfaces – Produces: `int doImport(String kontoId)`; `boolean importMessage(Message msg,IMAPFolder folder,EmailDirection direction,String kontoId)`; Repository `findByKontoIdAndMessageId(String,String)`, `existsByKontoIdAndMessageId(String,String)`, `findByKontoIdAndMessageIdIn(String,Collection<String>)`. Alte Methoden/Overloads delegieren mit HAUPT für Altcaller. `GET/POST /api/einkauf/mail/abruf` kommt bei Task16, Admin-Mailtest nicht als Abruf missbrauchen. Consumes9,10.
- Steps:
  - [ ] Geprüfte `LocalTestMailPolicy` aus Task38 in allen neuen kontoabhängigen Netzwerkpfaden vor Resolver-Fallback/SMTP/IMAP/Workerdispatch anwenden; keine Umgehung über Testmail, Verbindungsprüfung, manuellen Abruf oder Retry. Bei local-test ohne Opt-in null Netzwerkaufrufe, mit Opt-in ausschließlich EINKAUF.
  - [ ] Gleiche Message-ID in zwei Konten ergibt zwei korrekt getrennte Nachrichten; gleicher UID-Import mehrfach nur einmal; UIDVALIDITY-Wechsel, Ordnerwechsel/Sent-Kopie, Message-ID fehlt, doppelter gleichzeitiger Import, Hauptkonto läuft weiter trotz Einkaufsfehler.
  - [ ] `email.konto_id` backfill HAUPT; globales Unique für Message-ID einschließlich implizitem Column-Unique durch `(konto_id,message_id)` ersetzen. Neue `email_import_identitaet` unique(konto_id,folder,uidvalidity,uid), Email-FK; fallback Message-ID enthält diese Werte. Deduplizierung derselben Mail in mehreren Ordnern innerhalb desselben Kontos über Message-ID, unterschiedliche identische IDs mit abweichendem Inhalt als Prüfkonflikt erfassen.
  - [ ] Kontoordner/Konfiguration aus Resolver; bisherige Hauptkontoordnerliste als HAUPT-Default behalten. Parent-/Bounce-Suche immer kontogebunden, keine Übernahme einkaufsfremder Betreffheuristik. Auth-/Auto-Submitted-/In-Reply-To-/References-Daten für Task16 persistieren. Einkaufsverarbeitung nach Commit melden; pro Nachricht Transaktion, kein IMAP-Netz unter DB-Transaktion.
  - [ ] Legacy-Outboundpersistence explizit HAUPT/DOKUMENTE zuweisen, normale manuelle Antworten auf Einkaufsmails müssen später Task35-API verwenden. Scheduler kontoweise isoliert Fehler behandeln. Vollständige bestehenden Import-/Bounce-Tests grün.

## Abschnitt 5 (Tasks 7, 13, 14; disjunkte Dateien)

### Task 7: HiCAD-Excel-Vorschau und idempotente Bedarfsübernahme

- Branch: `codex/beschaffung-task-7`
- Worktree: `.claude/worktrees/beschaffung-task-7`
- E2E-Port: `5188`
- Consumes-Gate: 2, 3, 4, 6. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/HiCadImport.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/HiCadImportZeile.java`; `src/main/java/org/example/kalkulationsprogramm/repository/HiCadImportRepository.java`; `src/main/java/org/example/kalkulationsprogramm/service/einkauf/HiCadImportService.java`; `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/HiCadImportDto.java`; `src/main/java/org/example/kalkulationsprogramm/controller/HiCadImportController.java`; `src/main/resources/db/migration/V381__hicad_bedarfsimport.sql`; `pom.xml`; Tests `src/test/java/org/example/kalkulationsprogramm/service/HiCadImportServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/HiCadImportControllerTest.java`.
- Vorbild: `ArtikelMatchingService.java:31` nur Vorschlagsidee (dessen findAll/Fuzzy-Autoverhalten nicht übernehmen), bestehende Schnittform/Winkel aus Task3. Kein bestehender HiCAD-Parser auf diesem Stand.
- Interfaces – Produces: `Vorschau vorschau(Long projektId, MultipartFile file, SpaltenMapping mapping, Long akteurId)`; `List<EinkaufBedarfDto.Response> uebernehmen(Long importId, Uebernahme r, Long akteurId)`; `Zeile(int zeilennummer, String rohtext, PositionSnapshot vorschlag, List<Long> artikelKandidaten, boolean bereitsUebernommen, List<String> hinweise)`; `Uebernahme(long version,List<ZeilenAuswahl> zeilen,boolean duplikatBewusst,UUID idempotenzKey)`; `ZeilenAuswahl(int zeilennummer,BigDecimal menge,PositionSnapshot korrigiert)`; SpaltenMapping mappt Feldnamen auf Spaltenindex. API POST `/api/einkauf/hicad/vorschau`, `/{id}/uebernehmen`. Consumes3,4,6.
- Steps:
  - [ ] In-Memory-POI-Dummyworkbooks für deutsche Zahlen, Stück/Länge/Werkstoff/Winkel, uneindeutige Güte, Bildanker, doppelte Datei, ausgewählte Teilmenge und erneuten Retry erzeugen.
  - [ ] XLS/XLSX erkennen, maximal10MiB/10000Zeilen/20Spalten, POI-ZIP-Limits, keine Formelauswertung/externe Links/Makros. Header-Aliase erkennen und in Vorschau sichtbares manuelles Mapping erlauben, statt ein unbewiesenes festes HiCAD-Layout anzunehmen. Stückzahl/Länge in gemeinsames Modell normalisieren; Rohwerte erhalten.
  - [ ] Exact-Matching über interne Nummer plus Technik; Fuzzy liefert nur Kandidaten, insbesondere S235/S355 nicht gleichsetzen. Eingebettete Bilder über Zellanker als ungeprüfte Anlagen anzeigen und vor Übernahme bestätigen lassen.
  - [ ] Dateihash+Projekt+Ursprungszeile+bewusste Importinstanz speichern, übernommene Teilmenge tracken; zweite Übernahme nur verbleibende Auswahl oder ausdrücklich bestätigte neue Instanz. Upload/Preview erzeugt noch keinen Bedarf. Atomar nur bestätigte Zeilen übernehmen; API-/Parsertests grün.

### Task 13: Revisionsfähige Mehrlieferantenanfragen

- Branch: `codex/beschaffung-task-13`
- Worktree: `.claude/worktrees/beschaffung-task-13`
- E2E-Port: `5194`
- Consumes-Gate: 1, 2, 3, 4, 6, 8. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/Einkaufsanfrage.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/AnfrageRevision.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/AnfragePosition.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/AnfrageHerkunft.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/AnfrageLieferant.java`; neu `src/main/java/org/example/kalkulationsprogramm/repository/EinkaufsanfrageRepository.java`, `src/main/java/org/example/kalkulationsprogramm/repository/AnfrageRevisionRepository.java`, `src/main/java/org/example/kalkulationsprogramm/repository/AnfrageLieferantRepository.java`, `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufsanfrageService.java`, `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufsanfrageDto.java`, `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufsanfrageController.java` unter Basispfad; `src/main/resources/db/migration/V386__einkaufsanfragen_revisionen.sql`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufsanfrageServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/EinkaufsanfrageControllerTest.java`.
- Vorbild: `LieferantArtikelpreisService.java:77` (Historie), `BestellungService.java:75` (zu ersetzendes externes Mapping), `AusgangsGeschaeftsDokument`-Snapshots als fachliches Muster; keine Kunden-Anfrage-Entity wiederverwenden.
- Interfaces – Produces: `Detail anlegen(Create r,Long akteurId)`; `Detail revidieren(Long id,RevisionRequest r,Long akteurId)`; `Detail laden(Long id)`; `Page<Kopf> suchen(Pageable p)`; `Create(List<Herkunft> positionen,List<Snapshot> empfaenger,LocalDate antwortfrist,LocalDate liefertermin,Long zustaendigId,UUID idempotenzKey)`; `RevisionRequest(long version,Create inhalt)`; Detail enthält Kopf-ID/Version/PA-Nummer, Revision-ID/-Nummer, Positionen mit stabilen IDs+Herkünften, Lieferantbeteiligungen, Fristen/Status. APIs GET/POST `/api/einkauf/anfragen`, GET `/{id}`, POST `/{id}/revisionen`. Consumes1–4,6,8.
- Steps:
  - [ ] Test Bedarf10→Anfrage4→3Lieferanten unverändert offen10; unterschiedliche Zeugnisse/Zeichnungsrevision/Lieferort getrennt; alte Revision immutable und Angebot verweist weiter darauf; IDs fremder Revision/Lieferant abweisen.
  - [ ] PA aus Task2 einmal am Kopf; Revisionen unique(kopf,nummer). Lieferantenneutraler Entwurf darf empfaenger=[] und noch offene Fristen haben; Versandvorbereitung verlangt vollständig gewählte Empfänger/Fristen. So kann Task28 gewählten Bedarf persistieren und auf die bereits registrierte Detailseite weiterführen. Snapshotpositionen über `mitTeilmenge` auf die tatsächlich angefragten4 statt ursprünglichen10 skalieren (Profilstückzahl/Länge/Gewicht konsistent), Quellenmengen persistent, Teilmengen <= disponierbar bei Übernahme prüfen, aber Anfrage reserviert nicht. Gesendete Revision nie updaten; neue technische Änderung klont vollständig mit neuer Revision.
  - [ ] AnfrageLieferant mit cryptorandom Rückmeldecode unique, Kontaktsnapshot, Absage/Antwortstatus und getrennten Versandversuchen; identische fachliche Revision an alle Empfänger, keine fremden Adress-/Preisdaten im DTO je Versand. Replycode ist Zuordnungshilfe, kein Berechtigungsnachweis.
  - [ ] Zuständigkeit/Fristen editierbar mit Version, Absage/Erledigung auditiert; Summen angefragt aus aktueller Revision ohne Lieferantenmultiplikation. Tests grün.

### Task 14: Gemeinsame Anfrage-/Bestell-/Entnahme-PDFs

- Branch: `codex/beschaffung-task-14`
- Worktree: `.claude/worktrees/beschaffung-task-14`
- E2E-Port: `5195`
- Consumes-Gate: 1, 3, 4, 5, 6, 8. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufPdfController.java`, `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufPdfService.java`, `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufPdfPositionsRenderer.java`, `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufPdfDto.java`; ändern `src/main/java/org/example/kalkulationsprogramm/service/BestellungPdfService.java`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufPdfServiceTest.java`, bestehende `src/test/java/org/example/kalkulationsprogramm/service/BestellungPdfServiceTest.java`.
- Vorbild: `BestellungPdfService.java:277,354` (Schnittsymbol, Profiltext) und Firmenlogo `:268`. Kein Anhängen von Textbytes an fertige PDFs wie im Altcode.
- Interfaces – Produces: `byte[] erzeugen(Beleg r)`; `Beleg(String typ,String nummer,int revision,Snapshot empfaenger,List<PdfPosition> positionen,List<PdfKosten> kopfkosten,List<Liefergruppe> liefergruppen,LocalDate antwortfrist,LocalDate liefertermin,String bedingungen,BigDecimal nettoSumme,boolean entwurf)`; `PdfPosition(String positionsnummer,PositionSnapshot technik,List<PdfHerkunft> herkuenfte,List<PdfKosten> kosten,BigDecimal nettoSumme)`; `PdfHerkunft(Long bedarfId,String projektNummer,BigDecimal menge,Einheit einheit)`; `PdfKosten(String bezeichnung,BigDecimal betrag,String basis,BigDecimal basisMenge,boolean enthalten,String rechenweg)`; `Resource entnahmeliste(List<Long> bedarfIds)` über GET `/api/einkauf/lagerentnahmen/pdf` mit separatem neuen `controller/EinkaufPdfController.java` unter Java-Basispfad. Consumes3–6,8; Anfrage/Bestellung liefern DTO, Renderer kennt keine veränderlichen Stammdaten.
- Steps:
  - [ ] PDFBox-Text-/Seitentest: interne Nummer, 4Stück à6000mm, ein Schnittbild, Winkel links/rechts, Werkstoff/Oberfläche/Bearbeitung, mehrere Dokumentanforderungen, Liefergruppen, PA/B+Revision und Seitenumbruch bei60Positionen.
  - [ ] Gemeinsame Positionsrender-Funktionen extrahieren und legacy BestellungPdfService delegieren lassen, Fehler nicht schlucken. PDF aus Bytes, Firmenlogo vorhanden/nicht vorhanden, lange Texte und Umlaute sauber umbrechen.
  - [ ] Anfragen ohne Preise anderer Lieferanten; Bestell-PDF ausschließlich eingefrorene gewählte Konditionen; Entnahmeblatt mit Abhak-/Istmengefeldern und Kennzeichnung „Ausdruck bestätigt keine Entnahme“. PDFgenerierung allein keine Nummer/Status-/Mengenänderung.
  - [ ] Ergebnis vor Queue archivieren und Hash vergleichen; PDF-Tests einschließlich Altregression grün.

## Abschnitt 6 (Tasks 15; disjunkte Dateien)

### Task 15: Persistente Versand-Outbox und vorsichtige Wiederholungen

- Branch: `codex/beschaffung-task-15`
- Worktree: `.claude/worktrees/beschaffung-task-15`
- E2E-Port: `5196`
- Consumes-Gate: 1, 6, 9, 10, 38. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufVersandauftrag.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufVersandversuch.java`; `src/main/java/org/example/kalkulationsprogramm/repository/EinkaufVersandauftragRepository.java`; `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufOutboxService.java`, `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufVersandWorker.java`; `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufVersandDto.java`; `src/main/resources/db/migration/V387__einkauf_versand_outbox.sql`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufOutboxServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/EinkaufVersandWorkerTest.java`, `src/test/java/org/example/kalkulationsprogramm/repository/EinkaufOutboxParallelTest.java`.
- Vorbild: `EmailService.java:498`, `SentMailArchiver.java:84`; keine bestehende belastbare Outbox, daher eigener klar begrenzter Baustein.
- Interfaces – Produces: `VersandDto einreihen(VersandSnapshot s,UUID idempotenzKey,Long akteurId)`; `void verarbeite(Long auftragId)`; `VersandDto erneutVersuchen(Long id,long version,Long akteurId)`; `void klaeren(Long id,Klaerung r,Long akteurId)`; `VersandSnapshot(String typ,Long vorgangId,Long revisionId,Long beteiligungId,KontoZugangReferenz konto,Nachricht nachricht,String freigabeHash)` ; `KontoZugangReferenz(String kontoId)` (keine Secrets); Ereignis `EinkaufVersandAngenommen(Long versandId,String typ,Long vorgangId,Long revisionId,Instant zeit)`. Consumes6,9,10.
- Steps:
  - [ ] Geprüfte `LocalTestMailPolicy` aus Task38 in allen neuen kontoabhängigen Netzwerkpfaden vor Resolver-Fallback/SMTP/IMAP/Workerdispatch anwenden; keine Umgehung über Testmail, Verbindungsprüfung, manuellen Abruf oder Retry. Bei local-test ohne Opt-in null Netzwerkaufrufe, mit Opt-in ausschließlich EINKAUF.
  - [ ] Crash-/Retrytests: zwei Worker nur ein SMTP-Start; Timeout nach DATA→UNKLAR; Neustart bei LAEUFT→UNKLAR; eindeutiger Reject wiederholbar; DB-Ausfall nach SMTP-Annahme nicht automatisch erneut senden; Sent-APPEND-Fail führt ausschließlich Archiv-Retry aus.
  - [ ] Outbox/Versuch immutable Payload/MIMEhash, stabile Message-ID vor Netz, unique Idempotenzkey, Zustand VORBEREITET/LAEUFT/ANGENOMMEN/FEHLGESCHLAGEN/UNKLAR, getrennte Archivablage. Worker claim in kurzer DB-Transaktion, Netzwerk danach, Ergebnis danach. Annahmeereignis persistiert/in derselben Ergebnis-Transaktion verarbeiten; Wiederverarbeitung idempotent.
  - [ ] Bekannter sicherer Fehler darf nur gezielt mit Berechtigung erneut versucht werden; unsicherer Versand verlangt Beleg+Entscheidung „bereits angenommen“ oder „nachweislich nicht gesendet“. Keine automatische Zustandsfreigabe nach Frist. Erfolgreicher SMTP-Status ist kein Zustell-/Lieferantennachweis.
  - [ ] Queue kann gezielt unmittelbar nach Nutzerfreigabe asynchron verarbeitet werden; zyklische Recovery separat abschaltbar (Task38). Logs nur IDs/Zustände. MySQL-/Worker-Tests grün.

## Abschnitt 7 (Tasks 16; disjunkte Dateien)

### Task 16: Anfrageversand und vollständige Antwortzuordnung

- Branch: `codex/beschaffung-task-16`
- Worktree: `.claude/worktrees/beschaffung-task-16`
- E2E-Port: `5197`
- Consumes-Gate: 1, 9, 10, 11, 12, 13, 14, 15, 38. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufMailZuordnung.java`, `src/main/java/org/example/kalkulationsprogramm/repository/EinkaufMailZuordnungRepository.java`, `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufKommunikationService.java`, `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufAntwortZuordnungService.java`, `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufKommunikationDto.java`, `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufKommunikationController.java`; neue Kommunikationsmigration `src/main/resources/db/migration/V388__einkauf_kommunikation.sql`; ändern `src/main/java/org/example/kalkulationsprogramm/service/EmailImportService.java`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufAntwortZuordnungServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/EinkaufKommunikationServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/EinkaufKommunikationControllerTest.java`.
- Vorbild: `EmailImportService.java:663`, `EmailAutoAssignmentService` als Integrationspunkt; bestehende Zuordnung Projekt/Anfrage/Lieferant bleibt, Einkaufslink zusätzliche Beziehung.
- Interfaces – Produces: `Vorschau vorschau(Long anfrageId,Long beteiligungId,Long templateId)`; `VersandDto senden(Long anfrageId,Long beteiligungId,Freigabe r,Long akteurId)`; `Zuordnungsergebnis zuordnen(Long emailId)`; `void bestaetigen(Long emailId,ZuordnungRequest r,Long akteurId)`; `Page<NachrichtDto> verlauf(String typ,Long vorgangId,Pageable p)`; `Vorschau(long version,String vorschauHash,String subject,String htmlBody,String empfaenger,Long pdfDateiId,List<Long> anlageVersionIds)`; `Freigabe(long version,String vorschauHash,UUID idempotenzKey)`; `ZuordnungRequest(String typ,Long vorgangId,Long beteiligungId,Long revisionId,String begruendung)`; APIs `/api/einkauf/anfragen/{id}/lieferanten/{beteiligungId}/vorschau|senden`, `/api/einkauf/mail/{emailId}/zuordnung`, `/api/einkauf/mail/abruf`, `/api/einkauf/{typ}/{id}/verlauf`. Consumes1,9–15.
- Steps:
  - [ ] Geprüfte `LocalTestMailPolicy` aus Task38 in allen neuen kontoabhängigen Netzwerkpfaden vor Resolver-Fallback/SMTP/IMAP/Workerdispatch anwenden; keine Umgehung über Testmail, Verbindungsprüfung, manuellen Abruf oder Retry. Bei local-test ohne Opt-in null Netzwerkaufrufe, mit Opt-in ausschließlich EINKAUF.
  - [ ] Tests veränderter Betreff bei korrekten Threadheadern, Codefallback, bekannter Kontakt+eindeutige PA, widersprüchliche Header/Code, fremder Absender, Nummer nur PDF, Autoantwort, Absage und Bounce; mehrere Antworten überschreiben keine.
  - [ ] Vorschau enthält serverseitig gerenderten Text/PDF, Empfängersnapshot und freigegebene Anlagen; hashgebundene Freigabe friert Revision pro Beteiligung ein und legt nur deren Outbox an. Fehler/Erfolg pro Lieferant sichtbar, kein Masserneuversand erfolgreicher Beteiligungen.
  - [ ] Nach Import kontogebunden Headerkandidaten gegen Code/Absender plausibilisieren. Widerspruch/neuer Kontakt/mehrere Treffer immer PRUEFEN; exakte bekannte Header+Absender zuerst, dann Code+bekannter Absender, dann PA+Kontakt nur eindeutig. PDF-Nummer ausschließlich Quellenvorschlag; manuelle bestätigte Zuordnung auditiert. Threadlink erteilt nie Preis-/Bestell-/Materialfreigabe.
  - [ ] Status je Nachricht `ANGEBOT, RUECKFRAGE, ABSAGE, AUTOMATISCHE_ANTWORT, UNZUSTELLBAR, SONSTIG, PRUEFEN`; Angebot erst nach Klassifizierung/Erfassung zählen, nicht jeder Eingang. Request- und später Bestellverlauf getrennt referenzieren; alle Nachrichten paginiert. Gezielter manueller Abruf EINKAUF ohne Hauptkonto-Scheduler. Tests grün.

## Abschnitt 8 (Tasks 17; disjunkte Dateien)

### Task 17: Manuelle Lieferantenangebote und unveränderliche Angebotsversionen

- Branch: `codex/beschaffung-task-17`
- Worktree: `.claude/worktrees/beschaffung-task-17`
- E2E-Port: `5198`
- Consumes-Gate: 1, 3, 13, 16. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufAngebot.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/AngebotVersion.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/AngebotPosition.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/AngebotKostenbestandteil.java`; `src/main/java/org/example/kalkulationsprogramm/repository/EinkaufAngebotRepository.java`, `src/main/java/org/example/kalkulationsprogramm/repository/AngebotVersionRepository.java`; `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufAngebotService.java`; `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufAngebotDto.java`; `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufAngebotController.java` unter Java-Basispfad; `src/main/resources/db/migration/V389__einkauf_angebote.sql`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufAngebotServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/EinkaufAngebotControllerTest.java`.
- Vorbild: Preishistorie `LieferantenArtikelPreise.java:115`, `LieferantGeschaeftsdokument.java:33`; keine Verwendung des Kundenangebotsmodells.
- Interfaces – Produces: `VersionDto erfassen(Long beteiligungId,Erfassung r,Long akteurId)`; `VersionDto bestaetigen(Long versionId,long version,Long akteurId)`; `VersionDto neueVersion(Long angebotId,Erfassung r,Long akteurId)`; `Erfassung(Long anfrageRevisionId,String angebotsnummer,LocalDate datum,LocalDate gueltigBis,String waehrung,List<Position> positionen,List<Kosten> kosten,String zahlungsbedingungen,BigDecimal skontoProzent,Integer skontoTage,Long emailId,Long originalDateiId)`; `Position(Long anfragePositionId,String originalNummer,String originalText,Mengenbasis angeboten,BigDecimal mindestmenge,BigDecimal verpackungseinheit,LocalDate liefertermin,List<String> abweichungen,List<ZeugnisZusage> zeugnisse,List<Kosten> kosten)`; `Kosten(String schluessel,String art,BigDecimal betrag,String basis,BigDecimal basisMenge,String prozentBasisSchluessel,boolean enthalten,boolean variabel,String quelle)`; `ZeugnisZusage(Dokumentart art,String status,BigDecimal aufpreis)`; API POST `/api/einkauf/anfrage-lieferanten/{id}/angebote`, GET/PUT `/api/einkauf/angebote/{id}`, POST `/{id}/versionen|bestaetigen`. Consumes1,3,13,16.
- Steps:
  - [ ] Tests Angebot auf alter Revision bleibt alt; mehrere Originalmails/Versionen bleiben; fehlender Preis null; falsche Lieferanten-/Positions-/Dateireferenz abweisen; menschlich bestätigte Version kann nicht durch erneute Erfassung überschrieben werden.
  - [ ] Angebotkopf je Beteiligung, Versionunique(angebot,nummer), Originaldatei-/Emailreferenz, Datum/Gültigkeit, Liefergruppen, Bedingungen und quellbezogene Positionsdaten persistieren. Status ERFASST/GEPRUEFT/ABGELOEST, Optimistic Lock für bearbeitbaren Stand; Bestätigung friert Werte ein.
  - [ ] Kostenarten MATERIAL/FRACHT/ZUSCHNITT/VERPACKUNG/MINDERMENGE/ZEUGNIS/LEGIERUNG/SCHROTT/ENERGIE/MENGE/GUETE/BEARBEITUNG/OBERFLAECHE/RABATT, Basis STUECK/M/KG/100KG/T/PROZENT/PAUSCHAL. enthalten/zusätzlich explizit; Prozentbasis referenziert Kostenkeys und darf keinen Zyklus bilden. Fehlende Felder bleiben offen; technische Ersatzangebote verlangen separate menschliche Abweichungsbestätigung.
  - [ ] Zeugnisstatus ENTHALTEN/AUFPREIS/NICHT_LIEFERBAR/OFFEN pro Sollart. Original-Lieferantennummer wird Zusatzinformation, nie interner Schlüssel. Manuelle Erfassung unabhängig von KI funktionsfähig. Tests grün.

## Abschnitt 9 (Tasks 18; disjunkte Dateien)

### Task 18: Deterministischer Vollkostenvergleich und belegte Umrechnung

- Branch: `codex/beschaffung-task-18`
- Worktree: `.claude/worktrees/beschaffung-task-18`
- E2E-Port: `5199`
- Consumes-Gate: 1, 3, 13, 17. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufMengenUmrechnung.java`, `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufVergleichService.java`, `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufVergleichDto.java`, `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufVergleichController.java`; ändern `src/main/java/org/example/kalkulationsprogramm/service/PreisUebernahmeService.java` für wiederverwendbare Einheitendeutung ohne Änderung des Altvertrags; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufMengenUmrechnungTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/EinkaufVergleichServiceTest.java`, bestehende `src/test/java/org/example/kalkulationsprogramm/service/PreisUebernahmeServiceTest.java`.
- Vorbild: `PreisUebernahmeService.java:385` (Preisbasis), `ArtikelPositionsPreisService` für Aufteilung; Specbeispiel A1060/B1080/Cunvollständig als feste Regression.
- Interfaces – Produces: `Umrechnung normalisieren(Mengenbasis quelle,Einheit ziel)`; `Umrechnung(BigDecimal menge,Einheit einheit,BigDecimal faktor,String quelle,boolean vollstaendig,String hinweis)`; `Vergleich vergleiche(Long anfrageId,LocalDate stichtag)`; `AngebotSumme berechne(VersionDto angebot,List<Herkunft> paket,LocalDate stichtag)`; `AngebotSumme(Long angebotVersionId,BigDecimal nettoGesamt,boolean vollstaendig,boolean technischGeeignet,boolean gueltig,List<String> hindernisse,List<Rechenschritt> rechnung)`; `Rechenschritt(String key,String formel,BigDecimal basis,BigDecimal ergebnis,String quellenbezug)`; GET `/api/einkauf/anfragen/{id}/vergleich`. Consumes3,13,17.
- Steps:
  - [ ] Parameterisierte rote Tests Stück/m/kg/t/100kg und100Stück; Faktor unbekannt; enthaltene Zuschläge; variable Basis offen; Fracht je Liefergruppe; Paketrabatt fällt bei verändertem Paket weg; Skonto separat; USD nicht gerankt; Datum abgelaufen.
  - [ ] Einheitencodes zentral deuten; kg↔m nur mit belegtem kgJeMeter, Stück↔Länge nur mit passender Einzellänge. Unbekannt niemals Faktor1 im Einkauf. Shared Parsing aus bestehendem Service extrahieren und Altverhalten durch separate Fallbackstrategie erhalten.
  - [ ] Nettokosten mit BigDecimal berechnen, jeder Bestandteil einmal anhand stabilem Key; enthaltene Komponenten nur anzeigen. Prozentkosten gegen explizite Basis, keine versehentliche Rekursion/Doppeladdition. Mindestmengen/Verpackungseinheiten nicht still aufrunden; bewusste neue Menge erfordert neue Bestellprüfung.
  - [ ] Ranking nur EUR, vollständige, gültige, technisch bestätigte Pakete; unvollständige Zeilen behalten null, Warnungen+Originalbasis anzeigen. Vergleichsbegründung deterministisch verfügbar, KI optional. 100%-Utility-Tests und API-Rechte grün.

## Abschnitt 10 (Tasks 19, 20; disjunkte Dateien)

### Task 19: Bestätigte Preisübernahme in bestehende Historie

- Branch: `codex/beschaffung-task-19`
- Worktree: `.claude/worktrees/beschaffung-task-19`
- E2E-Port: `5200`
- Consumes-Gate: 1, 17, 18. Vor Start geprüft und in den Featurebranch integriert.
- Files: ändern `src/main/java/org/example/kalkulationsprogramm/domain/LieferantenArtikelPreise.java`, `src/main/java/org/example/kalkulationsprogramm/repository/LieferantenArtikelPreiseRepository.java`, `src/main/java/org/example/kalkulationsprogramm/service/LieferantArtikelpreisService.java`, `src/main/java/org/example/kalkulationsprogramm/service/LieferantArtikelpreisMapper.java`, `src/main/java/org/example/kalkulationsprogramm/dto/Lieferant/LieferantArtikelpreisDto.java`, `src/main/java/org/example/kalkulationsprogramm/domain/Artikel.java`; neu `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufPreisUebernahmeService.java`, `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufPreisUebernahmeDto.java`, `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufPreisUebernahmeController.java`; `src/main/resources/db/migration/V390__einkauf_preishistorie_quellen.sql`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufPreisUebernahmeServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/LieferantArtikelpreisServiceTest.java`.
- Vorbild: `LieferantArtikelpreisService.java:77`, `LieferantenArtikelPreise.java:115`, `PreisQuelle.ANGEBOT_EMAIL` existiert.
- Interfaces – Produces: `LieferantArtikelpreisDto uebernehmen(Long angebotVersionId,Long angebotPositionId,PreisUebernahme r,Long akteurId)`; `PreisUebernahme(String scope,Long projektId,BigDecimal abMenge,BigDecimal bisMenge,String begruendung,UUID idempotenzKey)`; `Optional<Preisvorschlag> letzterBestaetigterPreis(Long artikelId,Long lieferantId,Long projektId,BigDecimal menge,LocalDate datum)`; POST `/api/einkauf/angebote/{id}/positionen/{positionId}/preis-uebernehmen`. Consumes17,18.
- Steps:
  - [ ] Tests gleicher Preis/neue Quelle bleibt neue nachvollziehbare Fassung, Wiederholungskey keine Duplikate, Projektsonderpreis nicht Standard, abgelaufener Preis nur Hinweis, 1,83€/100Stück→0,0183, Fracht nicht im allgemeinen Artikelpreis, Projektkosten unverändert.
  - [ ] History um Quelle-Angebotsversion/-position, fachliches Datum, Gültigkeit, Währung/Einheit/Preisbasis, Scope STANDARD/PROJEKT/MENGENSTAFFEL, Grenzen und geprüfte Komponenten ergänzen. Bestehende Zeilen bleiben Standard. Aktuell-Queries dürfen scoped prices nicht unbesehen als allgemeinen günstigsten Preis verwenden; `Artikel.getAktuellePreise/getGuenstigsterPreis` entsprechend filtern.
  - [ ] Übernahme nur aus menschlich geprüfter Position mit eindeutiger Artikel-ID, normalisierbarer Basis und vollständigen wiederkehrenden Materialkosten; einmalige Fracht/Bearbeitung nicht hineinmischen. Auf Artikel/Lieferant sperren und einen neuen scoped Stand erzeugen; alten passenden Scope veralten lassen, andere Scopes erhalten.
  - [ ] Vorhandenen Historyservice durch Overload mit Herkunft/Scope erweitern, Alt-Signaturen bleiben. Kein Aufruf Projekt-/Materialkostenbuchung. Tests grün.

### Task 20: Quellenbelegte KI-Vorschläge ohne Überschreiben bestätigter Daten

- Branch: `codex/beschaffung-task-20`
- Worktree: `.claude/worktrees/beschaffung-task-20`
- E2E-Port: `5201`
- Consumes-Gate: 1, 6, 16, 17, 18. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufAnalyseJob.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufAnalyseVorschlag.java`; `src/main/java/org/example/kalkulationsprogramm/repository/EinkaufAnalyseJobRepository.java`; `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufAngebotsAnalyseService.java`, `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufAnalyseWorker.java`; `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufAnalyseDto.java`; `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufAnalyseController.java`; `src/main/resources/db/migration/V391__einkauf_ki_vorschlaege.sql`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufAngebotsAnalyseServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/EinkaufAnalyseWorkerTest.java`.
- Vorbild: `GeminiDokumentAnalyseService.java:542` (bestehender Client), PDFBox-Verarbeitung `:669`. Keine Übernahme der Rechnungserkennung mit automatischer Preisbuchung.
- Interfaces – Produces: `JobDto starten(Long emailId,Long angebotId,Long akteurId)`; `void analysiere(Long jobId)`; `List<FeldVorschlag> vorschlaege(Long jobId)`; `VersionDto uebernehmen(Long jobId,Uebernahme r,Long akteurId)`; `FeldVorschlag(String feldpfad,JsonNode wert,Quelle quelle,BigDecimal confidence,String hinweis)`; `Quelle(Long emailId,Long dateiId,Integer seite,String zitat,Integer textStart,Integer textEnd)`; `Uebernahme(long erwarteteAngebotVersion,List<String> akzeptierteFeldpfade,Map<String,JsonNode> korrekturen)`; APIs `/api/einkauf/analysen`. Consumes6,16–18.
- Steps:
  - [ ] Fake-KI antwortet mit Quellen, fehlender Seite, ungültigem Feldtyp und Promptinjection; Tests bestätigen manuelle Korrektur bleibt nach zweitem Lauf, AI-Ausfall blockiert manuelle Erfassung nicht, jede geeignete Einzelantwort startet eigenen Job.
  - [ ] Persistenter deduplizierter Jobkey(email,Anlagenhash,ParserVersion), Hintergrundverarbeitung nach Commit. E-Mail/PDF als untrusted content explizit vom Systemprompt trennen; schema-validierte Ausgabe mit Mengen/Einheiten/Gültigkeit/Zeugnis-/Kostenfeldern. CAD nicht an KI senden; PDF/Text nur sichere begrenzte Bytes.
  - [ ] Fundstelle gegen tatsächlich extrahierten Seitentext/Zitat validieren; unbelegte Felder als unbestätigter Hinweis, nicht Rangfolgewert. PDF-only PA/B-Fundstelle erzeugt Zuordnungsvorschlag an Task16, keine automatische Zuordnung.
  - [ ] Übernahme akzeptierter Felder in bearbeitbare Angebotsfassung mit Optimistic Lock; geprüfte Fassung erzeugt neue Version, niemals blind aktualisieren. Empfehlung erhält ausschließlich Task18-Zahlen+Quellen, deterministischer Textfallback. Tests grün, echter Provider nie Voraussetzung.

## Abschnitt 11 (Tasks 21; disjunkte Dateien)

### Task 21: Nummerierte Bestellentwürfe aus Angebot oder Direktbestellung

- Branch: `codex/beschaffung-task-21`
- Worktree: `.claude/worktrees/beschaffung-task-21`
- E2E-Port: `5202`
- Consumes-Gate: 1, 2, 3, 4, 8, 17, 18, 19. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufBestellung.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/BestellungRevision.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/BestellungPosition.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/BestellungHerkunft.java`; `src/main/java/org/example/kalkulationsprogramm/repository/EinkaufBestellungRepository.java`, `src/main/java/org/example/kalkulationsprogramm/repository/BestellungRevisionRepository.java`; `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufBestellungService.java`; `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufBestellungDto.java`; `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufBestellungController.java`; `src/main/resources/db/migration/V392__einkauf_bestellungen.sql`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufBestellungServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/repository/EinkaufBestellungParallelTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/EinkaufBestellungControllerTest.java`.
- Vorbild: `BestellungService.java:38` bewusst nicht als Versandnachweis verwenden; immutable Snapshotmuster Task13.
- Interfaces – Produces: `Detail ausAngebot(AusAngebot r,Long akteurId)`; `Detail direkt(Direkt r,Long akteurId)`; `Detail aendern(Long id,Aenderung r,Long akteurId)`; `void verwerfen(Long id,long version,Long akteurId)`; `AusAngebot(Long angebotVersionId,List<Herkunft> paket,String entscheidungsgrund,UUID idempotenzKey)`; `Direkt(Long lieferantId,Snapshot empfaenger,List<Herkunft> paket,List<Direktpreis> preise,LocalDate liefertermin,LocalDate bestaetigungsfrist,String bedingungen,UUID idempotenzKey)`; `Direktpreis(Long bedarfId,BigDecimal preis,Einheit einheit,BigDecimal basisMenge,Long preisHistorieId,LocalDate bestaetigtAm,LocalDate gueltigBis,String bestaetigungsbeleg)`; `Aenderung(long version,Direkt inhalt,String grund)`; APIs GET/POST `/api/einkauf/bestellungen`, POST `/aus-angebot`, `/direkt`, `/{id}/verwerfen`, `/{id}/revisionen`; GET `/{id}`. Consumes1–4,8,17–19.
- Steps:
  - [ ] Tests Angebot wählen reserviert4/10, noch nicht bestellt; zwei parallele Entwürfe können zusammen10 nicht überschreiten; Direktbestellung B ohne PA/Angebot; fehlender aktueller Preis blockiert Freigabe, historischer Vorschlag behauptet keine Lieferzusage.
  - [ ] B-Nummer beim Entwurf aus Task2, ein Kopf je Lieferant/Versandvorgang; Anfrage-/Angebotsversion nullable. Unveränderliche Herkunftsanteile, technische Snapshotpositionen, vollständige Kosten-/Bedingungsfassung, Liefergruppen und Empfänger speichern. Reservierung im selben Mengenlock/DB-Commit wie Entwurf; Rücknahme gibt nur dessen Reservierung frei.
  - [ ] Erste UI-Funktion vergibt komplettes gewähltes Paket. Requestpaket muss geprüfter Angebotsbasis entsprechen; bei verändertem Paket Task18 neu rechnen und fehlende Fracht/Staffelbindung blockieren. Datenmodell erlaubt getrennte spätere Herkunftsanteile, kein Optimierer.
  - [ ] Fachzustand ENTWURF/BESTELLT/TEILGELIEFERT/GELIEFERT/STORNIERT, Lieferantstatus AUSSTEHEND/BESTAETIGT/ABWEICHUNG, Versand ausschließlich Outbox. Neue Revision nach Versand separat mit Deltareservierung; Originalpositionen nicht überschreiben. Tests grün.

## Abschnitt 12 (Tasks 22; disjunkte Dateien)

### Task 22: Bestellfreigabe, Annahme, Änderung und belegtes Storno

- Branch: `codex/beschaffung-task-22`
- Worktree: `.claude/worktrees/beschaffung-task-22`
- E2E-Port: `5203`
- Consumes-Gate: 12, 14, 15, 16, 18, 21, 38. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufBestellfreigabeService.java`, `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufBestellVersandListener.java`; `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufBestellfreigabeDto.java`; `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufBestellfreigabeController.java`; ändern `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufKommunikationService.java`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufBestellfreigabeServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/service/EinkaufBestellVersandListenerTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/EinkaufBestellfreigabeSecurityTest.java`.
- Vorbild: `AnfrageBestaetigungVersandService.java:162` für idempotente lokale Versandpersistenz; Outbox Task15 ist neuer verbindlicher Vertrag.
- Interfaces – Produces: `Vorschau vorschau(Long bestellungId,Long templateId)`; `VersandDto freigeben(Long id,Freigabe r,Long akteurId)`; `void versandAngenommen(EinkaufVersandAngenommen event)`; `Detail externGesendet(Long id,ExternerNachweis r,Long akteurId)`; `Detail stornoBestaetigen(Long id,Storno r,Long akteurId)`; `ExternerNachweis(long version,Instant versendetAm,Long dateiId,String begruendung,UUID idempotenzKey)`; `Storno(long version,List<Herkunft> anteile,Long belegDateiId,String grund,UUID idempotenzKey)`; APIs POST `/api/einkauf/bestellungen/{id}/vorschau|freigeben|extern-gesendet|storno-bestaetigen`. Consumes12,14–16,18,21.
- Steps:
  - [ ] Tests Download/Wahl/gescheiterter Versand kein BESTELLT, SMTP angenommen konvertiert genau einmal reserviert→bestellt, Timeout hält Reservierung, Bounce gibt nichts frei, Stammanpassung nach Versand verändert nichts, Freigabe durch falsche Rolle/fremde IDs 403/400.
  - [ ] Freigabe prüft aktuelle Version, Angebotgültigkeit am aktuellen Datum, technisch bestätigte Abweichungen, Preisvollständigkeit, Empfänger, Adresse, Mengen, Zeugnisse und Snapshot-/Anlagenhash. Abgelaufene Angebote erfordern neue belegte Bestätigung/versionierten Stand, kein Klickverlängern.
  - [ ] Revision einfrieren und Outbox in derselben DB-Transaktion; erst Commit, dann explizit asynchron Versand-ID dispatchen. Versandannahme in Ergebnis-Transaktion idempotent Mengen buchen und Fachzustand setzen; Lieferantbestätigung bleibt ausstehend.
  - [ ] Stornoanforderung als neue Kommunikationsfassung ohne Mengenfreigabe; bestätigtes Storno nur noch nicht gelieferte Anteile mit Beleg, Mengenhistory erhalten. Änderung nach Versand macht neue Fassung mit positiven Deltareservierungen; Reduktion erst mit bestätigter Änderung/Storno freigeben. Externversand dokumentiert Datum+Datei+berechtigten Akteur und denselben Übergang.
  - [ ] Alle Zustandskombinationen/Concurrencytests grün. Keine automatische Neuübermittlung unsicherer Bestellung.

## Abschnitt 13 (Tasks 24; disjunkte Dateien)

### Task 24: Teillieferungen, Chargen und abweichende Auftragsbestätigung

- Branch: `codex/beschaffung-task-24`
- Worktree: `.claude/worktrees/beschaffung-task-24`
- E2E-Port: `5205`
- Consumes-Gate: 1, 4, 21, 22. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufLieferung.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/LieferungPosition.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufCharge.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/BestellBestaetigung.java`; `src/main/java/org/example/kalkulationsprogramm/repository/EinkaufLieferungRepository.java`; `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufLieferungService.java`; `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufLieferungDto.java`; `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufLieferungController.java`; ändern `src/main/java/org/example/kalkulationsprogramm/domain/LieferantDokument.java`; `src/main/resources/db/migration/V393__einkauf_lieferungen_chargen.sql`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufLieferungServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/EinkaufLieferungControllerTest.java`.
- Vorbild: `LieferantDokumentService.java:349` Belegverknüpfung, `LieferantGeschaeftsdokument.java:53` Metadaten.
- Interfaces – Produces: `LieferungDto annehmen(Long bestellungId,Annahme r,Long akteurId)`; `BestaetigungDto bestaetigungErfassen(Long id,Bestaetigung r,Long akteurId)`; `Annahme(long version,Long lieferscheinId,Instant eingang,List<Lieferanteil> positionen,UUID idempotenzKey)`; `Lieferanteil(Long bestellPositionId,BigDecimal menge,String charge,String schmelznummer,List<Herkunft> projektAnteile)`; `Bestaetigung(Long dokumentId,LocalDate datum,LocalDate liefertermin,List<BestaetigtePosition> positionen)`; `BestaetigtePosition(Long bestellPositionId,BigDecimal menge,BigDecimal nettoPreis,String abweichung)`; API `/api/einkauf/bestellungen/{id}/lieferungen|bestaetigungen`. Consumes1,4,21,22.
- Steps:
  - [ ] Test Teillieferung2+2 auf4, erneuter identischer Eingang nur einmal; Überlieferung nicht still akzeptieren; zwei Chargen/Projektanteile; AB-Preisabweichung erzeugt Prüfhinweis und verändert Originalpreis nicht.
  - [ ] Liefermenge/Chargenbezug und physische Annahme persistieren; Bestellstatus daraus TEILGELIEFERT/GELIEFERT, Bedarf bleibt nicht erneut gedeckt. Dokumentprüfung/Freigabe separat und zunächst offen.
  - [ ] AB aus vorhandenem LieferantDokument verknüpfen, Lieferant/Positionen validieren; bestätigte Werte neben bestellten behalten. Abweichungstatus mit Quellen, menschliche Klärung nur über neuen Änderungsworkflow Task22.
  - [ ] Lieferschein-/Angebots-/Rechnungsbeleglinks bleiben vorhandene Belegketten; historische Belege ohne Bestellung weiterhin nutzbar. Tests grün.

## Abschnitt 14 (Tasks 25, 26; disjunkte Dateien)

### Task 25: Zeugnisanforderungen, spätere Eingänge und manuelle Prüfung

- Branch: `codex/beschaffung-task-25`
- Worktree: `.claude/worktrees/beschaffung-task-25`
- E2E-Port: `5206`
- Consumes-Gate: 1, 3, 6, 22, 24. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufZeugnisErwartung.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufZeugnisZuordnung.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufDokumentPruefung.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufAnforderungsVorlage.java`; `src/main/java/org/example/kalkulationsprogramm/repository/EinkaufZeugnisRepository.java`, `src/main/java/org/example/kalkulationsprogramm/repository/EinkaufAnforderungsVorlageRepository.java`; `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufZeugnisService.java`; `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufZeugnisDto.java`; `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufZeugnisController.java`; `src/main/resources/db/migration/V394__einkauf_zeugnisse.sql`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufZeugnisServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/EinkaufZeugnisSecurityTest.java`.
- Vorbild: `LieferantDokument`→EmailAttachment-Deduplizierung; Projekt-/Artikel-Sollanforderungen als versionierte explizite Grundlage, nicht alte EN1090-Pauschalregeln.
- Interfaces – Produces: `void erwarte(Long bestellungRevisionId,LocalDate frist)`; `ZuordnungDto zuordnen(Zuordnung r,Long akteurId)`; `PruefungDto pruefen(Long zuordnungId,Pruefung r,Long akteurId)`; `Zuordnung(Long dokumentId,List<Long> erwartungIds,List<Long> lieferPositionIds,List<Long> chargeIds,String schmelznummer)`; `Pruefung(long version,String ergebnis,String begruendung,String grundlageVersion)`; `List<DokumentSoll> vorschlagen(Long artikelId,Long projektId)`; APIs `/api/einkauf/zeugnisse`, `/{id}/zuordnen|pruefen`, `/api/einkauf/anforderungsvorlagen`. Consumes1,3,6,22,24.
- Steps:
  - [ ] Tests Zeugnis vor Lieferung, erst nach voller Lieferung, ein PDF für mehrere passende Positionen/Chargen, mehrere Zeugnisse je Position, falsche Charge/Lieferant, User ohne Prüfberechtigung, unbekannte Klassifikation braucht Prüfung.
  - [ ] Erwartung nach Bestellannahme aus Snapshot erzeugen, Frist explizit hinterlegen; States ANGEFORDERT/ERWARTET/EINGEGANGEN/ZUGEORDNET/GEPRUEFT/KLAERUNG_NOETIG. Physischer PDF-Eingang niemals geprüft. Sollarten2.1/2.2/3.1/3.2/Leistungserklärung/CE getrennt und mehrfach möglich.
  - [ ] Many-to-many-Zuordnungen über existierenden Dokument-/Dateireferenzdatensatz, keine Dateikopie; Prüfung append-only mit Akteur/Zeit/Grundlage, widersprechende Charge blockiert automatische Zuordnung. Materialfreigabe wird ausdrücklich separat gespeichert; nur berechtigte positive Prüfung aller expliziten Anforderungen erlaubt Freigabe.
  - [ ] Zentraler Katalog für manuell fachlich bestätigte Artikel-/Projekt-Vorgaben mit Versionsnummer/Grundlagentext; Änderungen gelten nur für neue Snapshots. Unbekannter EXC/Werkstoff erzeugt Hinweis, keine automatische normative Freigabe. Gezielte Tests grün.

### Task 26: Positionsbezogener Rechnungsabgleich und bestehende Reklamationen

- Branch: `codex/beschaffung-task-26`
- Worktree: `.claude/worktrees/beschaffung-task-26`
- E2E-Port: `5207`
- Consumes-Gate: 17, 18, 20, 21, 24. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufBelegPosition.java`, `src/main/java/org/example/kalkulationsprogramm/domain/einkauf/EinkaufBelegZuordnung.java`; `src/main/java/org/example/kalkulationsprogramm/repository/EinkaufBelegPositionRepository.java`; `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufRechnungsabgleichService.java`; `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufRechnungsabgleichDto.java`; `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufRechnungsabgleichController.java`; ändern `src/main/java/org/example/kalkulationsprogramm/domain/LieferantReklamation.java`, `src/main/java/org/example/kalkulationsprogramm/dto/CreateReklamationRequest.java`, `src/main/java/org/example/kalkulationsprogramm/dto/LieferantReklamationDto.java`, `src/main/java/org/example/kalkulationsprogramm/controller/LieferantReklamationController.java`, `src/main/java/org/example/kalkulationsprogramm/controller/BestellungsUebersichtController.java`; `src/main/resources/db/migration/V395__einkauf_rechnungsabgleich.sql`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufRechnungsabgleichServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/LieferantReklamationControllerTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/BestellungsUebersichtControllerTest.java`.
- Vorbild: `BestellungsUebersichtController.java:463` zentrale tatsächliche Kostenzuordnung, `LieferantReklamationController.java:74`, `LieferantGeschaeftsdokument.java:33`.
- Interfaces – Produces: `Abgleich vergleichen(Long bestellungId)`; `Abgleich zuordnen(Long dokumentId,BelegZuordnung r,Long akteurId)`; `BelegZuordnung(long version,String art,Long bezugsDokumentId,List<BelegPosition> positionen,UUID idempotenzKey)`; `BelegPosition(String originalPositionsnummer,Long bestellPositionId,BigDecimal menge,Einheit einheit,List<Kosten> kosten,List<Quelle> quellen)`; `Abweichung(Long positionId,String feld,BigDecimal vereinbart,BigDecimal abgerechnet,BigDecimal differenz,String rechenweg,List<Quelle> quellen)`; GET `/api/einkauf/bestellungen/{id}/rechnungsabgleich`, POST `/api/einkauf/belege/{dokumentId}/zuordnung`. ReklamationRequest/Dto zusätzlich `bestellungId,bestellPositionId,rechnungId`. Consumes17,18,21,24.
- Steps:
  - [ ] Rote Tests Teilrechnung2von4 gegen exakt2, spätere zweite Teilrechnung, Gutschrift/Storno/Nachberechnung mit Originalbezug, einmalige Fracht nicht pro Teilrechnung neu, unbekannte Einheit/Zuordnung offen, begründete80€-Abweichung, doppelte Projektkosten verhindern.
  - [ ] Belegpositionsdaten persistent ergänzen (vorhandene Metadaten sind nur Kopfwerte), Herkunft aus LieferantDokument/EmailAttachment; manuelle Zuordnung und Originalquellen. Normalisierte bestellte/bestätigte/gelieferte/kumuliert abgerechnete Mengen nebeneinander, vorzeichenrichtige Belegkorrekturen; nicht den Gesamtbestellwert gegen jede Teilrechnung halten.
  - [ ] Shared Task18-Umrechnung/Kostenberechnung; Kostenanteile explizit zuordnen, pauschale Fracht mit verbleibendem vereinbartem Anteil. Unsichere Zuordnung/Einheit ergibt PRUEFEN statt akzeptierter Differenz0.
  - [ ] Keine Buchung beim Vergleich oder Preisübernahme. Bestehenden `/api/bestellungen-uebersicht/zuordnen`-Pfad weiter benutzen, derselbe Dokument-/Belegbezug darf keine zweite Kostenquelle erzeugen. Historische Belegketten ohne B-Referenz anzeigen, nicht umetikettieren.
  - [ ] Reklamation um optionale Bestell-/Positions-/Rechnungs-FKs erweitern, alle gehören zu gleichem Lieferanten/Bestellkopf; Status/Bilder bestehend. Controller extrahiert neue Validierung in Service statt zweiten Reklamationsworkflow. Backendtests grün.

## Abschnitt 15 (Tasks 23; disjunkte Dateien)

### Task 23: Arbeitsliste „Das ist fällig“ und vorbereitete Nachfragen

- Branch: `codex/beschaffung-task-23`
- Worktree: `.claude/worktrees/beschaffung-task-23`
- E2E-Port: `5204`
- Consumes-Gate: 12, 13, 16, 21, 25. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufFaelligkeitService.java`, `src/main/java/org/example/kalkulationsprogramm/dto/Einkauf/EinkaufFaelligkeitDto.java`, `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufFaelligkeitController.java`; Tests `src/test/java/org/example/kalkulationsprogramm/service/EinkaufFaelligkeitServiceTest.java`, `src/test/java/org/example/kalkulationsprogramm/controller/EinkaufFaelligkeitControllerTest.java`.
- Vorbild: `BestellungenUebersicht.tsx:169` Terminanzeige, Task12-Vorlagen; keine automatische Mahnversandfunktion verwenden.
- Interfaces – Produces: `Page<Faelligkeit> liste(LocalDate heute,Long zustaendigId,Pageable p)`; `NachfrageEntwurf nachfrage(String typ,Long vorgangId,Long beteiligungId)`; `Faelligkeit(String typ,Long vorgangId,String nummer,Long beteiligungId,LocalDate frist,Long zustaendigId,String hinweis)`; GET `/api/einkauf/faelligkeiten`, POST `/api/einkauf/faelligkeiten/nachfrage`. Consumes12,13,16,21,25 (Zeugnisquery); keine Änderungen an deren Dateien.
- Steps:
  - [ ] Feste Clock-Tests für abgelaufene Antwortfrist, fehlende Auftragsbestätigung, bestätigten Liefertermin überschritten, Zeugnisfrist und null-Frist „Termin klären“; Absagen/erledigte/stornierte Vorgänge fehlen.
  - [ ] Abfragen serverseitig paginieren und Zuständigkeit mitliefern; ein verknüpftes Ergebnis pro fälliger Ursache, kein N+1. Lieferdatum ohne Bestätigung nicht als bestätigten Termin ausgeben.
  - [ ] Nachfragen erzeugen nur Vorlagen-/Empfängervorschau im bestehenden Kommunikationsworkflow; Tests verifizieren `verifyNoInteractions(transport)` bis ausdrückliche Freigabe. Zeugnisnachfrage benennt fehlende Sollarten/Chargen. API-/Clocktests grün.

## Abschnitt 16 (Tasks 27; disjunkte Dateien)

### Task 27: Gemeinsame Desktop-Einkaufsbausteine und typisierte API

- Branch: `codex/beschaffung-task-27`
- Worktree: `.claude/worktrees/beschaffung-task-27`
- E2E-Port: `5208`
- Consumes-Gate: 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `react-pc-frontend/src/features/einkauf/types.ts`, `react-pc-frontend/src/features/einkauf/api.ts`, `react-pc-frontend/src/features/einkauf/positionDrafts.ts`, `react-pc-frontend/src/features/einkauf/components/PositionsEditor.tsx`, `react-pc-frontend/src/features/einkauf/components/Mengenstand.tsx`, `react-pc-frontend/src/features/einkauf/components/QuellenLink.tsx`, `react-pc-frontend/src/features/einkauf/components/VersandStatus.tsx`, `react-pc-frontend/src/features/einkauf/components/EinkaufNavigation.tsx`; Tests siehe explizite Testdateien.
- Explizite Testdateien: `react-pc-frontend/src/features/einkauf/positionDrafts.test.ts`, `react-pc-frontend/src/features/einkauf/components/PositionsEditor.test.tsx`, `react-pc-frontend/src/features/einkauf/components/Mengenstand.test.tsx`, `react-pc-frontend/src/features/einkauf/components/QuellenLink.test.tsx`, `react-pc-frontend/src/features/einkauf/components/VersandStatus.test.tsx`, `react-pc-frontend/src/features/einkauf/components/EinkaufNavigation.test.tsx`, `react-pc-frontend/src/features/einkauf/api.test.ts`.
- Vorbild: `lib/numberDrafts.ts:4`, `components/artikel/ArtikelSuche.tsx:151`, `components/DetailLayout.tsx:10`, gemeinsamer `Dialog`, `useToast/useConfirm`.
- Interfaces – Produces: TS-Modelle entsprechend obigen DTOs; `einkaufApi.get<T>(path:string):Promise<T>`, `post<T>(path:string,body:unknown):Promise<T>` über den bestehenden globalen Fetch-CSRF-Interceptor `react-pc-frontend/src/main.tsx:9`; `PositionsEditor({value:PositionDraft,onChange:(v:PositionDraft)=>void,readOnly?:boolean})`; `toPositionPayload(draft:PositionDraft):ValidationResult<PositionSnapshot>`; `Mengenstand({stand})`, `QuellenLink({quelle})`, `VersandStatus({versand})`. Consumes Contracts3–26, keine unfertigen Komponenten importieren.
- Steps:
  - [ ] Componenttests Komma/Nullfokus/leer/unvollständig/negative Zahl, eine Schnittform/zwei Winkel, mehrere Dokumentarten, Quellenlink und Versand-unklar ohne Retryknopf.
  - [ ] Stringdrafts für Mengen/Preise, IDs/Nummern Strings nach fachlichem Vertrag; `validateNumberDrafts` zentral anwenden. Positioneditor enthält Katalog/Zeichnungsteil, kurze bestehende Artikelanlage, Werkstoff/Profil/Bearbeitung/Oberfläche/Sollunterlagen und revisionierte Anlagen. ArtikelSuche wiederverwenden, keinerlei Preis0-Fallback.
  - [ ] API errors {message,fieldErrors} anzeigen; 409 hält Nutzerentwurf und bietet Neuladen/Abgleich, kein blindes Retry. Quellen öffnen safe URLs/Dokumentviewer, HTML sanitisiert. Accessibility/Fokus/Tooltips und Unit-/Lintchecks grün.

## Abschnitt 17 (Tasks 29, 30; disjunkte Dateien)

### Task 29: Einkaufs-Mailkonto und Berechtigungen in Einstellungen

- Branch: `codex/beschaffung-task-29`
- Worktree: `.claude/worktrees/beschaffung-task-29`
- E2E-Port: `5210`
- Consumes-Gate: 1, 9, 10, 27. Vor Start geprüft und in den Featurebranch integriert.
- Files: ändern `react-pc-frontend/src/components/settings/sections/EmailSettingsSection.tsx`, `react-pc-frontend/src/pages/BenutzerEditor.tsx`; neu `react-pc-frontend/src/components/settings/MailkontoFields.tsx`, `react-pc-frontend/src/components/settings/EinkaufBerechtigungen.tsx`; Tests siehe explizite Testdateien; neu `react-pc-frontend/e2e/einkauf-einstellungen.spec.ts`.
- Explizite Testdateien: `react-pc-frontend/src/components/settings/MailkontoFields.test.tsx`, `react-pc-frontend/src/components/settings/EinkaufBerechtigungen.test.tsx`, `react-pc-frontend/src/pages/BenutzerEditor.einkauf.test.tsx`.
- Vorbild: `EmailSettingsSection.tsx:59,252,581`, bestehende `PasswordField`, `TestResultBanner`, `SaveButton`.
- Interfaces – Produces: `MailkontoFields({value:MailkontoDraft,onChange,passwordState,disabled})` gemeinsam für Dokument- und Einkaufskarte; `EinkaufBerechtigungen({profileId:number})`. Consumes1,9,27.
- Steps:
  - [ ] Tests SMTP+IMAP getrennt, Passwort unverändert bei leerem Edit, Response enthält nur passwordSet, Verbindungstest sendet nichts, Testmail verlangt Empfänger, Nichtadmin bekommt keine editierbaren Zugänge.
  - [ ] Wiederkehrende Server-/Port-/TLS-/Absender-/Passwortfelder aus bestehendem Dokumentformular extrahieren; Einkaufs-Postfach-Karte mit Aktivierung, Inbox/Sent, letzterAbruf/Fehler. Haupt-/Dokumentkontoverträge bleiben, gemeinsame Felder verhindern dritte Formular-Kopie.
  - [ ] Getrennte Aktionen „Verbindung prüfen“ und „Testmail senden“; keine automatische Testmail bei Speichern. `EinkaufBerechtigungen` in `BenutzerEditor.tsx:343` unter dem Bearbeitungsformular mounten mit `profileId={selectedUser.id}` sobald gespeichertes Profil gewählt ist, bei Neuanlage erst nach erfolgreichem Save. Vorbild `BenutzerEditor.tsx:159,166` verwendet bereits selectedUser und `/api/frontend-users`. Rechtekarte lädt Grants des konkret gewählten Profils; Sende-/Bestell-/Prüfrechte einzeln bearbeitbar, ADMIN-Zugang bleibt erforderlich.
  - [ ] E2E konfiguriert Dummyserver, prüft Fehler/Status und Maskierung; drei Größen, Designprüfung, alle Componenttests grün.

### Task 30: Lieferantenkontakte und Vorlageneditor für Einkauf

- Branch: `codex/beschaffung-task-30`
- Worktree: `.claude/worktrees/beschaffung-task-30`
- E2E-Port: `5211`
- Consumes-Gate: 8, 12, 27. Vor Start geprüft und in den Featurebranch integriert.
- Files: ändern `react-pc-frontend/src/pages/LieferantenEditor.tsx`, `react-pc-frontend/src/pages/EmailTextvorlagenEditor.tsx`; neu `react-pc-frontend/src/features/einkauf/components/LieferantKontakte.tsx`; Tests bestehende `react-pc-frontend/src/pages/LieferantenEditor.test.tsx`, neu `react-pc-frontend/src/features/einkauf/components/LieferantKontakte.test.tsx`, `react-pc-frontend/src/pages/EmailTextvorlagenEditor.einkauf.test.tsx`; E2E `react-pc-frontend/e2e/einkauf-kontakte-vorlagen.spec.ts`.
- Vorbild: `LieferantenEditor.tsx` Stammdaten-/Bearbeitenlayout, `EmailTextvorlagenEditor.tsx:631,646,792` Metadaten/Gruppierung.
- Interfaces – Produces: `LieferantKontakte({lieferantId:number,readOnly:boolean})`; Editor nutzt `placeholders?dokumentTyp=` und Standard-/Variantenmetadata. Consumes8,12,27.
- Steps:
  - [ ] Tests Standardanfrage/Bestelladresse unabhängig von Rechnungsabsender, neutrale Anrede, eigeneKundennummer mit führenden Nullen; mehrere Varianten pro Einkaufstyp, Direktvorlage ohne PA, falscher Token blockiert Versandvorschau.
  - [ ] Kontaktbereich im bestehenden Lieferanteneditor integrieren, Adressen sichtbar prüfen/ändern; keine automatische Personenextraktion aus Vertretertext.
  - [ ] Einkauf als vorhandene Kategorie; Variantenauswahl/Defaultwechsel in vorhandenem Editor, kontextsensitive Tokens und echte Backendvorschau. Ein Verkaufs-ANFRAGENUMMER-Label darf Einkäufer nicht auf Website verweisen. Bestehende Verkaufs-/Mahnvorlagen unverändert nutzbar.
  - [ ] E2E bearbeitet Vorlage, erzeugt Vorschau, prüft erhaltenen Alttext in gesendeter Fassung nach weiterer Vorlagenänderung (Stubvertrag und Backendintegration Task37). Component/E2E/Design grün.

## Abschnitt 18 (Tasks 31; disjunkte Dateien)

### Task 31: Anfrageliste und Detail mit Sendefreigabe/Verlauf

- Branch: `codex/beschaffung-task-31`
- Worktree: `.claude/worktrees/beschaffung-task-31`
- E2E-Port: `5212`
- Consumes-Gate: 12, 13, 14, 15, 16, 27, 30. Vor Start geprüft und in den Featurebranch integriert.
- Files: ändern `react-pc-frontend/src/App.tsx`; neu `react-pc-frontend/src/pages/Einkaufsanfragen.tsx`, `react-pc-frontend/src/pages/EinkaufsanfrageDetail.tsx`; `react-pc-frontend/src/features/einkauf/components/AnfrageLieferanten.tsx`, `react-pc-frontend/src/features/einkauf/components/VersandVorschauDialog.tsx`, `react-pc-frontend/src/features/einkauf/components/KommunikationsVerlauf.tsx`; Tests siehe explizite Testdateien; E2E `react-pc-frontend/e2e/einkauf-anfragen.spec.ts`.
- Explizite Testdateien: `react-pc-frontend/src/pages/Einkaufsanfragen.test.tsx`, `react-pc-frontend/src/pages/EinkaufsanfrageDetail.test.tsx`, `react-pc-frontend/src/features/einkauf/components/AnfrageLieferanten.test.tsx`, `react-pc-frontend/src/features/einkauf/components/VersandVorschauDialog.test.tsx`, `react-pc-frontend/src/features/einkauf/components/KommunikationsVerlauf.test.tsx`.
- Vorbild: `DetailLayout.tsx:10`, `EmailCenter.tsx:97`, bestehender Dokumentpreview; Bestandsgroßdialog aus BestellungEditor nicht kopieren.
- Interfaces – Produces: Routen `/einkaufsanfragen`, `/einkaufsanfragen/:id`; `VersandVorschauDialog({vorschau,onFreigeben:(hash:string)=>Promise<void>,onSchliessen})`; `KommunikationsVerlauf({typ:'ANFRAGE'|'BESTELLUNG',vorgangId:number})`. Consumes12–16,27,30.
- Steps:
  - [ ] Tests drei Lieferanten, gleiche Revision/interne Nummer, jeder eigener Empfänger/Code; Erfolg1/Fehler1/unklar1 getrennt; neue Revision nach Versand, alte Antworten sichtbar und nicht still gültig; kein Bestellstatus durch Anfrage.
  - [ ] Lauffähige Seiten sofort in App.tsx unter `/einkaufsanfragen` und `/einkaufsanfragen/:id` registrieren, damit eigene E2E direkt navigieren können. Liste mit Frist/Projekt/Antwortzähler; Detail aus DetailLayout mit Positionen, Lieferanten & Antworten, Vergleichverweis, Verlauf. Empfänger, unsereKundennummer, Anlagenrevisionen und Fristen in Freigabe sichtbar. Noch lieferantenneutralen Entwurf aus Task28 hier mit Kontaktwahl/Fristen/Positioneditor vervollständigen und per versioniertem RevisionRequest speichern, erst dann Versandvorschau zulassen.
  - [ ] Vorschau aus Server laden, hashgebunden freigeben, Polling nur aktiver Jobs; erneuter gezielter Versand nur sicher fehlgeschlagene Beteiligung. Änderungen invalidieren Vorschau. Historische Revision read-only und direkt wählbar.
  - [ ] Manuelle Zuordnungsprüfung für Mail/PDF-Vorschlag mit Fundstelle und Lieferant/Absender sichtbar; unbekannte Mail niemals per UI automatisch bestätigen. E2E prüft kompletten Dialog und Reload; Tests/Design grün.

## Abschnitt 19 (Tasks 33, 35; disjunkte Dateien)

### Task 33: Echte Bestellungen und Direktbestellung im bestehenden Einkaufsbereich

- Branch: `codex/beschaffung-task-33`
- Worktree: `.claude/worktrees/beschaffung-task-33`
- E2E-Port: `5214`
- Consumes-Gate: 19, 21, 22, 27, 31. Vor Start geprüft und in den Featurebranch integriert.
- Files: ändern `react-pc-frontend/src/App.tsx`; ändern `react-pc-frontend/src/pages/BestellungenUebersicht.tsx`; neu `react-pc-frontend/src/pages/EinkaufBestellungDetail.tsx`, `react-pc-frontend/src/features/einkauf/components/DirektbestellungDialog.tsx`, `react-pc-frontend/src/features/einkauf/components/BestellfreigabeDialog.tsx`, `react-pc-frontend/src/features/einkauf/components/StornoKlaerungDialog.tsx`; Tests siehe explizite Testdateien; E2E `react-pc-frontend/e2e/einkauf-bestellungen.spec.ts`.
- Explizite Testdateien: `react-pc-frontend/src/pages/EinkaufBestellungDetail.test.tsx`, `react-pc-frontend/src/features/einkauf/components/DirektbestellungDialog.test.tsx`, `react-pc-frontend/src/features/einkauf/components/BestellfreigabeDialog.test.tsx`, `react-pc-frontend/src/features/einkauf/components/StornoKlaerungDialog.test.tsx`.
- Vorbild: `BestellungenUebersicht.tsx:23,121` historische Ketten, DetailLayout und Task31-Vorschau.
- Interfaces – Produces: `/bestellungen/:id`, neue Bestellliste auf `/bestellungen`, bestehende Belegketten als eigener Bereich „Bisherige Belege“; `DirektbestellungDialog({anteile:Herkunft[],onCreated})`. Consumes19,21,22,27,31.
- Steps:
  - [ ] E2E Auswahl→Entwurf→PDF→Freigabe→SMTP angenommen, Mengenwechsel erst nach Annahme; Versandsicherheit/unklar blockiert blindes Retry; Direktbestellung ohne PA/Angebotsnummer; Stornoanfrage ohne Freigabe, bestätigtes Storno mit Beleg.
  - [ ] In App.tsx Detailroute `/bestellungen/:id` erst mit dieser fertigen Seite registrieren. Header zeigt B-/optionalePA, Lieferant, fachlichen Zustand, Versandstatus, Bestätigung und Termin getrennt. Angezeigte Positionen aus Revision, nicht Artikelstamm. Ansprechpartner/Zeugnisse/Anlagen/Preise/Adresse bewusste Freigabeschritte.
  - [ ] Direktpreis mit Quelle/Datum vorschlagen und Gültigkeit bestätigen; ohne aktuellen Preis Richtung Anfrage führen. Änderung erzeugt neue Fassung, unklare Sendung Klärdialog, externer Versand verlangt Datum+Beleg.
  - [ ] Historische Belegketten-UI/Kostenzuordnung behalten, keine Rückinterpretation „B versendet“. Component-/E2E-/Designprüfung grün.

### Task 35: E-Mail-Center mit Postfachfilter und Einkaufsantworten

- Branch: `codex/beschaffung-task-35`
- Worktree: `.claude/worktrees/beschaffung-task-35`
- E2E-Port: `5216`
- Consumes-Gate: 11, 15, 16, 22, 27, 31. Vor Start geprüft und in den Featurebranch integriert.
- Files: ändern `react-pc-frontend/src/pages/EmailCenter.tsx`, `react-pc-frontend/src/features/email/emailCenterModel.ts`, `src/main/java/org/example/kalkulationsprogramm/controller/UnifiedEmailController.java`, `src/main/java/org/example/kalkulationsprogramm/dto/Email/EmailCenterItemDto.java`; neu `react-pc-frontend/src/features/einkauf/components/EmailEinkaufBezug.tsx`; ändern `src/main/java/org/example/kalkulationsprogramm/service/einkauf/EinkaufKommunikationService.java`, `src/main/java/org/example/kalkulationsprogramm/controller/EinkaufKommunikationController.java`; Tests `react-pc-frontend/src/pages/EmailCenter.test.tsx`, neu `react-pc-frontend/e2e/einkauf-email-center.spec.ts`, `src/test/java/org/example/kalkulationsprogramm/controller/UnifiedEmailControllerTest.java`.
- Vorbild: bestehendes EmailCenter-Modell und Threadcleanup, `UnifiedEmailController` Sendepfad, Task16-Kommunikation.
- Interfaces – Produces: optional `kontoId` im bestehenden Listfilter, DTO `kontoId,einkaufTyp,einkaufVorgangId,einkaufNummer,zuordnungPruefen`; `VersandDto antworten(Long emailId,Antwort r,Long akteurId)` mit `Antwort(String subject,String htmlBody,List<Long> anlageIds,String vorschauHash,UUID idempotenzKey)`; POST `/api/einkauf/mail/{id}/antwort-vorschau|antworten`. Consumes11,15,16,27,31.
- Steps:
  - [ ] Tests Haupt-/Dokument-/Einkaufspostfach getrennt, Einkaufsvorgang verlinkt, Antwort verwendet EINKAUF/Originalheader, fehlerhafte Konfiguration kein Hauptkonto-Fallback, falsche Rolle403; globaler Sendepfad darf Einkauf-ID nicht umgehen.
  - [ ] Liste/Threadfilter kontobewusst; Lieferant-Zuordnung und Einkaufslink gleichzeitig anzeigen. Einkaufsspezifische Leserechte auch beim Abruf über generischen Mailendpoint prüfen, Anhänge/Thread nicht per fremder Mail-ID offenlegen.
  - [ ] Bestehenden Editor weiterverwenden, Einkaufantworten ausschließlich hashgebunden über Task16/Outbox; generischer `/api/emails/send` prüft referenzierte Einkaufsmail und delegiert oder weist unberechtigten Direktversand ab. Kontowechsel ist keine versteckte Alternative bei Fehlern.
  - [ ] Zuordnung prüfen/autoAntwort/Absage/Angebot/Bounce sichtbar; manuelles Abrufen nur gewähltes Konto. EmailCenter-Regression/Component/E2E/Design grün.

## Abschnitt 20 (Tasks 28, 32, 34; disjunkte Dateien)

### Task 28: Bedarfsseite mit Teilmengen, Entnahme und HiCAD

- Branch: `codex/beschaffung-task-28`
- Worktree: `.claude/worktrees/beschaffung-task-28`
- E2E-Port: `5209`
- Consumes-Gate: 4, 5, 6, 7, 13, 21, 27, 31, 33. Vor Start geprüft und in den Featurebranch integriert.
- Files: ändern `react-pc-frontend/src/pages/BestellungEditor.tsx`; neu `react-pc-frontend/src/features/einkauf/components/BedarfDialog.tsx`, `react-pc-frontend/src/features/einkauf/components/LagerentnahmeDialog.tsx`, `react-pc-frontend/src/features/einkauf/components/HiCadImportDialog.tsx`, `react-pc-frontend/src/features/einkauf/components/AnlagenEditor.tsx`; Tests siehe explizite Testdateien; neu `react-pc-frontend/e2e/einkauf-bedarf.spec.ts`, `react-pc-frontend/e2e/einkauf-hicad-lager.spec.ts`.
- Explizite Testdateien: `react-pc-frontend/src/features/einkauf/components/BedarfDialog.test.tsx`, `react-pc-frontend/src/features/einkauf/components/LagerentnahmeDialog.test.tsx`, `react-pc-frontend/src/features/einkauf/components/HiCadImportDialog.test.tsx`, `react-pc-frontend/src/features/einkauf/components/AnlagenEditor.test.tsx`.
- Vorbild: `BestellungEditor.tsx:875` alte Bedarfsliste, `ArtikelAuswahlDialog.tsx:75`, `lieferant-dokument-modal.spec.ts:1`.
- Interfaces – Produces: bestehende Route `/bestellungen/bedarf` mit `onAnfrageVorbereiten(anteile:Herkunft[])` und `onDirektBestellen(anteile:Herkunft[])` Navigation über persistenten Create-Request; Consumes4–7,27,13/21-Create-APIs sowie lauffähige Routen/Dialogs aus31 und33; Tasknummer ist keine Ausführungsreihenfolge.
- Steps:
  - [ ] Rote UI-/E2E-Fälle Bedarf10, Teilmenge4, Seitenreload erhält Daten, Entnahmedruck ändert0, bestätigte Entnahme3 zeigt offen7, konkurrierende Reservierung409, HiCAD Teilübernahme/erneuter Import, Zeichnungsteil mit fehlender Anlage.
  - [ ] Alte lieferantengruppierte Flagliste durch lieferantenneutrale persistente Bedarfsliste ersetzen; interne Nummer, Projekt/Lager, Soll/offen/reserviert/bestellt/angefragt getrennt, Filter/Pagination. Altdaten-Nachpflege klar verlinken; kein localStorage als Bedarfsdatenquelle.
  - [ ] Dialoge für Erfassung/Teilmenge/Entnahmebewertung und importierte Zeilen; Auswahl validiert vor erster API-Mutation. „Angebote einholen“ als Primäraktion, Direktbestellung/Lagerentnahme sekundär. HiCAD Vorschau mit Mapping/Kandidaten/Bildfreigabe, nur bestätigte Zeilen übernehmen.
  - [ ] E2E prüft reale Bedienfolge einschließlich geöffneter Picker, Dateigrenze, Keyboard und Reload; alle3Desktopgrößen + Designprüfung. Komponententests/Lint grün.

### Task 32: Angebotsvergleich, manuelle Erfassung, KI-Prüfung und Preisübernahme

- Branch: `codex/beschaffung-task-32`
- Worktree: `.claude/worktrees/beschaffung-task-32`
- E2E-Port: `5213`
- Consumes-Gate: 17, 18, 19, 20, 21, 27, 31, 33. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `react-pc-frontend/src/features/einkauf/components/Angebotsvergleich.tsx`, `react-pc-frontend/src/features/einkauf/components/AngebotEditor.tsx`, `react-pc-frontend/src/features/einkauf/components/KiVorschlaege.tsx`, `react-pc-frontend/src/features/einkauf/components/PreisUebernahmeDialog.tsx`; ändern `react-pc-frontend/src/pages/EinkaufsanfrageDetail.tsx`; Tests siehe explizite Testdateien; E2E `react-pc-frontend/e2e/einkauf-angebote.spec.ts`.
- Explizite Testdateien: `react-pc-frontend/src/features/einkauf/components/Angebotsvergleich.test.tsx`, `react-pc-frontend/src/features/einkauf/components/AngebotEditor.test.tsx`, `react-pc-frontend/src/features/einkauf/components/KiVorschlaege.test.tsx`, `react-pc-frontend/src/features/einkauf/components/PreisUebernahmeDialog.test.tsx`.
- Vorbild: Preis-/Zahlenprüfung Task27, bestehende Artikelpreis-Historie, Quellenviewer Task27.
- Interfaces – Produces: `Angebotsvergleich({anfrageId:number,onBestellung:(angebotVersionId:number)=>void})`, `AngebotEditor({beteiligungId,angebotVersionId?,onSaved})`, `KiVorschlaege({jobId,onUebernommen})`. Consumes17–20,27,31.
- Steps:
  - [ ] E2E A1060/B1080/Coffen, verschiedene Preisbasen, enthaltene Legierung, abgelaufene Gültigkeit, technische Abweichung, Zeugnis nicht lieferbar, Skonto nur Hinweis; keine grüne Empfehlung für C.
  - [ ] Matrixpositionen mit internen Nummern/Revisionen, Liefergruppen und Gesamtkosten; horizontal nur Matrix scrollbar, Header/Primäraktion erreichbar. Rechenweg mit Originalpreis/Faktor/Quelle aufklappbar; fehlend als „offen“, niemals0€.
  - [ ] Manuell erfassen/korrigieren auch bei KI-Fehler; Quellenfelder einzeln bestätigen, geänderte Werte erkennbar, Neu-Analyse überschreibt Korrekturen nicht. Preisübernahme ist eigene bewusste Aktion mit Scope/Gültigkeit, keine Bestellung oder Kostenbuchung.
  - [ ] „Als Bestellung vorbereiten“ nennt konkrete Angebotsversion und erzeugt nur Entwurf; abgelaufener Preis zeigt erforderliche Bestätigung. Vitest/E2E/Design grün.

### Task 34: Lieferung, Unterlagen und Rechnungsprüfung im Desktop

- Branch: `codex/beschaffung-task-34`
- Worktree: `.claude/worktrees/beschaffung-task-34`
- E2E-Port: `5215`
- Consumes-Gate: 24, 25, 26, 27, 33. Vor Start geprüft und in den Featurebranch integriert.
- Files: ändern `react-pc-frontend/src/App.tsx`; neu `react-pc-frontend/src/pages/EinkaufLieferungen.tsx`, `react-pc-frontend/src/features/einkauf/components/LieferungDialog.tsx`, `react-pc-frontend/src/features/einkauf/components/ZeugnisZuordnung.tsx`, `react-pc-frontend/src/features/einkauf/components/Rechnungsabgleich.tsx`, `react-pc-frontend/src/features/einkauf/components/AnforderungsVorlagen.tsx`; ändern `react-pc-frontend/src/pages/EinkaufBestellungDetail.tsx`, `react-pc-frontend/src/components/LieferantReklamationenTab.tsx`; Tests siehe explizite Testdateien; E2E `react-pc-frontend/e2e/einkauf-lieferung-zeugnis.spec.ts`, `react-pc-frontend/e2e/einkauf-rechnungsabgleich.spec.ts`.
- Explizite Testdateien: `react-pc-frontend/src/pages/EinkaufLieferungen.test.tsx`, `react-pc-frontend/src/features/einkauf/components/LieferungDialog.test.tsx`, `react-pc-frontend/src/features/einkauf/components/ZeugnisZuordnung.test.tsx`, `react-pc-frontend/src/features/einkauf/components/Rechnungsabgleich.test.tsx`, `react-pc-frontend/src/features/einkauf/components/AnforderungsVorlagen.test.tsx`.
- Vorbild: `LieferantReklamationenTab.tsx`, vorhandener LieferantDokumentModal, Task27 Quellenlink und DetailLayout.
- Interfaces – Produces: Route `/einkauf/lieferungen`; `Rechnungsabgleich({bestellungId:number})`; `ZeugnisZuordnung({bestellungId:number})`. Consumes24–27,33.
- Steps:
  - [ ] E2E bestellt4/geliefert2+2 in zwei Chargen, Zeugnis fehlt trotz voller Lieferung; späteres PDF mehreren passenden Lieferpositionen zuordnen, separat prüfen; fremde Charge ablehnen, Nutzer ohne Prüfberechtigung kann nicht freigeben.
  - [ ] In App.tsx fertige Route `/einkauf/lieferungen` registrieren. Physische Menge, Dokumentvollständigkeit und Materialfreigabe getrennt darstellen. Sollvorlagen mit Grundlage/Version manuell pflegen; keine automatisch berechnete EN1090-Erfüllung behaupten.
  - [ ] Rechnungsteil2von4 und weitere Rechnung/Gutschrift erfassen/verknüpfen, Rechenweg vereinbart/bestätigt/geliefert/abgerechnet zeigen; Abweichung öffnet bestehenden Reklamationsdialog mit Bestell-/Positions-/Rechnungsreferenz. Kostenübernahme verlinkt bestehenden Zuordnungspfad.
  - [ ] Fehlende historische Datei meldet fehlend, neue Dummydatei uploadbar; nicht reparieren durch erfundene Datei. Tests/Design grün.

## Abschnitt 21 (Tasks 36; disjunkte Dateien)

### Task 36: Navigation und Fälligkeits-Arbeitsliste

- Branch: `codex/beschaffung-task-36`
- Worktree: `.claude/worktrees/beschaffung-task-36`
- E2E-Port: `5217`
- Consumes-Gate: 23, 27, 28, 29, 30, 31, 32, 33, 34, 35. Vor Start geprüft und in den Featurebranch integriert.
- Files: ändern `react-pc-frontend/src/App.tsx`, `react-pc-frontend/src/components/layout/RibbonNav.tsx`; neu `react-pc-frontend/src/pages/EinkaufFaelligkeiten.tsx`; Tests `react-pc-frontend/src/pages/EinkaufFaelligkeiten.test.tsx`, `react-pc-frontend/e2e/einkauf-navigation-fristen.spec.ts`.
- Vorbild: `App.tsx:83`, `RibbonNav.tsx:95`; Pageheader/DetailLayout-Regeln.
- Interfaces – Produces: finale Menüintegration der bereits in31/33/34 registrierten Routen und neue Route `/einkauf/faelligkeiten`; Consumes23,27–35 (Komponenten müssen vor Route verfügbar sein).
- Steps:
  - [ ] Tests Antwort/AB/Liefertermin/Zeugnisfälligkeit, terminlos „Termin klären“, Zuständigkeitsfilter, erledigte Vorgänge fehlen; Klick bereitet Mail vor, sendet ohne bestätigende Aktion nichts.
  - [ ] Einkauf-Navigation Bedarf/Anfragen/Bestellungen/Lieferungen & Zeugnisse/Das ist fällig, alte Direktlinks erhalten. Rechteblendung entsprechend Serverrechte, kein Frontend-only-Schutz.
  - [ ] Arbeitsliste mit konkretem nächsten Schritt/Frist/Nummer/Verantwortung; Vorlagennachfrage öffnet vorhandene Vorschau, nach Versand unveränderlicher Verlauf. Keine automatische Nachfassmail. Route-/E2E-/Designchecks grün.

## Abschnitt 22 (Tasks 37; disjunkte Dateien)

### Task 37: Vollständige integrierte Abnahme mit echter DB und Testmailserver

- Branch: `codex/beschaffung-task-37`
- Worktree: `.claude/worktrees/beschaffung-task-37`
- E2E-Port: `5218`
- Consumes-Gate: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 38. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `src/test/java/org/example/kalkulationsprogramm/integration/EinkaufWorkflowIntegrationTest.java`, `src/test/java/org/example/kalkulationsprogramm/integration/EinkaufMigrationIntegrationTest.java`, `src/test/java/org/example/kalkulationsprogramm/integration/EinkaufSmtpImapIntegrationTest.java`, `src/test/java/org/example/kalkulationsprogramm/integration/EinkaufSecurityIntegrationTest.java`; neu `react-pc-frontend/e2e-real/einkauf-real-workflow.spec.ts`, `react-pc-frontend/playwright.einkauf-real.config.ts`, `react-pc-frontend/e2e-real/hilfen/einkaufRealServer.ts`; ändern `pom.xml` und `react-pc-frontend/package.json` nur nötige Testscripts sowie `react-pc-frontend/vite.config.ts` für das lokale Proxyziel; `docs/testing/beschaffung-abnahme.md`; neu `src/test/resources/db/baseline-v376.sql`, `scripts/generate-test-baseline.sh`.
- Vorbild: vorhandene JUnit-/MockMvc-Tests, `e2e/lieferant-dokument-modal.spec.ts:1`; deren reine API-Stubs reichen ausdrücklich nicht für den Nachweis des echten Versandablaufs.
- Interfaces – Produces: reproduzierbarer `npm run test:e2e:einkauf-real`, startet dedizierte Dummy-MySQL/SMTP/IMAP/Backendinstanz. Eigener `testDir: ./e2e-real`, normale `e2e/`-Mocksuite sammelt diesen Test nicht ein; beide `npm run test:e2e` und `npm run test:e2e:einkauf-real` sind verpflichtende Gates. Consumes1–36,38. Tests niemals gegen lokale Produktionskopie aus Task39.
- Steps:
  - [ ] Vite-Proxy über `ERP_DEV_API_TARGET` konfigurierbar machen; bisheriger Default `https://localhost:8080` bleibt ohne Flag unverändert. Explizites Ziel strikt als lokale HTTP(S)-Origin validieren (127.0.0.1/localhost/[::1], gültiger Port, keine Credentials/Pfade/Query/Fragmente); fehlerhafte/externe Ziele beim Start ablehnen. Real-E2E setzt das Ziel auf den eigenen HTTP-Backendport; regulärer lokaler Start in Task39 verwendet dasselbe Flag.
  - [ ] Red/green-Suite startet isolierte Dummy-MySQL8 mit datenfreier Baseline (siehe nächster Schritt) sowie GreenMail2.1.14/lokalen FakeSMTP für gezielten DATA-Timeout; Backend mit Testprofil und Dummy-Login. KI kontrollierter Fake, echte REST-Persistenz statt `page.route` für Fach-APIs.
  - [ ] Migrationhistorie beginnt im vorhandenen Repo erst beiV208 und ist keine vollständige Leerschema-Baseline. Datenfreie Test-DDL aus dem unveränderten V376-Entitymodell des Ausgangscommits per Hibernate-Schemaexport generieren, als `src/test/resources/db/baseline-v376.sql` mit Herkunftshash speichern (nur Struktur, keine Produktionsdaten). Test-DB damit initialisieren, Flywaybaseline376 setzen und ausschließlich neue Migrationen377–395 anwenden, danach finales Hibernatevalidate. Task39 prüft zusätzlich echten Upgrade367→395. Keine aktuelle Final-Entity-DDL erzeugen und danach neue Migrationen versehentlich überspringen.
  - [ ] GreenMail2.1.14 mit Authentifizierung aktiviert, getrennten Konten und dynamischen localhost-Portmappings (internSMTP3025/IMAP3143/SMTPS3465/IMAPS3993/API8080); eigener Testtruststore, niemals TrustAll im Produkt. Alle Ressourcen nach Run nur anhand eigener Containerlabels aufräumen.
  - [ ] Browser legt Artikel/Zeichnungsteil/Bedarf10 an, fragt4 bei3 Dummy-Lieferanten an, empfängt geänderte Betreff-/Referenzantworten über IMAP, erfasst/vergleicht Angebote und bestätigt Quellen/Preis, bestellt4, prüft echte SMTP-Nachricht/PDF/Anlagen und DB-Mengen; neue Revision/Reload unverändert.
  - [ ] Zweiter Ablauf Direktbestellung, konkurrierende Lagerentnahme/Reservierung, unklarer DATA-Ausgang ohne zweiten Versand, Sent-APPEND-Fehler, erneuter IMAP-Import, deaktivierte KI, HiCAD-Teilimport, Teillieferungen/Zeugnisprüfung/Teilrechnung+Gutschrift+Reklamation/Fälligkeitsentwurf.
  - [ ] Parametrisierte API-Sicherheit für jede neue Mutation/Download: anonymous401, fehlendes Recht403, fremde verschachtelte IDs, CSRF, ungültige Nummern/Mengen/XSS/SQL-Literal/Upload. MySQL-Paralleltests und FlywayUpgrade der datenfreien V376-Dummystruktur, Hibernate validate. Keine Produktionsdatenfixtures.
  - [ ] Abnahmematrix unten mit Testnamen/Resultaten füllen. Vollständige verpflichtende Backend-/Frontend-/Lint-/E2E-/Builds laufen lassen, vorhandene17Backend-Skips gesondert als Bestand dokumentieren und keine neuen Skips einführen; wenn projektrelevanter Pflichtfall dadurch fehlt, testbar machen. Designreview alle3Größen. Erst grüne fachliche Ergebnisse erlauben den finalen Review/Commit.

## Abschnitt 23 (Tasks 39; disjunkte Dateien)

### Task 39: Lokale Kopie migrieren und vollständigen Probebetrieb übergeben

- Branch: `codex/beschaffung-task-39`
- Worktree: `.claude/worktrees/beschaffung-task-39`
- E2E-Port: `5220`
- Consumes-Gate: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38. Vor Start geprüft und in den Featurebranch integriert.
- Files: neu `scripts/beschaffung-local.sh`, `docs/betrieb/beschaffung-lokal.md`; ändern `.gitignore` nur für neue lokale PID-/Log-/Konfigurationspfade; lokales ignored `src/main/resources/application-local.properties` ausschließlich durch gezielte Konfiguration, niemals stagen. Tests `scripts/tests/beschaffung-local-test.sh` (Shell mit Stubkommandos); keine realen Daten in Testdateien.
- Vorbild: bereits erfolgreiches Restoreprotokoll außerhalb Git unter `/Users/marvinkuhn/Library/Application Support/Codex/erp-db-clone/2026-09-23-beschaffung/README.md`. Orchestrator hat Infrastruktur bereits vorbereitet; nicht erneut restaurieren.
- Interfaces – Produces: `scripts/beschaffung-local.sh start|stop|status`, dokumentierte URLs Backend `http://127.0.0.1:8080`, Desktop `http://127.0.0.1:5173` (bei belegten Ports bewusst andere lokale Ports wählen und dokumentieren). Consumes alle Produkt-Tasks und38.
- Steps:
  - [ ] Vorhandene isolierte Kopie prüfen: Container `erp-beschaffung-db`, MySQL8.0.44, Volume `erp-beschaffung-db-20260923`, nur127.0.0.1:3309, Datenbank `kalkulationsprogramm_db`, Appnutzer `erp_local`. Restore bereits nachgewiesen:139Tabellen/122821Zeilen, FlywayV367. Originale `erp-kalkulations-db`/`kalkulationsprogramm-mysql`, deren Volumes und Dump unangetastet lassen.
  - [ ] Lokale Credentialdateien im neuen Cloneordner `root.cnf`, `root-password`, `app-password` nur ohne Ausgabe lesen und an geschützte lokale Config übergeben; keine Shellargumente/Prozessausgabe mit Passwort. Dumpquelle `/Users/marvinkuhn/Library/Application Support/Codex/erp-db-clone/2026-09-09/kalkulationsprogramm_db.sql.gz` bleibt unverändert. Vor Migration Snapshot **nur der neuen Kopie** erzeugen; Bericht nur Counts/Versionen/Exitcodes, keine Zeileninhalte.
  - [ ] Vor allen Code-Migrationen den vorhandenen Bericht `/Users/marvinkuhn/Library/Application Support/Codex/erp-db-clone/2026-09-23-beschaffung/flyway-preflight.json` prüfen: 106 exakte CRC32-Treffer, 15 fehlende/anders benannte historische Skripte (V103, V104, V150–V152, V200–V209), V254 mit gespeichertem Checksum0 gegenüber aktuellem Kommentarinhalt353141432; keine V102-Zeile im Dump. Quellen/Git-Historie der betroffenen Altskripte nachvollziehen. Nach Snapshot ausschließlich auf der neuen Kopie archivierte lokale Migrationsressourcen verwenden oder eine eng begrenzte, dokumentierte lokale Historienreconciliation durchführen, mit Begründung und Vorher-/Nachhernachweis pro Abweichung. Kein blindes/pauschales Flywayrepair und kein Abschalten der Validierung. Originaldump, Originalcontainer und Produktmigrationen unverändert lassen.
  - [ ] FlywayUpgrade V368–V376 plus neue V377–V395 auf Kopie durchführen, jede Migration erfolgreich und Hibernate `ddl-auto=validate`; kein checksum repair, kein pauschales validate-off zur Fehlerumgehung. Abweichungen zu realer Altstruktur mit neuer kompatibler Migration/gezielter Erklärung korrigieren. Alte SQL-Dateien unverändert. Fehlende Uploads erwartbar und als fehlende Anhänge anzeigen, neue lokale Dummyunterlagen nutzbar.
  - [ ] Startscripttest mit Stub-java/docker/curl prüft bind-local, nur eigenen PID beenden, keine Fremdprozesse/Container löschen, schon laufenden Dienst erkennen, DBGuard aktiviert, Secrets nicht echo. Standardmäßig Hintergrundjobs aus; `start` startet Backend mit `local,local-test` und Desktop Vite auf127.0.0.1 mit `ERP_DEV_API_TARGET=http://127.0.0.1:<Backendport>` aus Task37; Backend verwendet gemäß Task38 explizit HTTP. Bei Ausweichport dieselbe tatsächliche Portnummer an Proxy und Healthcheck übergeben. `stop` beendet nur eigene Appprozesse, optional stoppt ausschließlich eigenen Container ohne Volume-Löschung.
  - [ ] Real hier starten und Login/Bedarf/Anfragevorschau/Angebot/Bestellentwurf/Dateiupload prüfen; keine echte Lieferantenmail senden. Einkaufskonto-Konfigurationsweg erklären, expliziter Nutzer-Versand und gezielter Antwortabruf bleiben funktionsfähig. Agentische Versandabnahme nur Task37-Testserver. Start/Stop/Restart reproduzieren; erreichbare URLs und fehlende historische Dateien dokumentieren.
  - [ ] Finale Gesamtprüfung und `/review-and-ship` mit GPT-6Sol, Graphifyupdate, nur eigene Dateien staged prüfen, genehmigte Pipeline bis PR/Merge durch Orchestrator. Laufende lokale Instanz auf finalen gemergten Stand bringen; dem Nutzer Start-/Stopweg und Mailkonfiguration übergeben.

## Abnahmematrix

| Spec-Abnahme | Verantwortliche Tasks | Verifikation |
|---|---|---|
|1 interne Nummer bei3Lieferanten|3,8,13,14,31|Position-/PDFtests + echter Mailinhalt Task37|
|2 Anfrage deckt keinen Bedarf|4,13,28|Mengenservice + Reload-E2E|
|3 keine Vervielfachung/Übervergabe|4,15,21,22|MySQL-Paralleltest +3Antworten|
|4 neue Revision|13,17,31,32|Immutable Revision-/Altangebottest|
|5 Vorschau=Versand/Tokenvalidierung|12,14–16,30,31|SnapshotHash + SMTP-MIME|
|6 Einkaufskonto plus Legacykonten|9–11,29,35|GreenMail SMTP/IMAP + bestehende Mailtests|
|7 Thread/Mehrfachantwort/manuell|11,16,20,31,35|Header/Code/Konfliktintegration|
|8 Autoantwort/Absage/Bounce/Dedup|11,16,35|Idempotenter Import + Klassifikationsfälle|
|9 Vollkosten/Einheiten/offen|17,18,32|A1060/B1080/Coffen, Faktoren|
|10 KI mit Quellen/manuell|20,32|FakeKI-Neulauf/Ausfall/Korrektur|
|11 tatsächlicher Versand/unklar|15,21,22,33|DATA-Timeout/Crash/Sent-Fail|
|12 historische Bestellfassung|21,22,33|Stammänderung nach Versand|
|13 spätes Zeugnis/Charge|24,25,34|Lieferung komplett/Prüfung offen|
|14 serverseitige Rollen/IDs|1,alle Controller,37|Securitymatrix/CSRF/verschachtelte IDs|
|15 Direktbestellung|12,19,21,22,33|BohnePA, aktuelle Preisbestätigung|
|16 Zeichnung/Anlagengrenzen|3,6,13,14,28|gleiche Revision bei3Empfängern|
|17 Kontakte/Kundennummer|8,12,30|getrennte Rollen/neutral/führende Nullen|
|18 HiCAD/Lager/Concurrency|4–7,28|Teilimport/Duplikat/MySQL-Locks|
|19 Zuschläge/Gültigkeit/Preis|17–19,22,32|keine Doppeladdition/keine Istkosten|
|20 Rechnung/Reklamation/Fristen|23–26,34,36|Teilrechnung/Gutschrift/keine Autonachfrage|
|§14 lokaler Probebetrieb|38,39|isolierte reale Kopie/Flyway/Startstop/keine Schedules|

## Ergänzende Ausführungshinweise

- **39 Tasks**, davon38 unabhängige frühe Betriebsgrundlage,39 finale lokale Abnahme. Der Produktumfang braucht Parallelisierung; sequenziell ist dies kein kurzer Fix. Höchstens3Tasks je Abschnitt, disjunkte Files, fertige Consumes vor Start.
- Kritischer Pfad: 1/2/3→4→13→16; 9→10→11 und12/14→15→16; 16→17→18→19/20→21→22→24→25/26→UI/37→39. Task23 wartet auf25; Task36 auf die neuen Seiten. Task38 kann früh neben Fundament laufen. Task28 folgt31/33, damit neue Anfrage-/Direktbestellaktionen auf fertige Seiten und Dialoge führen.
- Shared Files bewusst sequenziell: `pom.xml`(2,7,37), `EmailImportService`(11,16), `EinkaufKommunikationService`(16,22,35), `EinkaufsanfrageDetail`(31,32), `EinkaufBestellungDetail`(33,34), `Artikel/Preisservice`(3,18,19), App.tsx(31,33,34,36) sequenziell; neue Seite jeweils sofort routen, finale Menünavigation36. Keine parallelen Änderungen derselben Datei.
- Migrationen auf dem Integrationsbranch in Versionsreihenfolge anwenden; Tasks mit späteren Flywaynummern nicht auf einer persistenten Testkopie vor früheren noch ausstehenden Migrationen starten. Eigene frische Dummy-DB je Task erlaubt; finale Kopie ausschließlich orchestriert migrieren.
- Bestehenden Featurebranch `codex/beschaffung-konzept` gemäß Orchestrator weiterführen. Taskbranches `codex/beschaffung-task-N`, **nicht** `codex/beschaffung-konzept/task-N` (Git-Datei/Verzeichnis-Konflikt). Kontextlog existiert bereits und wird weitergeführt.
- E2E-Port je Task `5181 + Tasknummer`, keine parallele Nutzung5173/8080 der Nutzerinstanz. Bestehende `node_modules` in Worktrees bei kompatiblem Lockfile verlinken; kein paralleles npm-ci auf gemeinsamem Verzeichnis. Tests mit Dependenciesänderung isoliert installieren.
- Abgeschlossen ist der Auftrag erst mit vollständigen Abnahmefällen, grünen vorgeschriebenen Checks/Review, PR/Merge und hier startbarem System. Teststubs, reine DTOs oder Button ohne echten Versandpfad sind kein Ersatz.

## Log

Abschlusszusammenfassungen der späteren Abschnitte werden durch den Review-Agenten ergänzt; laufender Kontext ausschließlich in der separaten Kontext-Log-Datei.
