# Kasse & Belege — Abschnitt 2, Review vom 14.09.2026

Dieser Claude-Bericht bewertet den Stand **vor** den anschließend umgesetzten
Nachbesserungen. Die letzte unabhängige Ampel ist GELB. Alle fünf Warnungen
wurden inzwischen korrigiert und getestet. Der erneute Review wurde durch
das monatliche Claude-Ausgabenlimit blockiert; er ist vor dem Commit noch
nachzuholen. Details und aktuelle Testergebnisse stehen im
[Fortschrittslog](../plans/2026-09-09-kasse-belege-log.md).

Zusätzlich zu den fünf Punkten prüft die Nachprüfung den neuen
`ProjektDokumentRepository.findGeschaeftsdokumentByIdForUpdate`-Lookup:
Eine zweite Zahlung wartet unter einer Datenbanksperre und liest nach dem
Commit den aktualisierten Zahlungsstatus. Der Test mit zwei H2-Transaktionen
ist grün; ein MySQL-spezifischer Konkurrenztest wurde nicht ausgeführt.

---

Kontext vollständig gelesen (Log-Abschnitt 2, Task-Briefings 3/4/5/6/7/11, alle 38 geänderten/neuen Dateien, Backend-/Frontend-Logs). Hier der Befund.

---

## 🔎 ERP-CODE-REVIEW — Kasse & Belege, Abschnitt 2 (gegen `16747d78`)

**Geprüfte Dateien: 38**
Backend: 27 Java (7 geändert, 6 neu, 8 Bestandstests, 6 neue Tests) | Frontend: 8 TSX/TS | SQL-Migrationen: 0 (korrekt — V375 unberührt) | Sonstiges: 2 MD, 1 Golden-CSV

**Gate-Lage (fremde Logs gelesen, nicht selbst gefahren):**
`kasse-section2-backend-green.log` → 3053 Tests, 0 Failures, 0 Errors, 17 Skips, BUILD SUCCESS · `pc-lint` sauber · `pc-unit` 1414/1414 · `pc-build` ✓ · `pc-e2e` 522 passed.

---

### 🛑 KRITISCH
**Keine.** Keine Secrets, keine geänderte Bestands-Migration, keine String-Konkatenation in JPQL, keine verbotene Farbfamilie, keine nativen `<select>`/`<input type="date">`, keine Entity im Controller-Response, durchgehend Constructor Injection.

---

### ⚠️ WARNUNGEN

- **[src/main/java/.../service/KassenbuchungService.java:294]** `offeneAusgangsrechnungen()` reicht `findOffeneGeschaeftsdokumente()` ungefiltert durch. Die Query (`repository/ProjektDokumentRepository.java:24-26`) liefert neben Rechnungen auch `%mahn%` und `%erinnerung%`. `ladeRechnung` (`:282-287`) weist genau diese danach mit 400 „Bitte eine Kundenrechnung wählen" ab. → Konkreter Trigger: offene Rechnung mit 1. Mahnung ⇒ die Auswahlliste der Kachel „Geld eingenommen" enthält beide Zeilen mit identischem Betrag, die Mahnzeile führt garantiert zum Fehler; zusätzlich verbrauchen Mahnungen Plätze im `.limit(100)` (`:297`) und drängen echte Rechnungen heraus. **Fix:** vor `sorted`/`limit` filtern mit demselben Prädikat wie `ladeRechnung` (`getMahnstufe() == null && art.toLowerCase().contains("rechnung")`) und einen Fall in `KassenbuchungServiceTest:265` ergänzen. Die Validierung selbst ist korrekt — `setMahnstufe` wird ausschließlich auf eigenen Mahndokumenten gesetzt (`AutoMahnVersandService.java:482`, `DateiSpeicherService.java:409/432`), nie auf der Originalrechnung.

- **[src/main/java/.../service/KassenbuchAbschlussService.java:257-294]** Der Storno kopiert jetzt `quelle`, `gegenpartei`, `aufteilungsModus` und die drei Firmenbeträge — aber **nicht** `ausgangsrechnungId`, und die verknüpfte Kundenrechnung wird nicht zurückgesetzt. → Konkreter Trigger: Kachel „Geld eingenommen" mit `ausgangsrechnungId` setzt `rechnung.setBezahlt(true)` (`KassenbuchungService.java:193-196`) → Monat abschließen → Beleg stornieren. Das Kassenbuch ist korrigiert, `ProjektGeschaeftsdokument.bezahlt` bleibt dauerhaft `true`: Die Rechnung fällt endgültig aus `findOffeneGeschaeftsdokumente()` und aus dem Mahnlauf, obwohl das Geld nachweislich nicht geflossen ist. **Fix:** `ausgangsrechnungId` in `erzeugeStorno` mitkopieren und beim Storno einer Kassen-Einnahme das Dokument auf `bezahlt = false` zurücksetzen (mit `protokolliereStornierung`-Eintrag) — oder die Lücke bewusst im Plan für Abschnitt 3 festschreiben, bevor Task 12 die Kachel freischaltet.

- **[src/main/java/.../service/BelegKiAnalyseService.java:141-145 ↔ react-pc-frontend/src/pages/BelegeKasseEditor.tsx:513]** Der (plankonforme) Lieferant-Fix setzt `kiVorgeschlagenerLieferant` nur noch bei tatsächlicher KI-Lesung; vorher wurde dort ersatzweise der gewählte Lieferant eingetragen. Die Titelkette der Belegliste ist aber weiterhin `belegNummer || kiVorgeschlagenerLieferant || originalDateiname`. → Konkreter Trigger: Mitarbeiter wählt am Handy „Musterbaustoffe GmbH", die KI liest keinen Lieferantennamen ⇒ die Zeile heißt in der PC-Liste jetzt `quittung-1234.jpg` statt wie bisher „Musterbaustoffe GmbH". **Fix:** in `BelegeKasseEditor.tsx:513` `beleg.lieferantName` vor `kiVorgeschlagenerLieferant` einhängen (Feld ist im Listen-DTO vorhanden, wird in `:190` bereits gefiltert). Der Prüf-Dialog ist nicht betroffen (`BelegDetailModal.tsx:311` zeigt den Vorschlag nur bei leerem Lieferantenfeld).

- **[.gitattributes:1-7 ↔ src/test/resources/datev/buchungsstapel-golden.csv]** Die Golden-Datei muss byte-genau CRLF bleiben (alle 7 Zeilen sind CRLF, `KasseDatevExportServiceTest.java:36` vergleicht den Roh-String), ist aber von keiner Attributregel gedeckt und noch untracked. → Konkreter Trigger: Commit von einem Windows-Checkout mit `core.autocrlf=true` normalisiert die Datei im Index auf LF; der nächste macOS-/Linux-Checkout bekommt LF und `goldenDateiUndSpaltenpositionen` schlägt fehl, ohne dass am Writer etwas geändert wurde. **Fix:** `src/test/resources/datev/*.csv -text` (oder `text eol=crlf`) in `.gitattributes` ergänzen — das ist dieselbe Klasse Problem, für die dort schon `static/index.html` steht.

- **[react-pc-frontend/src/components/kasse/KassenbuchJournal.tsx:27-46, 66-71 ↔ KasseShortcuts.tsx:72-87]** Im Kassenbuch-Tab stehen ab jetzt zwei große Kassenstände unmittelbar übereinander: „Aktueller Kassenstand" (`text-2xl`, aus `KasseShortcuts`) und „Kasse jetzt" (`text-3xl`, neu), beide aus `/api/buchhaltung/kasse/saldo`, mit zwei getrennten Requests pro Tab-Aufruf. Nur die obere Anzeige markiert die Unterschreitung des Mindestbestands — der Handwerker sieht die prominentere Zahl ohne Warnung. **Fix:** entweder die Zahl im Journal weglassen (die Leiste steht direkt darüber) oder `saldo`/`mindestbestand` von `KassenbuchTab` als Prop an beide Kinder durchreichen und den zweiten Fetch streichen.

---

### 💡 HINWEISE

- **[src/main/java/.../service/KasseDatevExportService.java:34-38, 120-128]** `Parameter.firmenname` wird nirgends gelesen; die Kopfzeile kommt ohne aus. Entweder aus dem Record entfernen oder in Task 8 bewusst als „nicht im EXTF-Header vorgesehen" dokumentieren.
- **[src/main/java/.../service/KasseDatevExportService.java:127]** `belege.stream().allMatch(Beleg::istFestgeschrieben)` liefert bei leerer Belegmenge `true` — ein Leerexport meldet Festschreibung `1`. Für DATEV irreführend, `!belege.isEmpty() && allMatch(...)` wäre ehrlicher.
- **[src/main/java/.../service/BelegPdfService.java:152-157 ↔ KassenbuchungService.java:165-175]** (Task-3-Rückfrage, real bewertet) `schreibeUndHashe` schreibt erst die Datei und hasht danach; scheitert `berechneDateiHash`, fliegt die `RuntimeException`, ohne dass der Aufrufer den Dateinamen je erfährt — `dateipfad` ist im neuen Pfad noch `null`, `loescheDatei(null)` tut nichts. **Reale Auswirkung: gering.** Kein DB-Eintrag, kein Datenverlust, keine Sicherheitslücke, nur eine unreferenzierte PDF in `uploads/belege`; das Fenster setzt einen Lesefehler unmittelbar nach erfolgreichem Schreiben in denselben Ordner voraus. Sauber wäre ein `try/finally` mit `Files.deleteIfExists` **in** `BelegPdfService` — das ist aber fremdes Terrain aus Abschnitt 1 und kein Blocker für diesen Commit.
- **[src/main/java/.../service/BuchungssatzAbleitung.java:87-96 ↔ KassenbuchungService.java:105-111]** (Task-7-Rückfrage zu Legacy-Stornos, konkret geprüft) Die Heuristik ist für alle heute erzeugbaren Fälle eindeutig: neue Transfer-/Privat-Kacheln tragen 1200/1800/1810 (kein AUFWAND/ERTRAG) ⇒ `istDatevTransfer` = true; V375-backfillte Alt-Stornos mit Aufwands-/Ertragskonto ⇒ false; Alt-Stornos ohne Konto ⇒ true, was für belegfreie Kassen-Umbuchungen der richtige Default ist; PRIVAT* konsultieren `transfer` gar nicht. Einzige theoretische Mehrdeutigkeit: der Legacy-Adapter erhält ein mitgesendetes Sachkonto, erzwingt aber `quelle = TRANSFER` — bei KASSE_EINNAHME/AUSGABE ignoriert `ableitenKonten` das Konto dann zugunsten der Bank, die spätere Gegenbuchung benutzt es. **Heute folgenlos:** kein Frontend ruft `POST /api/buchhaltung/umbuchungen` mehr auf (kein Treffer in `react-pc-frontend/src` und `react-zeiterfassung/src`). Falls der Endpunkt extern weiterlebt, hier einen Regressionstest nachziehen.
- **[react-pc-frontend/src/components/kasse/KassenbuchJournal.tsx:138]** Die Spalte „Beleg" zeigt `Beleg #{belegId}`, also die interne Datenbank-ID. `KassenBewegung` (`types.ts:1280-1298`) führt weder Dateinamen noch Belegnummer, insofern die einzige verfügbare Option — für den Handwerker aber eine bedeutungslose Zahl. Vorschlag für Abschnitt 3: `quelle` in die Bewegung aufnehmen und stattdessen ein Herkunfts-Badge (Quittung / Scan / Ersatzbeleg) zeigen.
- **[react-pc-frontend/src/components/kasse/KasseErklaerkasten.tsx:26, 38]** `aria-controls={inhaltId}` verweist im zugeklappten Zustand auf ein nicht gerendertes Element. Verbreitetes Muster, Screenreader verkraften es; sauberer wäre, den Inhalt immer zu rendern und per `hidden` zu steuern.

---

### ✅ BESTANDEN

- **Secrets & DSGVO:** Diff enthält nur `systemSettingsService.getGeminiApiKey()` und `"dummy-test-key"`; keine `.properties`/`.yml`/`.env`/Keys/`uploads/` im Scope. Alle Fixtures durchgängig Dummy (Max Mustermann, Musterbaustoffe GmbH, Musterbetrieb GmbH, R-MUSTER-7).
- **Flyway:** keine Migration im Diff — V375 bestätigt unberührt.
- **Schichtentrennung:** `KassenbuchungController` delegiert ausschließlich, Auth + Fehler-Mapping (403/400/409/404/500) exakt nach `KasseShortcutController`-Muster; Response durchgehend `BelegDto.Response`, Request als `KassenbuchungDto.CreateRequest`.
- **DI:** `@RequiredArgsConstructor` in allen neuen Services/Controllern, kein Feld-`@Autowired`, keine `new Service(...)`.
- **JPA:** `findLetzteGepruefteByLieferant` (`BelegRepository.java:106-113`) mit Named Params, `LEFT JOIN FETCH` auf beide nullable Beziehungen und `Pageable(0,20)`; `BelegVorschlagService` macht genau **einen** Historien-Lookup für beide Vorschläge und läuft nur bei `mitPositionen == true` — `listBelege` bleibt frei von N+1 (Kommentar `BelegService.java:1097-1099` belegt den Grund).
- **Transaktion + Datei-Cleanup:** `registriereRollback` vor jedem Schreibvorgang plus `catch`-Löschung in beiden Zweigen; per Test abgesichert (`KassenbuchungServiceTest:241 geloeschteDateiBeiAuditFehler`, `:251 scanWirdAuchBeiTransaktionsRollbackGeloescht`, `:322 teilweiserUploadWirdBeiSchreibfehlerGeloescht`).
- **Upload-Sicherheit:** MIME- und Endungs-Whitelist, 25-MB-Grenze, `Path.of(name).getFileName()` + Zeichenfilter, UUID-Präfix, `startsWith(basis)`-Prüfung in `belegPfad` (`:327-335`), SHA-256; parametrisiert getestet mit `../../beleg.pdf`, `a\b.pdf`, `.exe`, `.svg`, `.js`.
- **Path Traversal im KI-Pfad:** `buildInitialTurn` prüft `startsWith` **und** `toRealPath().startsWith(...)` — inklusive eigenem Symlink-Test (`symlinkAusserhalbBelegordnerWirdNichtAnKiGesendet`); Fehlerlogs geben nur `e.getClass().getSimpleName()` und die Beleg-ID preis, keine Pfade.
- **Zugriffsrechte:** beide neuen Endpoints prüfen `findCaller` + `darfScannen`, 403 vor jeder Logik; `unerwarteterFehlerGibtKeineInternenDatenPreis` sichert zu, dass 500 keine internen Details leakt.
- **DATEV-Writer:** Kopfzeile 22 Felder, Spaltenzeile und alle Datenzeilen exakt 125 Felder, CRLF, Komma-Dezimaltrenner, `S` in jeder Zeile, `TTMM`-Belegdatum, Windows-1252-Roundtrip (`:44`), Escaping von `"`/`;`/CR/LF, Golden-Datei UTF-8 lesbar. Netto→Brutto-Hochrechnung mit kumulativer Cent-Rundung, Restzeile ohne KOST1, Mischbon-Privatanteil auf 1800 ohne Steuer, Überverteilung abgewiesen — je mit eigenem Test. `Wirtschaftsjahr.beginn` klemmt ungültige Beginnmonate und ist separat abgedeckt.
- **Storno-Semantik:** Kategorie gedreht, Steuerart des Originals beibehalten (`steuer()`, `:143-154`), private Defaults des Originals (1800/1810) in `ableitenKonten`, V375-Backfill über den Kontotyp korrigiert — `migrierteGegenbuchungMitAufwandskontoIstKeinBanktransfer` und `privateGegenbuchungVerwendetDasKontoDesOriginals` belegen es.
- **Zahlungsstatus:** `updateBeleg` validiert den Wert vor jeder Feldänderung (`BelegService.java:317-320`), löscht bei `OFFEN` auch `bereitsGezahlt` (sonst bliebe die Rechnung durch die Repository-Abfrage aus den offenen Posten ausgeschlossen) und protokolliert alle drei Felder über `merke`; das DTO zeigt `bezahlt OR bereitsGezahlt` — genau die im Log dokumentierte RED/GREEN-Korrektur.
- **Bestandstests unangetastet:** die vier Charakterisierungs-Suites bekommen ausschließlich Verkabelung (`@MockBean` bzw. `ReflectionTestUtils.setField`); keine erwartete Zahl, keine Exception-Meldung, keine Fallmenge verändert. Die zwei geänderten Lieferanten-Erwartungen in `BelegKiAnalyseServiceTest` sind die vom Plan ausdrücklich geforderte Rohlesung.
- **Frontend-Design:** nur `rose`/`slate` plus die freigegebenen Funktionsfarben (indigo = neutrale Info, emerald = Einnahme, amber = Ausgabe/Warnung); kein `teal`/`blue`/`sky`/`cyan`/`green` im Diff. `DatePicker` statt `<input type="date">`, kein natives `<select>`, kein `dangerouslySetInnerHTML`, kein `console.log`, kein `any`, React-Keys über `belegId`/Titel statt Index. `min-w-0` konsequent auf jeder Flex-/Grid-Ebene.
- **Wording:** „Kasse jetzt", „Summe Einnahmen/Ausgaben", „Anfangsbestand", „Bestand am Ende", „Storniert", „Gegenbuchung" — kein „Soll"/„Haben"/„T-Konto" mehr, per E2E-Assertion `getByText(/^(Soll|Haben)$/) → 0` abgesichert. Hilfe-Dialog + `docs/KASSE_ANLEITUNG.md` tragen identische Abschnittsüberschriften, Abweichung von der Markdown-Route ist an Ort und Stelle begründet (`KasseAnleitungDialog.tsx:5-9`).
- **E2E-Abdeckung:** `kassenbuch-journal.spec.ts` deckt alle sieben Spalten, Serverbestände, Stornos, Nummerierung, Suche mit Summen-Hinweis, localStorage-Persistenz über `page.reload()`, Dialog inkl. Tastatur-Rückfokus und Überlaufmessung per `scrollWidth`/`clientWidth` ab; `kasse-refactoring.spec.ts` wurde korrekt mitgezogen. Je Zustand `designPruefung(...)`.
- **Build-Artefakte:** kein `src/main/resources/static`-Output und kein Graph-Diff im Arbeitsbaum.

---

## GESAMT-AMPEL: 🟡 GELB

Kein Blocker — die Architektur, die Buchhaltungsregeln, die Datei- und Transaktionssicherheit sowie die Testabdeckung tragen. Vor dem Rundencommit sollten **Warnung 1** (Mahnungen in der Rechnungsauswahl) und **Warnung 4** (`.gitattributes` für die Golden-Datei) erledigt werden, weil beide je eine Zeile kosten und sonst sofort auffallen. **Warnung 2** (Kundenrechnung bleibt nach Storno „bezahlt") ist der inhaltlich schwerste Punkt: entweder hier schließen oder ausdrücklich als Aufgabe für Abschnitt 3 in den Plan schreiben, bevor Task 12 die Kachel für Nutzer freigibt. Warnungen 3 und 5 sind kleine, gut isolierte Frontend-Korrekturen.
