# Spec: Zeitkonto-Vorlagen und Historisierung

Issues: **#98** (Monatsabschluss — der erste Baustein dieses Vorhabens,
umfasst E9/E11/E12/E13, siehe unten), #93 (Zeitkonto historisieren), #94
(System-Mitarbeiter kennzeichnen), #95 (Zeitkonto ja/nein je Mitarbeiter),
#96 (Zeitkontenmodelle als Katalog). #98:
https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/98.
Die übrigen vier:
https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/93
(94, 95, 96 dort als Unter-Issues verlinkt, Stand 0/3 abgeschlossen). E10
(Abschnitt 3, eingefrorene Abwesenheitsstunden) hat **keinen** eigenen
Issue-Bezug — ein reiner Befund aus dem Brainstorming, keine gesonderte
Aufgabe.

Status: Grundlage ist ein vom Nutzer abgenommenes Brainstorming zu
#93/#94/#95/#96 (als Entscheidungsliste übergeben, keine eigene
Brainstorming-Datei), ergänzt um den Monatsabschluss-Komplex (#98), der im
selben Brainstorming als Nachtrag entstand: der Monatsabschluss selbst
(E9), die Abgrenzung Anzeige-vs-Änderungssperre (E11), eine
Erinnerungsfunktion in der Benachrichtigungs-Glocke (E12) und die
Berechtigung darüber (E13) — dazu ein eigenständiger, nicht issue-gebundener
Befund zu eingefrorenen Abwesenheitsstunden (E10). Der Nutzer hat
entschieden, alle fünf Issues in einem Vorhaben umzusetzen. #93/#94/#95/#96
fassen dieselben Stellen an (`ZeitkontoService`, `ZeitverwaltungController`,
`MitarbeiterEditor.tsx`) — getrennt umgesetzt müssten dieselben rund 16
Aufrufstellen von `getOrCreateZeitkonto` mehrfach angefasst werden. #98
berührt zwar primär andere Dateien (`MonatsSaldo`, `MonatsSaldoService`,
`MonatsSaldoRepository`, `NotificationController`, `Abteilung`), gehört
aber ebenfalls hierher und an den Anfang: Es ist die Voraussetzung dafür,
dass die übrigen vier Themen — die alle Neuberechnungen auslösen können
(Zeitkonto-Wechsel, Vorlagenänderung, Historisierungs-Migration) — keinen
bereits abgerechneten Monat verändern.

Empfohlene Bearbeitungsreihenfolge: **#98 → #94 → #95 → #96 → #93.**
Monatsabschluss zuerst, weil er unabhängig von den anderen vier Themen ist
und die stärkste, allgemeinste Absicherung liefert, bevor an den Stellen
gearbeitet wird, die überhaupt erst rückwirkende Neuberechnungen auslösen.
Danach System-Mitarbeiter zuerst kennzeichnen, damit er nicht in die neue
Versionshistorie hineingerät; Vorlagen vor der Historisierung, damit die
erste Version nicht ohne Vorlagenkonzept gebaut und später umgebaut werden
muss.

Aufbauend auf #97/#91 (Langzeitkrankmeldung, bereits auf `main` gemergt,
Stand `e11bbbc1`), das `TagesSollService` als zentrale Stelle für die
Tagessoll-Berechnung eingeführt hat (verifiziert:
`src/main/java/org/example/kalkulationsprogramm/service/TagesSollService.java`,
216 Zeilen). Diese Spec ersetzt dessen `Zeitkonto`-Parameter durch
versionierte Werte, ohne die dort bereits gelöste Eigenschaft zu verlieren,
Daten einmal je Zeitraum statt einmal je Tag zu laden.

## Ziel

Das Büro (die Person, die im Betrieb Mitarbeiterstamm und Zeiterfassung
pflegt) kann:

- einen Monat abschließen, sobald die Lohnabrechnung dafür raus ist, damit
  sich sein Saldo garantiert nie wieder ändert — unabhängig davon, was
  später am Zeitkonto, an einer Vorlage oder im Rechenkern selbst geändert
  wird; der laufende und jeder noch offene Monat bleiben für den Mitarbeiter
  dabei jederzeit live sichtbar, der Abschluss sperrt nur die
  Neuberechnung, nie die Anzeige; das Büro wird an einen fälligen, noch
  nicht erfolgten Abschluss erinnert, statt dass er in Vergessenheit gerät;
- eine Vertragsänderung (Stundenreduzierung, Wechsel Vollzeit/Teilzeit,
  andere Tagesverteilung) eintragen, ohne dass sich dadurch die Sollstunden
  bereits abgerechneter Monate aus der Vergangenheit ändern;
- einen technischen System-Datensatz (aktuell "System Webseite") als das
  erkennen, was er ist — kein Mensch, kein Zeitkonto, keine
  Sollstunden-Rechnung —, ohne ihn über den fachlich falschen Umweg
  "ausgeschieden" zu verstecken;
- Mitarbeitern ohne vertragliche Arbeitszeit (typischerweise
  Geschäftsführer) explizit sagen "diese Person führt kein Zeitkonto",
  statt dass ihnen automatisch 40 Wochenstunden angerechnet werden, die nie
  gestempelt werden;
- wiederkehrende Wochenmodelle ("Vollzeit 40 Stunden", "Werkstatt", "Büro
  geringfügig") einmal als Arbeitszeit-Vorlage pflegen und Mitarbeitern
  zuweisen, statt sieben Wochentagsfelder für jede Person einzeln
  einzutippen — und beim Ändern einer Vorlage kontrolliert entscheiden, ab
  wann und für wen die Änderung gilt, statt dass sie automatisch und
  rückwirkend durchschlägt.

## Nicht-Ziele

- **Der Monatsabschluss sperrt keine Anzeige.** Ausdrückliches Anti-Ziel,
  wörtlich vom Nutzer: "Aber die Stunden der Mitarbeiter sollten trotzdem
  immer auf der Handy-App live angezeigt werden, nicht erst, wenn der
  Monatsabschluss erfolgt ist. Sie sollen immer live wissen, wie viel
  Urlaub ich habe und wie viele Stunden ich habe." Saldo und
  Urlaubsanspruch bleiben in Handy- und PC-App jederzeit live sichtbar,
  auch für noch nicht abgeschlossene Monate (inklusive des laufenden). Der
  Abschluss verhindert ausschließlich eine erneute Berechnung, niemals die
  Darstellung — kein Bildschirm wartet auf einen Abschluss, um Werte zu
  zeigen (Details Abschnitt 2).
- **Rundung, Karenzminuten, Schichtschwellen.** Wörtlich vom Nutzer:
  "Rundungen sollen erst mal nicht einstellbar sein. Da soll es erst mal
  keine Karenzzeit geben. Stechzeit ist Zeit." Der in #93 offen gelassene
  Punkt "Rundungsregeln" wird damit ausdrücklich nicht umgesetzt — und
  bekommt bewusst kein Folge-Issue.
- **Kein Eingriff ins Datenmodell der Abwesenheit.** `abwesenheit.stunden`
  bleibt ein beim Buchen einmalig berechneter, danach eingefrorener Wert
  (Details Abschnitt 3). Bei Änderungen im selben offenen Zeitraum sind
  abweichende Gutschriften ausdrücklich in der Vorschau sichtbar zu machen.
- **Das Arbeitszeitmodell wird nicht an die Abteilung gekoppelt.** Ein
  Mitarbeiter erbt seine Arbeitszeit nicht aus seiner Abteilung —
  `AbteilungRepository` und `ArbeitsgangController` bleiben in diesem Sinn
  unverändert. Abteilung (organisatorisch, trägt Berechtigungen) und
  Zeitkontenmodell (vertragliche Arbeitszeit) sind zwei unabhängige Achsen;
  eine Abteilung hat mehrere Modelle gleichzeitig (Büro: Vollzeit und
  geringfügig nebeneinander). **Davon unberührt:** `Abteilung` bekommt in
  diesem Vorhaben sehr wohl ein zusätzliches Berechtigungs-Flag
  `darfMonatAbschliessen` (Abschnitt 5, E13) — das ist eine Berechtigung,
  keine Arbeitszeit-Zuordnung, und verletzt die hier beschriebene Trennung
  nicht.
- **Der Altdaten-Import** aus dem abzulösenden Zeiterfassungssystem ist der
  Anlass für #93, aber nicht Teil dieses Vorhabens — eigenes, späteres
  Thema.
- **GoBD-Audit-Einträge über einen technischen System-Mitarbeiter.**
  `V321__zeitbuchung_eindeutige_aktive_buchung.sql` verzichtet auf
  automatisches Cleanup, weil `zeitbuchung_audit.geaendert_von_mitarbeiter_id`
  NOT NULL ist und es "im System keinen technischen System-Mitarbeiter
  gibt" (Kommentar in der Migration). Mit der Kennzeichnung aus diesem
  Vorhaben wird das möglich — hier nur als Notiz festgehalten, nicht
  umgesetzt.

## Architektur/Ablauf

### 1. Monatsabschluss, als erster Baustein (E9)

Aus dem abzulösenden Fremdsystem übernommen: ein Monat lässt sich
festschreiben — mit Stunden und allem. Kommt als erstes Thema in dieses
Vorhaben, vor die Historisierung.

Begründung, warum beide Konzepte nötig sind — sie lösen verschiedene
Probleme:

- **Historisierung** macht die Berechnung reproduzierbar, schützt aber nur
  gegen die eine Ursache, die historisiert wurde (das Wochenmodell). Nicht
  geschützt: eingefrorene Abwesenheitsstunden (Abschnitt 3), korrigierte
  Feiertage, nachgetragene Buchungen, und vor allem jeder künftige Bugfix
  im Rechenkern — wer nächstes Jahr einen Fehler in `TagesSollService`
  behebt, verschiebt damit alle Altmonate.
- **Monatsabschluss** ist ein Riegel gegen alles: festgeschrieben heißt,
  wird nie wieder gerechnet, egal was sich ändert. Eine Regel an einer
  Stelle statt einer Sorgfaltspflicht an jeder Aufrufstelle.
- Die Historisierung bleibt nötig für alle offenen Monate und für noch nie
  berechnete Zeiträume. Sie schützt deren Soll vor einem späteren
  Modellwechsel. Ein Abschluss kann nur einfrieren, was schon existiert.

Umsetzung — die Infrastruktur ist weitgehend vorhanden. `MonatsSaldo`
(`src/main/java/org/example/kalkulationsprogramm/domain/MonatsSaldo.java`,
verifizierte bestehende Felder: `jahr`, `monat`, `gueltig`, `berechnetAm`,
`istStunden`, `sollStunden`, `abwesenheitsStunden`, `feiertagsStunden`,
`korrekturStunden`) bekommt:

- `festgeschrieben` (Boolean), plus wer/wann — nach dem im Projekt bereits
  etablierten Attributions-Muster `@ManyToOne Mitarbeiter` (verifiziert:
  `erstelltVon` in `ZeitkontoKorrektur.java:61` und
  `LieferantReklamation.java:37`, `freigegebenVon` in `BwaUpload.java:75`):
  `festgeschriebenAm` (LocalDateTime) und `festgeschriebenVon`
  (`Mitarbeiter`).
- Alle fünf `invalidiere*`-Methoden in `MonatsSaldoService`
  (`invalidiereMonat`, `invalidiereJahr`, `invalidiereAlle`,
  `invalidiereFuerDatum`, `invalidiereFuerDateTime`, Zeilen 205-247)
  überspringen festgeschriebene Monate. Konkret betrifft das die drei
  zugrunde liegenden `@Modifying`-Queries in `MonatsSaldoRepository`
  (`invalidiere` Zeilen 50-52, `invalidiereJahr` Zeilen 57-59,
  `invalidiereAlle` Zeilen 64-66) — jede bekommt eine zusätzliche
  Bedingung, die festgeschriebene Zeilen ausnimmt.
- `getOrBerechne` (`MonatsSaldoService.java:67`) gibt einen
  festgeschriebenen Saldo unverändert zurück, statt ihn neu zu rechnen.
  Diese Prüfung muss vor der bestehenden Weiche laufen, die für den
  aktuellen und jeden zukünftigen Monat immer live rechnet
  (`MonatsSaldoService.java:68-79`) — sonst würde ein (im Ausnahmefall)
  festgeschriebener laufender Monat trotzdem live überschrieben.
- `MonatsSaldoWarmupService` überspringt festgeschriebene Monate ebenfalls.
- Eine Oberfläche zum Abschließen und zum Wieder-Öffnen. Öffnen muss
  möglich sein (Korrekturfall) und sichtbar protokolliert werden. Zugriff
  darauf nur mit entsprechender Berechtigung (Abschnitt 5, E13).

Fachlicher Anker: Nach Prüfung eines Monats soll dessen gespeicherter
Stundensaldo stabil bleiben. Dies ist eine fachliche Abschlussfunktion,
keine Zusicherung rechtlicher GoBD-Konformität. Die `Lohnabrechnung`-
Entity existiert bereits. Ob der Abschluss automatisch an die
Lohnabrechnung gekoppelt wird oder ein bewusster eigener Schritt bleibt,
ist offen (siehe Offene Punkte) — Empfehlung: eigener, bewusster Schritt,
weil eine automatische Kopplung den Monat zumachen würde, bevor jemand ihn
geprüft hat.

Wording in Handwerker-Sprache: **"Monat abschließen"** / **"Monat wieder
öffnen"**, nicht "festschreiben" oder "Periodenabschluss". Der
abgeschlossene Monat ist in der Oberfläche als solcher erkennbar
(Schloss-Symbol o. ä.).

### 2. Der Monatsabschluss sperrt Änderung, nicht Anzeige (E11)

Klarstellung zu Abschnitt 1, ausdrücklich vom Nutzer eingebracht, wörtlich:
"Aber die Stunden der Mitarbeiter sollten trotzdem immer auf der Handy-App
live angezeigt werden, nicht erst, wenn der Monatsabschluss erfolgt ist.
Sie sollen immer live wissen, wie viel Urlaub ich habe und wie viele
Stunden ich habe. Notfalls kann das auch irgendwie beschrieben werden:
'Monatsabschluss noch nicht erfolgt, also nicht geprüft'."

Wer Abschnitt 1 liest, könnte naheliegend den Abschluss zur Voraussetzung
für die Saldo-Anzeige machen. Das ist ausdrücklich falsch und muss
unmissverständlich ausgeschlossen bleiben:

1. **Saldo und Urlaubsanspruch sind jederzeit live sichtbar** — in der
   Handy-App (`react-zeiterfassung`, u. a. `SaldenPage.tsx`,
   `DashboardPage.tsx`) und in der PC-App (verifiziert:
   `react-pc-frontend/src/pages/ZeiterfassungKalender.tsx`, 1417 Zeilen,
   zeigt heute schon einen "Jahressaldo" über
   `/api/zeiterfassung/saldo/{token}`). Für den laufenden Monat wie für
   jeden noch nicht abgeschlossenen. Niemand wartet auf einen Abschluss,
   um zu sehen, wo er steht.
2. **Der angezeigte Saldo setzt sich aus zwei Teilen zusammen:** den
   abgeschlossenen Monaten (fester, geprüfter Wert) plus allen noch offenen
   Monaten (live gerechnet — verifiziert bereits heutiges Verhalten von
   `getOrBerechne` für den laufenden und jeden zukünftigen Monat,
   `MonatsSaldoService.java:67-79`). Beides zusammen ergibt den Stand, den
   der Mitarbeiter sieht. Der Abschluss ändert nur, ob ein Monat noch neu
   gerechnet werden darf — nicht, ob er in die Summe eingeht.
3. **Der vorläufige Teil ist erkennbar gekennzeichnet.**
   Formulierungsvorschlag in Handwerker-Sprache: "Geprüft bis März 2026 —
   die Stunden seitdem sind noch vorläufig." Nicht "ungeprüft" allein
   stehen lassen, das klingt nach Fehler; es geht um "noch nicht
   abgeschlossen", nicht um "falsch".
4. **Ausdrückliches Anti-Ziel:** Kein Bildschirm zeigt "Saldo erst nach
   Monatsabschluss verfügbar" oder blendet Werte aus, weil der Monat noch
   offen ist. Der Abschluss ist ein Riegel gegen Änderung, nicht gegen
   Anzeige.

### 3. Abwesenheitsstunden sind eingefroren — bleibt so (E10)

Befund, der in keinem der vier Issues steht, aber ein zweiter, von der
Historisierung unabhängiger Verfälschungsweg ist — und der eigentliche
Grund, warum Abschnitt 1 überhaupt gebraucht wird.

`AbwesenheitService.bucheAbwesenheit()` (`AbwesenheitService.java:60-61`)
berechnet die Stunden einer Abwesenheit **einmal** beim Buchen aus dem
damals gültigen Zeitkonto (über `tagesSollService.arbeitsSoll`) und
schreibt sie in `abwesenheit.stunden` (`Abwesenheit.java:50-51`,
`@Column(nullable = false, precision = 10, scale = 2)`). Danach werden sie
nie neu berechnet. `MonatsSaldoService` zieht sie über
`abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween`
(`MonatsSaldoService.java:128`) als gespeicherte Summe.

Daraus folgt eine Asymmetrie: Bei einer Neuberechnung wird die Soll-Seite
frisch gerechnet, die Abwesenheits-Seite bleibt der alte Snapshot.
Konkretes Beispiel:

> Ein Mitarbeiter mit 8-Stunden-Tagen nimmt 25 Urlaubstage, gebucht mit je
> 8 Stunden. Der Betrieb stellt ihn auf 6-Stunden-Tage um. Eine
> vollständige Neuberechnung der Altmonate lässt das Soll auf 6 Stunden je
> Tag sinken, die Urlaubsgutschrift bleibt bei 8 — macht 2 Stunden Plus je
> Urlaubstag, 50 Überstunden aus dem Nichts, rückwirkend über die gesamte
> Betriebszugehörigkeit.

**Entscheidung des Nutzers:** Das Einfrieren bleibt, kein Eingriff ins
Datenmodell der Abwesenheit. Der damalige Stundenwert bleibt als Snapshot erhalten. Stunden und
Urlaubsanspruch in Tagen sind dabei unterschiedliche Größen; eine
Stundenänderung bedeutet nicht automatisch eine Änderung des Anspruchs.
Bei einem Wechsel innerhalb eines offenen Zeitraums muss die Vorschau
die unveränderten Abwesenheitsgutschriften und mögliche Abweichungen nennen. Der Schutz kommt aus
dem Monatsabschluss (Abschnitt 1): Ein bereits abgerechneter Monat mit
eingefrorenen 8-Stunden-Urlaubstagen wird gar nicht erst neu gerechnet.

### 4. Erinnerung an den fälligen Monatsabschluss (E12)

Ein Riegel, an den niemand erinnert wird, wird vergessen — ein nie
abgeschlossener Monat ist genauso ungeschützt wie gar kein Abschluss. Die
Erinnerung ist deshalb kein Komfort, sondern das, was Abschnitt 1 überhaupt
wirksam macht.

Wunsch des Nutzers, wörtlich: "es soll dann immer im Notification Center
hingewiesen werden: 'Monat ist abzuschließen'."

**Vorhandene Infrastruktur, nicht neu bauen:** `NotificationController`
(`src/main/java/org/example/kalkulationsprogramm/controller/NotificationController.java`,
785 Zeilen, `GET /api/notifications/summary?mitarbeiterId=...`) aggregiert
laut Klassenkommentar "Zähler und aktuelle Einträge aus 7 Quellen"
(verifiziert aktuell mehr: mindestens 12 Kategorien, u. a.
`ANFRAGEN_WEBSEITE`, `EMAILS`, `URLAUBSANTRAEGE`, `TERMINE`), jede Kategorie
live bei jedem Abruf berechnet, als `CategoryDto` (`type`, `label`, `count`,
`icon`, `link`) und `RecentItemDto` (`type`, `title`, `subtitle`,
`timestamp`, `link`) — beide Records am Dateiende. Frontend:
`react-pc-frontend/src/components/layout/NotificationBell.tsx` (538
Zeilen, enthält je Kategorie-Typ eine explizite Icon-/Gruppen-Zuordnung,
verifiziert z. B. `types: ['TERMINE']` → `icon: CalendarClock` — kein
automatischer Fallback für unbekannte Typen), dazu `notification-helpers.ts`
(229 Zeilen, reine Logik ohne React, extra ausgelagert für
Vitest-Testbarkeit — Interfaces `CategoryDto`/`RecentItemDto`/
`NotificationSummary` spiegeln die Backend-DTOs) und
`lib/notificationRefresh.ts`. Tests liegen daneben:
`NotificationBell.test.ts`, `NotificationBell.render.test.tsx`.

Was das für den Monatsabschluss bedeutet:

1. Der Monatsabschluss wird eine **weitere Kategorie in der bestehenden
   Glocke**, kein eigener Benachrichtigungsweg. Vorbild ist eine der
   vorhandenen Kategorien in `NotificationController.getSummary()`.
   Zusätzlich bekommt `NotificationBell.tsx` eine neue Icon-/Gruppen-
   Zuordnung für den neuen Kategorie-Typ.
2. Ausgelöst wird der Hinweis, sobald ein Monat vorbei ist und noch keinen
   Abschluss hat. Der laufende Monat erzeugt keinen Hinweis.
3. Wording in Handwerker-Sprache, Vorschlag: **"Monat abzuschließen"** als
   Kategoriename, Eintrag darunter z. B. "März 2026 — noch nicht
   abgeschlossen". Nicht "Periodenabschluss fällig".
4. **Performance-Auflage:** `NotificationController` rechnet bei jedem
   Glocken-Abruf live über alle Kategorien. Die neue Kategorie muss
   deshalb über eine einzelne, indexgestützte Abfrage laufen (welche
   Monate haben keinen abgeschlossenen Saldo) statt über eine Schleife
   Mitarbeiter × Monate — sonst wird die Glocke langsam.

Wer den Hinweis bekommt, ist über die Berechtigung aus Abschnitt 5 (E13)
geklärt: die Mitarbeiter der Abteilungen mit dem Flag
`darfMonatAbschliessen`, nicht alle.

### 5. Berechtigung für den Monatsabschluss (E13)

Entscheidung: Wer Monate abschließen darf, wird über die bestehenden
Abteilungs-Berechtigungen eingestellt, nicht über einen neuen Mechanismus.
Damit ist auch geklärt, wer den Glocken-Hinweis aus Abschnitt 4 bekommt —
ein einziges Flag beantwortet beide Fragen.

Vorhandene Struktur, die genutzt wird: `Abteilung`
(`src/main/java/org/example/kalkulationsprogramm/domain/Abteilung.java`)
trägt heute vier Flags, alle `@Column(nullable = false)` mit Default am
Feld: `darfRechnungenGenehmigen` (Zeile 29, Default `false`),
`darfRechnungenSehen` (Zeile 36, Default `false`),
`darfFreigabeAnnahmePushen` (Zeile 44, Default `true`),
`darfWebseitenAnfragenPushen` (Zeile 52, Default `true`).
`AbteilungBerechtigungDto` spiegelt sie in zwei geschachtelten Klassen,
`Response` (Flags Zeilen 21-24) und `UpdateRequest` (Flags Zeilen 42-45).
Zugehöriger Controller: `AbteilungBerechtigungController`. Frontend:
`react-pc-frontend/src/pages/AbteilungBerechtigungenEditor.tsx`, Route
`/abteilung-berechtigungen`, in `App.tsx:113` mit `<RequireAdmin>`
geschützt.

**Ein neues Flag `darfMonatAbschliessen`.** Wer es hat, darf Monate
abschließen und wieder öffnen — und bekommt den Glocken-Hinweis aus
Abschnitt 4.

Begründung, warum ein einzelnes Flag reicht, anders als beim
Zwei-Flags-Muster bei Rechnungen: Das Projekt trennt bei Rechnungen bewusst
`darfRechnungenGenehmigen` (sehen und genehmigen) von
`darfRechnungenSehen` (nur sehen), weil es dort zwei echte Rollen gibt —
Büro genehmigt, Buchhaltung schaut zu (dokumentiert in
`OffenePostenController.java:53-54`). Beim Monatsabschluss gibt es diese
zweite Rolle nicht: Ob ein Monat abgeschlossen ist, ist eine Eigenschaft
des Monats, die auf der Zeitkonten-Seite ohnehin mit angezeigt wird (siehe
Abschnitt 2) — wer die Seite sehen darf, sieht auch das
Abgeschlossen-Kennzeichen. Ein zweites Flag nur fürs Sehen wäre eine
Einstellung, die niemand bewusst setzt.

Umsetzung:

- Neue Spalte in derselben Migration wie der Rest des Abschluss-Bausteins
  (Abschnitt 11), `NOT NULL DEFAULT FALSE`. Bewusst `false`, nicht `true`
  wie bei den beiden Push-Flags: nach der Migration darf zunächst niemand
  abschließen, der Betrieb schaltet es gezielt frei. Ein versehentlich
  abgeschlossener Monat ist teurer als ein zu spät abgeschlossener.
- `AbteilungBerechtigungDto` (beide geschachtelten Klassen) und
  `AbteilungBerechtigungController` ziehen nach.
- `AbteilungBerechtigungenEditor.tsx` bekommt den Schalter. Handwerker-
  Sprache: **"Monate abschließen"**, Hilfetext sinngemäß "Aus, wenn diese
  Abteilung Monate weder abschließen noch wieder öffnen soll."
- Die Endpoints zum Abschließen/Wieder-Öffnen (Abschnitt 1) prüfen das Flag
  serverseitig, nicht nur die Oberfläche — ein ausgeblendeter Knopf ist
  keine Berechtigung.

### 6. System-Mitarbeiter als eigene Art (#94)

Neue Spalte `mitarbeiter.art` — `ENUM('MENSCH','SYSTEM') NOT NULL DEFAULT
'MENSCH'` — sagt, **was** ein Datensatz ist. `aktiv` sagt danach wieder nur,
**ob** ein Mensch noch beschäftigt ist; ein ausgeschiedener Mitarbeiter
(`aktiv=0`, `art=MENSCH`) bleibt in Historie und Auswertung sichtbar, ein
System-Datensatz (`art=SYSTEM`) verschwindet unabhängig von `aktiv` überall
dort, wo Menschen gemeint sind.

Neues Java-Enum `MitarbeiterArt` (Werte `MENSCH`, `SYSTEM`), angelegt analog
zu `Beschaeftigungsart`
(`src/main/java/org/example/kalkulationsprogramm/domain/Beschaeftigungsart.java`
— Enum-Konstante plus lesbare Bezeichnung).

`MitarbeiterRepository` bekommt eine Filtermöglichkeit auf `art = MENSCH`
(verifiziert vorhanden: `findByAktivTrue()` in `MitarbeiterRepository.java:22`,
keine `art`-Filterung existiert bisher). Alle Stellen, die "alle Mitarbeiter"
lesen, aber tatsächlich Menschen meinen, ziehen darauf um. Verifiziert per
`grep`:

- `MitarbeiterService.list()` — `findAll()` ohne Filter
  (`MitarbeiterService.java:66`), Grundlage für `GET /api/mitarbeiter`.
- `ZeitverwaltungController.getAlleZeitkonten()` — `findAll()`
  (`ZeitverwaltungController.java:611`), ruft anschließend pro Mitarbeiter
  `getOrCreateZeitkonto()` (Zeile 615) — dieser Aufruf legt aktuell beim
  bloßen Öffnen der Zeitkonten-Seite ein 40-Stunden-Konto für den
  System-Mitarbeiter an (Details in Abschnitt 12).
- `WebPushService.java:296` — `findAll()`.
- `BelegService.java:1226` — `findAll()`.

**Zusätzlich verifiziert, in keinem der vier Issues genannt:**
`mitarbeiterRepository.findByAktivTrue()` wird an zwei weiteren Stellen
aufgerufen, die von der Migration "`aktiv` zurück auf 1" für den
System-Mitarbeiter (Abschnitt 11) unmittelbar betroffen sind:

- `VerrechnungslohnService.java:137` — die Jahres-Lohnkosten-/
  Stundenreport-Berechnung (mündet in "Selbstkosten pro Stunde") läuft über
  `aktive` Mitarbeiter und rechnet für jeden eine Lohn- und Stundenzeile
  (`berechneLohnZeile`, `berechneStundenZeile`), ohne Sonderbehandlung.
  Sobald der System-Mitarbeiter wieder `aktiv=1` ist, fließt er ungefiltert
  in diese Summen ein.
- `SteuerberaterEmailProcessingService.java:449` und `:515` — ordnen
  eingehende Steuerberater-E-Mails/-Dateinamen per Name-Matching einem
  aktiven Mitarbeiter zu (`findMitarbeiterByName`,
  `findMitarbeiterFromFilename`). Ein aktiver Datensatz mit Vor-/Nachname
  "System"/"Webseite" kann hier theoretisch fehlzugeordnet werden.

**Einschätzung des Auftraggebers zu diesen beiden Stellen:** ja, auch sie
sollten auf `art = MENSCH` filtern — ein System-Datensatz in der
Lohnkosten-Berechnung ist dieselbe Fehlerklasse wie einer in der
Zeiterfassung. Formal bleibt das ein Punkt für den Grobplan (siehe Offene
Punkte), aber mit dieser Empfehlung.

`MitarbeiterDto` bekommt `art` mit, damit auch Stellen, die bewusst über
alle Mitarbeiter listen, System-Datensätze erkennen und ausblenden können.

`AnfrageFunnelService` (verifiziert: Konstante
`SYSTEM_MITARBEITER_TOKEN = "__SYSTEM_FUNNEL__"`, Lookup über
`findByLoginToken`, kein `aktiv`-Filter) ist von der Umstellung nicht
betroffen — er filtert schon heute nicht nach `aktiv`.

### 7. Zeitkonto ja/nein je Mitarbeiter (#95)

Neue Spalte `mitarbeiter.fuehrt_zeitkonto` — `BOOLEAN NOT NULL DEFAULT
TRUE`. Der Spalten-Default deckt Bestandsdaten ab, kein bestehendes
Zeitkonto ändert sich dadurch. Ein Betrieb stellt Geschäftsführer & Co.
bewusst und einzeln um — **kein** automatisches Ableiten aus
`istGeschaeftsfuehrer` oder `beschaeftigungsart`. Beide Felder beantworten
andere Fragen (Lohnseite bzw. SV-Sätze in der Lohnberechnung) und sagen
nichts über die Wochenverteilung der Arbeitszeit; auch ein Bürokaufmann in
Vertrauensarbeitszeit kann ohne Zeitkonto geführt werden, umgekehrt gibt es
Geschäftsführer, die ihre Zeit erfassen.

Feld `fuehrtZeitkonto` in `Mitarbeiter`, `MitarbeiterDto` und
`MitarbeiterErstellenDto`
(`src/main/java/org/example/kalkulationsprogramm/dto/Mitarbeiter/`).

UI im `MitarbeiterEditor`
(`react-pc-frontend/src/pages/MitarbeiterEditor.tsx`, 1392 Zeilen): ein
Schalter neben dem vorhandenen "Aktiv"-Schalter (verifiziert Zeilen
1146–1152: Checkbox `formData.aktiv` mit Label "Aktiv" und Hilfetext).
Handwerker-Sprache **"Arbeitszeit erfassen"**, nicht "führt Zeitkonto".
Hilfetext sinngemäß: aus, wenn diese Person nicht stempelt (z. B. Chef) —
dann kein Zeitkonto, keine Über-/Minusstunden.

### 8. Arbeitszeit-Vorlagen als eigener Katalog (#96)

Neue Tabelle `zeitkontenmodell` — Bezeichnung (freier Text, kein Bezug zu
`Abteilung`), die sieben Wochentags-Sollstunden, `buchung_start_zeit` /
`buchung_ende_zeit`. Java-Entity `Zeitkontenmodell`, eigenes Repository
`ZeitkontenmodellRepository`. Inhaltlich das, was heute in `Zeitkonto`
(`src/main/java/org/example/kalkulationsprogramm/domain/Zeitkonto.java`,
106 Zeilen) je Mitarbeiter steht, hier aber einmal je Modell statt einmal
je Person.

Bewusst **kein** Bezug zur `Abteilung` (siehe Nicht-Ziele) und **kein**
Bezug zu `Beschaeftigungsart` — Letzteres steuert laut Klassenkommentar
ausschließlich, welche SV-Sätze in der Lohnberechnung greifen, und
beantwortet nichts zur Verteilung der Arbeitszeit auf Wochentage. Zwei
Mitarbeiter mit identischem `MINIJOB`-Status können völlig unterschiedliche
Tagesverteilungen haben.

Die Zuweisung passiert am Mitarbeiter, nicht an der Abteilung: eine
`zeitkonto_version` (Abschnitt 10) trägt optional eine Herkunfts-Referenz
auf `zeitkontenmodell`. Welches Modell ein Mitarbeiter "gerade hat", ergibt
sich aus der `vorlage_id` seiner aktuell offenen Version — es gibt keine
zusätzliche, separate Zuordnungsspalte am Mitarbeiter.

UI: Vorlagen als eigener Abschnitt der Zeitkonten-Seite
(`react-pc-frontend/src/pages/ZeiterfassungZeitkonten.tsx`, 200 Zeilen) —
anlegen, umbenennen, Wochentage setzen. Am Mitarbeiter auswählbar
(`MitarbeiterEditor.tsx`). Handwerker-Sprache: **"Arbeitszeit-Vorlage"**,
nicht "Zeitkontenmodell".

### 9. Vorlage wird beim Zuweisen kopiert, nicht referenziert

Kernentscheidung, die #93 und #96 zusammenhält: Weist das Büro einem
Mitarbeiter eine Vorlage zu, entsteht eine `zeitkonto_version` mit eigenen,
kopierten Stundenwerten. `vorlage_id` dokumentiert nur die Herkunft, ist
danach keine lebende Verbindung mehr.

Begründung (wörtlich aus dem Brainstorming): Bei der Kopie muss man aktiv
etwas tun, damit eine Vorlagenänderung greift; bei einer Referenz müsste man
aktiv etwas tun, damit sie **nicht** zu weit greift. Die Kopie verzeiht
Fehler, die Referenz bestraft sie.

Daraus folgt unmittelbar: Eine individuelle Abweichung vom Modell ist kein
Sonderfall mit eigenen Spalten, sondern derselbe Mechanismus wie jede andere
Version — ihre Stundenwerte weichen einfach von dem ab, was
`zeitkontenmodell` unter derselben `vorlage_id` aktuell trägt. Ob eine
Version "abweicht", ergibt sich deshalb aus einem Wertevergleich zur
Anzeige-/Abfragezeit (Versionsspalten gegen die aktuellen
`zeitkontenmodell`-Spalten der referenzierten `vorlage_id`) — kein
gespeichertes Abweichungs-Flag, das wäre genau der Sonderfall, den diese
Entscheidung vermeiden soll. In der Oberfläche sichtbar als "weicht von
*Werkstatt* ab" (#96).

Ändert das Büro eine Vorlage nachträglich, kommt die Rückfrage "für wen ab
wann übernehmen?" — für die ausgewählten Mitarbeiter und ab dem gewählten
Stichtag entstehen neue Versionen; bestehende (auch bereits geschlossene)
Versionen bleiben unberührt. Welche Mitarbeiter für diese Rückfrage
überhaupt zur Auswahl stehen (nur wertgleich Folgende oder auch bereits
individuell Abweichende), legt das Brainstorming nicht fest (siehe Offene
Punkte).

### 10. Zeitkonto historisieren (#93)

Neue Tabelle `zeitkonto_version` löst `zeitkonto` **vollständig ab** (nicht
nur zusätzlich): `mitarbeiter_id`, `gueltig_von` (DATE, NOT NULL),
`gueltig_bis` (DATE, NULL = offen/aktuell), dieselben sieben
Wochentagsspalten wie `zeitkontenmodell`, `buchung_start_zeit` /
`buchung_ende_zeit`, `vorlage_id` (nullable FK auf `zeitkontenmodell`, nur
Herkunft, siehe Abschnitt 9). Java-Entity `ZeitkontoVersion`, eigenes
Repository `ZeitkontoVersionRepository`.

**Bewusst verworfen** gegenüber dem ursprünglichen Vorschlag in #93: dort
sollte `zeitkonto` als Sicht auf die jeweils aktuelle Version bestehen
bleiben, damit vorhandene Aufrufer unverändert weiterlaufen. Das ist
verworfen — zwei Quellen (Tabelle und Sicht), die bei einem Bug
auseinanderlaufen können, sind schlechter als eine Quelle mit durchgezogener
Migration aller Aufrufer.

Überlappungen und Lücken werden beim Speichern abgelehnt, mit 409 Conflict
nach dem im Projekt etablierten Muster für Konflikt-Antworten (z. B.
`throw new ResponseStatusException(HttpStatus.CONFLICT, ...)` in
`LieferantenController.java:173`, oder
`ResponseEntity.status(HttpStatus.CONFLICT)` in
`AusgangsGeschaeftsDokumentController.java:221`). Beim Anlegen einer neuen
Version wird die bisherige offene Vorgängerversion automatisch auf
`gueltig_bis = neue.gueltig_von - 1 Tag` gesetzt — es gibt keinen Weg, eine
Version rückwirkend in der Mitte der Historie einzufügen oder eine bereits
geschlossene Version zu bearbeiten; jede Änderung ist ein neuer, nach vorn
gerichteter Stichtag (konsistent mit Abschnitt 14: abgeschlossene Monate
bleiben in jedem Fall unberührt).

### 11. Migration der Bestandsdaten

Eine Flyway-Migration, `V368__*.sql` (nächste freie Nummer, verifiziert per
`ls src/main/resources/db/migration/ | sort -V | tail`: höchste vorhandene
ist `V367__langzeitkrankmeldung.sql`):

1. `mitarbeiter.art` anlegen, Default `MENSCH`.
2. `mitarbeiter.fuehrt_zeitkonto` anlegen, Default `TRUE`.
3. `zeitkontenmodell` anlegen — bleibt leer, **keine** Vorlagen automatisch
   erzeugen. Der Betrieb legt sie selbst an.
4. `zeitkonto_version` anlegen.
5. Für jeden bestehenden `zeitkonto`-Satz eine erste Version erzeugen:
   `gueltig_von = mitarbeiter.eintrittsdatum`, ersatzweise das früheste
   Buchungsdatum, `gueltig_bis = NULL`, `vorlage_id = NULL` (keine Vorlage
   nachträglich unterstellen).
6. System-Mitarbeiter (`login_token = '__SYSTEM_FUNNEL__'`, angelegt in
   `V221__system_mitarbeiter_webseite.sql`): `art = 'SYSTEM'`, `aktiv`
   zurück auf `1`, sein versehentlich angelegtes `zeitkonto` löschen (keine
   Version dafür anlegen).
7. `zeitkonto` (Tabelle) entfernen. Verifiziert unbedenklich: keine andere
   Tabelle/Entity hat eine Fremdschlüsselbeziehung auf `zeitkonto` — auch
   `ZeitkontoKorrektur` referenziert `mitarbeiter_id` direkt, nicht
   `zeitkonto_id` (`domain/ZeitkontoKorrektur.java:31`).
8. `monats_saldo.festgeschrieben` (Default `FALSE`), `festgeschrieben_am`,
   `festgeschrieben_von_mitarbeiter_id` anlegen (E9, Abschnitt 1) — kein
   bestehender Monat wird durch die Migration festgeschrieben, das
   Abschließen ist ein bewusster Akt danach.
9. `abteilung.darf_monat_abschliessen` anlegen, Default `FALSE` (E13,
   Abschnitt 5) — nach der Migration darf zunächst keine Abteilung
   abschließen, der Betrieb schaltet gezielt frei.

**Härtestes Abnahmekriterium** (wörtlich aus dem Brainstorming): kein
vorhandener Monatssaldo darf sich durch die Migration ändern.

### 12. Kein stillschweigendes Zeitkonto mehr

`ZeitkontoService.getOrCreateZeitkonto()`
(`src/main/java/org/example/kalkulationsprogramm/service/ZeitkontoService.java:32`)
entfällt ersatzlos — heute legt sie beim ersten Zugriff ein Zeitkonto mit
den `Zeitkonto`-Defaults (8 h Mo–Fr, `Zeitkonto.java:37-58`) an, ein GET
kann damit eine Zeile in die Datenbank schreiben. An ihre Stelle treten zwei
rein lesende Methoden auf `ZeitkontoService`:

- `versionAm(mitarbeiterId, tag)` — die an einem Tag gültige Version.
- `versionenImZeitraum(mitarbeiterId, von, bis)` — alle Versionen, die einen
  Zeitraum berühren.

Beide liefern leer, wenn nichts hinterlegt ist. Kein GET schreibt danach
mehr in die Datenbank. Wer keine Vorlage zugewiesen bekommen hat, hat
**kein** Soll — nicht 40 Stunden. Weil das einen stillen Fehler (falsches
Soll) gegen einen anderen (unsichtbares Soll) tauschen würde, **muss** die
Oberfläche "noch keine Arbeitszeit hinterlegt" sichtbar anzeigen, wo bisher
unbemerkt 40 Stunden unterstellt wurden.

Verifiziert 16 Aufrufstellen von `getOrCreateZeitkonto`
(`grep -rn 'getOrCreateZeitkonto' src/main/java`, Treffer inklusive der
Methode selbst), die umziehen und "kein Zeitkonto" (leeres `Optional`)
ausdrücklich behandeln müssen:

| Datei | Zeile(n) | Aufrufe |
| --- | --- | --- |
| `controller/ZeitverwaltungController.java` | 442, 615 | 2 |
| `service/ZeiterfassungApiService.java` | 1094, 1153, 1184 | 3 |
| `service/MonatsSaldoService.java` | 133 | 1 |
| `service/LangzeitkrankmeldungService.java` | 315, 360, 486, 652 | 4 |
| `service/AbwesenheitService.java` | 60 | 1 |
| `service/UrlaubsantragService.java` | 108 | 1 |
| `service/ZeitkontoService.java` (intern) | 67, 83, 97 | 3 |

Was genau jede einzelne Stelle bei leerem `Optional` tut (Fehler ablehnen, 0
Sollstunden werten, Warnung anzeigen), legt das Brainstorming nicht pro
Stelle fest — Aufgabe des Grobplans (siehe Offene Punkte). Erkennbar aus dem
bestehenden Code: `AbwesenheitService.java` (um Zeile 60) und
`UrlaubsantragService.java` (Zeile 108) berechnen Sollstunden für einen
konkreten Tag/Zeitraum, um eine Abwesenheit/einen Urlaubsantrag zu buchen —
ohne Zeitkonto gibt es dort fachlich nichts zu buchen.

`MonatsSaldoWarmupService`
(`src/main/java/org/example/kalkulationsprogramm/service/MonatsSaldoWarmupService.java`,
läuft über `findByAktivTrue()`) überspringt Mitarbeiter ohne Zeitkonto,
statt für sie ein Minus aus nicht gestempelten Sollstunden aufzubauen — und
überspringt zusätzlich festgeschriebene Monate (Abschnitt 1).

### 13. TagesSollService: Version statt Zeitkonto

`TagesSollService.tagesBasis()` (`service/TagesSollService.java:189`)
bekommt statt eines `Zeitkonto`-Objekts die am jeweiligen Tag gültige
`ZeitkontoVersion`. Die Batch-Methoden `periodenSollJeTag`,
`feiertagsGutschriftJeTag`, `arbeitsSollJeTag` und die private
`jeTag`/`summiere`-Grundlage (`TagesSollService.java:92-125`) laden die
Versionen **einmal je Zeitraum** über `versionenImZeitraum` und wählen darin
je Schleifentag die passende aus — genau die Eigenschaft, die #91 dort schon
für Phasen und Feiertage eingeführt hat (Javadoc-Referenz "Befund 2,
Abschnitt 4": 249 statt 2 Repository-Aufrufe für einen 31-Tage-Monat). Diese
Eigenschaft darf durch die Umstellung nicht verloren gehen — ein naiver
`versionAm`-Aufruf pro Tag innerhalb der Schleife würde sie wieder
zerstören.

Randbedingung aus der bestehenden Abhängigkeitsrichtung: `ZeitkontoService`
hängt bereits heute von `TagesSollService` ab (`private final
TagesSollService tagesSollService;` in `ZeitkontoService.java`). Damit
`TagesSollService` nicht umgekehrt von `ZeitkontoService` abhängen muss
(zyklische Spring-Abhängigkeit), liest `TagesSollService` die Version(en)
über eine eigene Repository-Abhängigkeit (`ZeitkontoVersionRepository`),
nicht über `ZeitkontoService.versionAm`/`versionenImZeitraum` — dasselbe
Muster, das `TagesSollService` heute schon für
`LangzeitkrankmeldungPhaseRepository` nutzt (direkter Repository-Zugriff
statt über einen Zwischen-Service).

Ohne laufende Zeitkonto-Historie (nur eine Version, wie nach der Migration
für jeden Bestandsmitarbeiter) muss das Ergebnis bitgleich zum heutigen
Stand bleiben: die `TagesSollCharakterisierung*Test`-Tests aus #91 laufen
unverändert grün.

### 14. Absicherung gegen rückwirkende Verfälschung

Heute wirft `ZeitverwaltungController.updateZeitkonto()`
(`ZeitverwaltungController.java:664`, Kommentar Zeile 663 "Sollstunden haben
sich geändert → ALLE MonatsSaldo-Caches invalidieren") mit
`monatsSaldoService.invalidiereAlle(mitarbeiterId)`
(`MonatsSaldoService.java:225-226`, delegiert an
`MonatsSaldoRepository.invalidiereAlle`, Zeile 66) **jeden** gespeicherten
Monat weg.

Ursprünglich war hier ein neues, engeres `invalidiereAbMonat(mitarbeiterId,
jahr, monat)` vorgesehen, das nur ab einem Stichtag neu rechnet. **Das
entfällt** (Folge aus Abschnitt 1, E9): Der Aufruf an dieser Stelle bleibt
unverändert `invalidiereAlle`. Der Schutz kommt stattdessen strukturell aus
dem Monatsabschluss — alle `invalidiere*`-Methoden überspringen
festgeschriebene Monate (Abschnitt 1). Ein Zeitkonto-Wechsel, eine
Vorlagenänderung, ein künftiger Bugfix im Rechenkern: nichts davon kann
einen abgeschlossenen Monat noch anfassen, unabhängig davon, wie sorgfältig
der jeweilige Aufrufer war — die Absicherung liegt jetzt beim Abschluss,
nicht bei der Sorgfalt des Aufrufers.

Für noch nicht abgeschlossene Monate bleibt es dabei, dass `invalidiereAlle`
sie sämtlich neu rechnet, auch weit vor dem eigentlichen Stichtag der
Änderung. Das ist unschädlich: Die Historisierung (Abschnitt 10) sorgt
dafür, dass jeder dieser Monate gegen das damals gültige Wochenmodell
gerechnet wird — eine Neuberechnung eines offenen Altmonats sollte
zahlenmäßig unverändert bleiben, selbst wenn sie technisch stattfindet.

Damit ergeben sich zwei getrennte Absicherungen, die zusammen rückwirkende
Verfälschung strukturell unmöglich machen, nicht nur unwahrscheinlich:

1. **Die Historisierung** (Abschnitt 10) sorgt dafür, dass eine
   Neuberechnung eines noch offenen Altmonats auf die damals gültige
   Version trifft, nicht auf die heutige.
2. **Der Monatsabschluss** (Abschnitt 1) sorgt dafür, dass ein bereits
   geprüfter Monat überhaupt nicht neu gerechnet wird — unabhängig vom
   Auslöser und unabhängig davon, ob dessen Autor an diesen Fall gedacht
   hat.

Auch ein Bug in der einen Absicherung liefe noch gegen die andere.

### 15. Der Wechsel-Dialog

Beim Anlegen einer neuen Zeitkonto-Version für einen Mitarbeiter (Wechsel
der Vorlage oder individuelle Anpassung) zeigt ein Dialog:

- **Vor dem Speichern:** was passieren wird — insbesondere, dass "ab wann"
  die eigentliche Entscheidung ist, kein Formularfeld nebenbei. Der Nutzer
  soll erkennen, dass bereits abgeschlossene Monate (Abschnitt 1) in jedem
  Fall unverändert bleiben, dass alle noch offenen Monate neu gerechnet
  werden, und dass sich davon nur die Monate ab dem gewählten Stichtag
  zahlenmäßig ändern sollten — offene Monate davor rechnen dank der
  Historisierung (Abschnitt 10) weiterhin gegen ihr damals gültiges Modell
  (Gulf of Execution).
- **Nach dem Speichern:** was passiert ist — welche Monate neu gerechnet
  wurden, wie sich der Saldo dadurch verschiebt, und ausdrücklich, welche
  Monate abgeschlossen und damit garantiert unverändert geblieben sind
  (Gulf of Evaluation).

Keine stille Umstellung, kein Saldo, der sich unerklärt bewegt. Derselbe
Dialog-Gedanke gilt sinngemäß für die "für wen ab wann übernehmen?"-
Rückfrage beim Ändern einer Vorlage (Abschnitt 9), nur für mehrere
Mitarbeiter auf einmal statt für einen.

### 16. Frontend: Wording und betroffene Seiten

Durchgehend Handwerker-Sprache (Projektregel):

| Fachbegriff | UI-Text |
| --- | --- |
| Zeitkontenmodell | Arbeitszeit-Vorlage |
| führt Zeitkonto | Arbeitszeit erfassen |
| gueltig_von | gültig ab |
| Monat festschreiben | Monat abschließen |
| Monat entsperren | Monat wieder öffnen |
| darf Monat abschließen | Monate abschließen |

Betroffen:

- `react-pc-frontend/src/pages/ZeiterfassungZeitkonten.tsx` (200 Zeilen) —
  bekommt die Vorlagen-Verwaltung (anlegen, bearbeiten, Wochentage setzen)
  und je Mitarbeiter die Versionsliste statt eines einzelnen Formulars, mit
  dem Wechsel-Dialog aus Abschnitt 15.
- `react-pc-frontend/src/pages/ZeiterfassungSteuerberater.tsx` (355 Zeilen)
  — iteriert heute über alle Zeitkonten (`loadStundenDaten`, Zeile 63, ruft
  je geladenem Konto den Kalender ab); läuft nach der Umstellung über
  `art = MENSCH`-Mitarbeiter mit Zeitkonto, nicht mehr über die alte
  `Zeitkonto`-Liste.
- `react-pc-frontend/src/pages/MitarbeiterEditor.tsx` (1392 Zeilen) —
  Schalter "Arbeitszeit erfassen" (Abschnitt 7), Vorlagen-Auswahl, Liste der
  Zeitabschnitte (Versionen) mit "gültig ab", "weicht von *Vorlage* ab"-
  Kennzeichnung (Abschnitt 9).
- `react-pc-frontend/src/pages/ZeiterfassungKalender.tsx` (1417 Zeilen) —
  zeigt schon heute den Jahressaldo; bekommt die "geprüft bis …,
  vorläufig"-Kennzeichnung aus Abschnitt 2 für alle noch nicht
  abgeschlossenen Monate.
- `react-pc-frontend/src/components/layout/NotificationBell.tsx` (538
  Zeilen) und `notification-helpers.ts` (229 Zeilen) — neue Kategorie
  "Monat abzuschließen" (Abschnitt 4), inklusive Icon-/Gruppen-Zuordnung
  und Tests (`NotificationBell.test.ts`, `NotificationBell.render.test.tsx`).
- `react-pc-frontend/src/pages/AbteilungBerechtigungenEditor.tsx` — neuer
  Schalter "Monate abschließen" (Abschnitt 5).
- Neu (Platzierung offen, siehe Offene Punkte): Bedienelemente zum
  Abschließen/Wieder-Öffnen eines Monats mit Schloss-Symbol (Abschnitt 1),
  nur sichtbar/wirksam mit der Berechtigung aus Abschnitt 5.

### 17. Mobile App (react-zeiterfassung)

Für Mitarbeiter ohne Zeitkonto (`fuehrtZeitkonto = false` oder keine
gültige Version) verschwinden nur Stempeluhr und Saldo — verifiziert
betroffen: `react-zeiterfassung/src/pages/ZeiterfassungPage.tsx`
(Stempeluhr) und `react-zeiterfassung/src/pages/SaldenPage.tsx` (Saldo),
beide über die Kachel-Navigation in `DashboardPage.tsx` erreichbar
(verifiziert: Kachel "Saldenauswertung" Zeile 1233). Der Zugang zu
Projekten, Belegen und Notizen bleibt unverändert (u. a. `ProjektePage.tsx`,
`BelegScannerPage.tsx`, `ProjektNotizenPage.tsx`, `AnfrageNotizenPage.tsx`)
— so in #95 vorgeschlagen und vom Nutzer bestätigt, weil die App auch für
diese Bereiche genutzt wird.

Für Mitarbeiter **mit** Zeitkonto gilt zusätzlich Abschnitt 2 (E11):
`SaldenPage.tsx` und `DashboardPage.tsx` zeigen Saldo und Urlaubsanspruch
immer live, inklusive des laufenden und jedes noch offenen Monats, mit der
"geprüft bis …, vorläufig"-Kennzeichnung für den nicht abgeschlossenen
Anteil.

## Betroffene Bereiche

**Backend** (`src/main/java/org/example/kalkulationsprogramm/`):

- Neu: `domain/MitarbeiterArt.java`, `domain/Zeitkontenmodell.java`,
  `domain/ZeitkontoVersion.java`, `repository/ZeitkontenmodellRepository.java`,
  `repository/ZeitkontoVersionRepository.java`.
- Entfällt: `domain/Zeitkonto.java`, `repository/ZeitkontoRepository.java`.
- Geändert: `domain/Mitarbeiter.java` (+`art`, +`fuehrtZeitkonto`),
  `repository/MitarbeiterRepository.java` (Filter auf `art`),
  `dto/Mitarbeiter/MitarbeiterDto.java` (+`art`, +`fuehrtZeitkonto`),
  `dto/Mitarbeiter/MitarbeiterErstellenDto.java` (+`fuehrtZeitkonto`).
- Geändert: `domain/MonatsSaldo.java` (+`festgeschrieben`,
  +`festgeschriebenAm`, +`festgeschriebenVon`), `service/MonatsSaldoService.java`
  (die fünf `invalidiere*`-Methoden überspringen festgeschriebene Monate,
  `getOrBerechne` gibt festgeschriebene Salden unverändert zurück, neue
  Methoden zum Abschließen/Öffnen mit Protokollierung und
  Berechtigungsprüfung), `repository/MonatsSaldoRepository.java` (die drei
  `@Modifying`-Queries `invalidiere`/`invalidiereJahr`/`invalidiereAlle`,
  Zeilen 50-66, nehmen festgeschriebene Zeilen aus),
  `service/MonatsSaldoWarmupService.java` (überspringt zusätzlich
  festgeschriebene Monate).
- Geändert: `domain/Abteilung.java` (+`darfMonatAbschliessen`),
  `dto/AbteilungBerechtigungDto.java` (beide geschachtelten Klassen
  `Response`/`UpdateRequest` +Feld), `controller/AbteilungBerechtigungController.java`
  (Abschnitt 5).
- Geändert: `controller/NotificationController.java` (neue Kategorie
  "Monat abzuschließen", eine indexgestützte Abfrage über alle Mitarbeiter
  statt einer Schleife, personalisiert über die Abteilungs-Berechtigung —
  siehe Abschnitt 4/5).
- Geändert: `service/ZeitkontoService.java` (kompletter Umbau:
  `getOrCreateZeitkonto` entfällt, `versionAm`/`versionenImZeitraum` neu,
  Vorlagen-Zuweisung, Modelländerungs-Rückfrage),
  `service/TagesSollService.java` (Parametertyp `Zeitkonto` →
  `ZeitkontoVersion`, versionsbewusste Tagesauflösung),
  `service/MitarbeiterService.java` (`list()` filtert auf `art = MENSCH`),
  `service/ZeiterfassungApiService.java` (3 Stellen),
  `service/LangzeitkrankmeldungService.java` (4 Stellen),
  `service/AbwesenheitService.java`, `service/UrlaubsantragService.java`,
  `service/WebPushService.java` (Zeile 296), `service/BelegService.java`
  (Zeile 1226).
- Zu prüfen, ob zusätzlich auf `art = MENSCH` umzustellen (siehe Offene
  Punkte, mit Empfehlung "ja"): `service/VerrechnungslohnService.java`
  (Zeile 137), `service/SteuerberaterEmailProcessingService.java` (Zeilen
  449, 515).
- Geändert: `controller/ZeitverwaltungController.java`
  (`getOrCreateZeitkonto`-Aufrufe in `getKalender()` Zeile 442 und
  `getAlleZeitkonten()` Zeilen 611/615, `invalidiereAlle`-Aufruf in
  `updateZeitkonto()` Zeile 664 bleibt bestehen); neuer Wechsel-Dialog-
  Endpoint für Vorher-/Nachher-Vorschau kommt hinzu; neue Endpoints zum
  Abschließen/Öffnen eines Monats (mit Berechtigungsprüfung); ggf. neuer
  Controller für die Vorlagenpflege (offen).
- Nicht geändert, nur referenziert: `domain/Lohnabrechnung.java`
  (fachlicher Anker für den Monatsabschluss, siehe Abschnitt 1 und Offene
  Punkte — keine Code-Kopplung in diesem Vorhaben, sofern der Grobplan
  nicht anders entscheidet).
- Neu: `src/main/resources/db/migration/V368__*.sql`.
- Tests: `TagesSollCharakterisierung*Test` (aus #91) bleiben grün; neue
  Tests für Historisierung, `art`-Filterung, `fuehrtZeitkonto`-Umschaltung,
  Vorlagen-Zuweisung/-Abweichung/-Änderung, Monatsabschluss/-Öffnung samt
  Berechtigungsprüfung, eingefrorene Abwesenheitsstunden nach
  Zeitkonto-Wechsel, neue Benachrichtigungs-Kategorie — mit Dummy-Daten
  (`Max Mustermann`).

**Frontend Desktop** (`react-pc-frontend/`): `src/pages/ZeiterfassungZeitkonten.tsx`,
`src/pages/ZeiterfassungSteuerberater.tsx`, `src/pages/MitarbeiterEditor.tsx`,
`src/pages/ZeiterfassungKalender.tsx`,
`src/components/layout/NotificationBell.tsx`,
`src/components/layout/notification-helpers.ts`,
`src/pages/AbteilungBerechtigungenEditor.tsx` (siehe Abschnitt 16).

**Frontend Mobile** (`react-zeiterfassung/`): `src/pages/ZeiterfassungPage.tsx`,
`src/pages/SaldenPage.tsx`, `src/pages/DashboardPage.tsx` (siehe
Abschnitt 17).

**Nicht betroffen im Sinn der Arbeitszeit-Zuordnung, aber mit einer
Ausnahme:** `AbteilungRepository`, `ArbeitsgangController` bleiben
vollständig unverändert; `Abteilung`/`AbteilungBerechtigungDto`/
`AbteilungBerechtigungController` sind **nicht** unangetastet — sie
bekommen das neue Berechtigungs-Flag `darfMonatAbschliessen` (Abschnitt 5,
siehe Nicht-Ziele für die Abgrenzung).

## Akzeptanzkriterien

Eingesammelt aus den vier Issues, entdoppelt (Migrationssicherheit wird in
den Issues mehrfach einzeln formuliert, hier einmal als gemeinsames
Kriterium), ergänzt um die Punkte aus dem Brainstorming zu Monatsabschluss,
eingefrorenen Abwesenheitsstunden, UI-Sichtbarkeit, Neuberechnung, Dialog,
Erinnerungsfunktion und Berechtigung.

**Monatsabschluss (E9/E10/E11):**

- [ ] Ein Monat lässt sich abschließen und wieder öffnen; beides ist
      protokolliert (wer/wann).
- [ ] Ein abgeschlossener Monat ändert sich nicht mehr — weder durch einen
      Zeitkonto-Wechsel, eine Vorlagenänderung, eine allgemeine
      Neuberechnung noch durch den Warmup.
- [ ] In der Handy-App sind Stundensaldo und Urlaubsanspruch auch dann
      vollständig sichtbar, wenn für den laufenden Monat (und beliebig
      viele davor) noch kein Abschluss erfolgt ist; der vorläufige Anteil
      ist als solcher gekennzeichnet (z. B. "geprüft bis …, vorläufig").
- [ ] Kein Bildschirm verweigert die Saldo-Anzeige mit Verweis auf einen
      fehlenden Monatsabschluss.
- [ ] Eingefrorene Abwesenheitsstunden (`abwesenheit.stunden`) werden durch
      keine Neuberechnung verändert — Test mit Dummy-Daten
      (`Max Mustermann`), der einen Zeitkonto-Wechsel nach Urlaubsbuchung
      simuliert und belegt, dass weder die Urlaubsstunden noch (nach
      Abschluss) der Gesamtsaldo davon betroffen sind.
- [ ] Tests mit Dummy-Daten (`Max Mustermann`) für Abschließen, Öffnen und
      die Sichtbarkeits-Garantie.

**Erinnerung an den Monatsabschluss (E12):**

- [ ] Ist ein vergangener Monat nicht abgeschlossen, erscheint in der
      Benachrichtigungs-Glocke ein Hinweis "Monat abzuschließen" mit dem
      betroffenen Monat.
- [ ] Nach dem Abschluss dieses Monats verschwindet der Hinweis.
- [ ] Der laufende Monat löst keinen Hinweis aus.
- [ ] Test mit Dummy-Daten (`Max Mustermann`).

**Berechtigung für den Monatsabschluss (E13):**

- [ ] Ein Mitarbeiter ohne `darfMonatAbschliessen` kann einen Monat weder
      abschließen noch wieder öffnen — auch nicht durch direkten Aufruf des
      Endpoints — und bekommt keinen Glocken-Hinweis.
- [ ] Ein Mitarbeiter mit `darfMonatAbschliessen` kann beides.
- [ ] Test mit Dummy-Daten (`Max Mustermann`).

**Historisierung (#93):**

- [ ] Ein Mitarbeiter kann mehrere Zeitkonto-Versionen mit disjunkten,
      lückenlosen Gültigkeitszeiträumen haben.
- [ ] Für einen Tag vor einem Modellwechsel liefert die Sollstunden-
      Berechnung das alte Soll, für einen Tag danach das neue.
- [ ] Ein Monatssaldo vor einem Modellwechsel rechnet gegen das damals
      gültige Wochenmodell.
- [ ] `TagesSollCharakterisierung*Test` (aus #91) läuft unverändert grün,
      solange nur eine Version existiert.
- [ ] Überlappende oder lückenhafte Zeiträume werden mit 409 abgelehnt.

**System-Mitarbeiter (#94):**

- [ ] `GET /api/mitarbeiter` liefert keinen Datensatz mit `art = SYSTEM`
      mehr.
- [ ] `GET /api/zeitverwaltung/zeitkonten` liefert keinen System-Mitarbeiter
      und legt beim Aufruf kein Zeitkonto mehr an.
- [ ] Der Steuerberater-Export enthält keinen System-Mitarbeiter.
- [ ] Ein bereits angelegtes Zeitkonto des System-Mitarbeiters ist nach der
      Migration entfernt.
- [ ] Der Webseiten-Funnel legt weiterhin Anfragen mit dem System-Mitarbeiter
      als Ersteller an (`AnfrageFunnelServiceTest` bleibt grün), auch
      nachdem `aktiv` wieder auf `1` steht.
- [ ] Ein ausgeschiedener echter Mitarbeiter (`aktiv=0`, `art=MENSCH`)
      bleibt in Historie und Auswertung sichtbar.

**Zeitkonto ja/nein (#95):**

- [ ] Im Mitarbeiterstamm lässt sich je Mitarbeiter einstellen, ob
      Arbeitszeit erfasst wird.
- [ ] Für `fuehrtZeitkonto = false` wird kein Zeitkonto angelegt — auch
      nicht implizit beim Öffnen der Zeitkonten-Seite.
- [ ] Der Warmup berechnet für ihn keine Monatssalden.
- [ ] Er erscheint nicht in der Zeitkonten-Übersicht und nicht im
      Steuerberater-Export.
- [ ] In der mobilen Zeiterfassung werden ihm keine Stempeluhr und kein
      Saldo angezeigt, andere Bereiche (Projekte, Belege, Notizen) bleiben
      erreichbar.
- [ ] Ein Mitarbeiter lässt sich von "ohne" auf "mit" Zeitkonto umstellen,
      ohne dass Altdaten verloren gehen.

**Arbeitszeit-Vorlagen (#96):**

- [ ] Vorlagen lassen sich anlegen, bearbeiten und einem Mitarbeiter
      zuweisen.
- [ ] Zwei Mitarbeiter derselben Abteilung können unterschiedliche Vorlagen
      haben, ohne die Abteilung zu wechseln und ohne dass sich
      Berechtigungen ändern.
- [ ] Ein Mitarbeiter kann individuell von der zugewiesenen Vorlage
      abweichen; die Abweichung bleibt beim Speichern erhalten und ist in
      der Oberfläche als solche gekennzeichnet ("weicht von … ab").
- [ ] Ein neu angelegter Mitarbeiter bekommt kein stillschweigendes
      40-Stunden-Konto mehr.
- [ ] Das Ändern einer Vorlage verfälscht keine bereits abgerechneten
      Monate — betroffene Mitarbeiter bekommen stattdessen neue Versionen ab
      einem gewählten Stichtag.
- [ ] Beim Löschen einer noch verwendeten Vorlage kommt eine verständliche
      Fehlermeldung statt eines Constraint-Fehlers.

**Migration (gemeinsam für alle vier, härtestes Kriterium):**

- [ ] Kein vorhandener Monatssaldo ändert sich durch die Migration.
- [ ] Bestehende Mitarbeiter stehen nach der Migration auf
      `fuehrtZeitkonto = true`, jeder bestehende `zeitkonto`-Satz hat genau
      eine migrierte, offene Version mit denselben Werten.
- [ ] Kein bestehender Monat ist nach der Migration festgeschrieben.
- [ ] Keine Abteilung darf nach der Migration Monate abschließen, bis der
      Betrieb es gezielt freischaltet.

**Sichtbarkeit und Nachvollziehbarkeit (aus dem Brainstorming ergänzt):**

- [ ] Für einen Mitarbeiter ohne gültige Version zeigt die Oberfläche
      sichtbar "noch keine Arbeitszeit hinterlegt" an, statt stillschweigend
      0 oder ein falsches Soll anzunehmen.
- [ ] Ein Zeitkonto-Wechsel lässt abgeschlossene Monate unverändert; alle
      übrigen Monate werden neu berechnet, wobei sich offene Monate vor dem
      gewählten Stichtag dank Historisierung zahlenmäßig nicht ändern
      sollten.
- [ ] Der Wechsel-Dialog zeigt vor dem Speichern, welche Monate sich ändern
      werden und welche abgeschlossen und damit sicher unverändert sind,
      und nach dem Speichern, welche sich tatsächlich geändert haben.

**Tests (durchgehend, alle Themen):** Dummy-Daten (`Max Mustermann`), keine
echten Mitarbeiterdaten.

## Offene Punkte für den Grobplan

- **Automatische Kopplung Monatsabschluss ↔ Lohnabrechnung:** Wird der
  Abschluss eines Monats an die `Lohnabrechnung`-Entity gekoppelt (z. B.
  automatisch beim Erzeugen einer Lohnabrechnung), oder bleibt er ein
  bewusster, eigener Schritt im Zeitkonten-Bereich? Empfehlung aus dem
  Brainstorming: eigener, bewusster Schritt — eine automatische Kopplung
  würde den Monat zumachen, bevor jemand ihn geprüft hat.
- **UI-Platzierung für Abschließen/Wieder-Öffnen eines Monats:** Auf
  welcher Seite/Ansicht (z. B. `ZeiterfassungZeitkonten.tsx`,
  `ZeiterfassungKalender.tsx` oder eine neue Monatsübersicht) die
  Bedienelemente aus Abschnitt 16 landen, ist nicht festgelegt.
- **Zwei zusätzliche, in keinem der vier Issues genannte
  `findByAktivTrue()`-Aufrufer:** `VerrechnungslohnService.java:137`
  (Jahres-Lohnkosten-/Stundenreport, fließt in "Selbstkosten pro Stunde"
  ein) und `SteuerberaterEmailProcessingService.java:449`/`:515`
  (Name-Matching für eingehende Steuerberater-Post). Empfehlung: ja,
  ebenfalls auf `art = MENSCH` filtern — ein System-Datensatz in der
  Lohnkosten-Berechnung ist dieselbe Fehlerklasse wie einer in der
  Zeiterfassung. Formal zu bestätigen im Grobplan.
- **`fuehrt_zeitkonto` beim Aus-/Wiedereinschalten:** Wird beim Umstellen
  auf "ohne Zeitkonto" die aktuell offene `zeitkonto_version` geschlossen
  (`gueltig_bis` gesetzt), oder bleibt sie unangetastet und wird beim
  Wiedereinschalten einfach weitergelesen? Issue #95 sagt nur, dass keine
  Altdaten verloren gehen dürfen, nicht wie der Zustand dazwischen
  aussieht.
- **`fuehrt_zeitkonto`-Wert für den System-Mitarbeiter selbst:** Die
  Migration sagt "alle übrigen `fuehrt_zeitkonto = TRUE`" — offen, ob der
  System-Mitarbeiter explizit auf `FALSE` gesetzt wird (Verteidigung in der
  Tiefe, falls irgendwo einmal nur nach `fuehrt_zeitkonto` statt nach `art`
  gefiltert wird) oder ob der Spalten-Default `TRUE` für ihn stehen bleibt,
  weil er ohnehin überall über `art` ausgefiltert wird.
- **Migrations-Fallback ohne Eintrittsdatum und ohne Buchung:** Die Regel
  "`gueltig_von = eintrittsdatum`, ersatzweise früheste Buchung" deckt den
  Fall nicht ab, dass beides fehlt (ein `zeitkonto`-Satz ohne
  `eintrittsdatum` und ohne jede `Zeitbuchung`). `MonatsSaldoWarmupService`
  löst denselben Fall heute mit "nichts zu cachen" (Zeile 80) — welchen
  `gueltig_von`-Wert die migrierte Version in diesem Fall bekommt, ist
  offen.
- **Kandidaten-Auswahl für die "für wen ab wann übernehmen?"-Rückfrage**
  beim Ändern einer Vorlage: nur Mitarbeiter, deren aktuelle Version noch
  wertgleich mit der Vorlage ist, oder auch bereits individuell
  Abweichende?
- **Reichweite der Löschsperre einer Vorlage:** Zählt für "noch verwendet"
  (#96-Akzeptanzkriterium) nur eine aktuell offene Version mit passender
  `vorlage_id`, oder jede historische, bereits geschlossene Version, die
  einmal von der Vorlage abstammt? Letzteres würde eine einmal verwendete
  Vorlage praktisch dauerhaft unlöschbar machen.
- **Eigener Controller für die Vorlagenpflege oder Erweiterung von
  `ZeitverwaltungController`** — #96 lässt das selbst offen ("ggf. eigener
  Controller").
- **Optimistisches Sperren (`version`-Spalte) für `zeitkontenmodell` und
  `zeitkonto_version`:** `V364__aggregat_versionsspalten.sql` hat allen 14
  damals verifizierten Aggregate-Root-Entities eine `version`-Spalte
  gegeben (`Mitarbeiter` darunter) — `Zeitkonto` war nicht darunter und hat
  bis heute keine. Beide neuen Tabellen werden voraussichtlich eigenständig
  gespeichert (nicht über den Mitarbeiter-Aggregat kaskadiert), was für
  eine eigene `version`-Spalte nach derselben Konvention spricht. Weder die
  vier Issues noch das Brainstorming erwähnen das.
- **Korrektur einer fehlerhaft angelegten, noch offenen Version:** Lässt
  sich die zuletzt angelegte, noch offene Version bearbeiten oder löschen,
  solange noch keine Monatsberechnung darauf aufsetzt — oder ist jede
  Korrektur ausschließlich über eine weitere neue Version ab einem
  weiteren Stichtag möglich (mit der Folge, dass ein Tippfehler für einen
  Tag in der Historie sichtbar bleibt)?
- **Genauer Umgang mit "kein Zeitkonto" je Aufrufstelle:** Für die 16 in
  Abschnitt 12 gelisteten Stellen legt das Brainstorming nur die
  Rückgabeform (leeres `Optional`) fest, nicht die fachliche Reaktion pro
  Stelle (Fehler ablehnen, 0 Sollstunden werten, Warnung anzeigen).

## Verweise

- #98 — Monatsabschluss (E9, E11, E12, E13) — der erste Baustein dieses
  Vorhabens —
  https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/98
- #93 — Zeitkonto historisieren —
  https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/93
- #94 — System-Mitarbeiter als "System" kennzeichnen —
  https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/94
- #95 — Je Mitarbeiter einstellbar: führt Zeitkonto ja/nein —
  https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/95
- #96 — Zeitkontenmodelle als eigener Katalog (Arbeitszeit-Vorlagen) —
  https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/96
- E10 (eingefrorene Abwesenheitsstunden) — kein eigener Issue-Bezug, reiner
  Befund aus dem Brainstorming, ergänzend zu #98 eingebracht.
- #97 / #91 — Langzeitkrankmeldung, führte `TagesSollService` als zentrale
  Tagessoll-Berechnung ein
  (`docs/superpowers/specs/2026-09-08-langzeitkrankmeldung.md`)
- Projektregeln: `.claude/CLAUDE.md`,
  `docs/agent instructions/docs/BACKEND_ARCH.md` (Constructor Injection,
  Flyway-Versionierung, Named Params, DSGVO-Dummy-Daten in Tests)


## Präzisierungen bei Implementierungsbeginn (09.09.2026)

Die folgenden Regeln konkretisieren offene Punkte und haben bei abweichenden
Detailformulierungen weiter oben Vorrang:

- Der Nutzer hat die Implementierung ausdrücklich freigegeben. Der Ablauf steht
  im zugehörigen Implementierungsplan; Tests und Entscheidungen im Kontext-Log.
- Abschließen ist ein eigener bewusster Schritt, ohne automatische Kopplung an
  die Lohnabrechnung. Regulär sind nur vergangene Monate abschließbar.
- Der Abschluss schützt auch den direkten Cache-Schreibpfad und parallele
  Abschluss-/Cache-Anlage. Bei Randmonaten darf die Gesamtsaldo-Anzeige keinen
  abgeschlossenen Wert durch eine neue Teilmonatsrechnung ersetzen.
- „Geprüft bis“ bezeichnet nur einen lückenlos abgeschlossenen Zeitraum. Ein
  später abgeschlossener Monat darf einen offenen Monat davor nicht verdecken.
- Der Akteur wird aus der angemeldeten Sitzung und ihrem Mitarbeiterbezug
  bestimmt, niemals aus einer frei übermittelten Mitarbeiter-ID. Das Recht
  bleibt standardmäßig aus, auch für Admins. Die Pflege der Rechte bleibt
  serverseitig auf Admins beschränkt.
- Historische Sollberechnung richtet sich nach Zeitabschnitten unabhängig vom
  heutigen Schalter fuehrtZeitkonto. Ausschalten erhält die Historie; erneutes
  Einschalten erfordert eine ausdrückliche neue Arbeitszeitzuweisung. Eine
  Lücke darf nur einen ausdrücklich dokumentierten Zeitraum ohne Konto
  repräsentieren, niemals einen versehentlich fehlenden Vertrag.
- Die Glocke muss auch fällige Monate ohne vorhandene Cachezeile erfassen.
  Der relevante Zeitraum ergibt sich aus Eintritt und tatsächlichen Daten,
  nicht aus einem technischen Migrations-Fallbackdatum.
- Neue Aggregate erhalten optimistisches Sperren. SYSTEM wird zusätzlich
  fuehrtZeitkonto=false gesetzt. Alle menschenbezogenen Auswertungen schließen
  SYSTEM aus, während ausgeschiedene Menschen historisch sichtbar bleiben.
- Migrationen werden für nachvollziehbare Zwischenstände aufgeteilt. Das
  Altmodell bleibt nur vorübergehend während des Umbaus kompilierbar; die
  abschließende Migration entfernt es. Keine Migration setzt automatisch
  Monatsabschlüsse oder ändert vorhandene Saldozahlen.

## Umsetzungsvertrag vom 09.09.2026

Die Implementierung verwendet Migrationen V368–V371; die alte Tabelle wird nach der Kopie entfernt. Offene Monatswerte werden bei Übernahme innerhalb derselben Transaktion neu berechnet. Eine schreibfreie Vorschau verwendet dieselbe Feiertags- und Wiedereingliederungsberechnung. Geschlossene Monate verhindern einen rückwirkenden Wechsel bis zur bewussten Wiederöffnung. Bestehende Abwesenheitsgutschriften werden ausdrücklich unverändert gelassen und in Einzel- und Mehrfachvorschau genannt.

Der Monatsabschluss liegt im Zeitbuchungskalender, erreichbar über den Hinweis im Notification Center. Das Abteilungsrecht „Monate abschließen und wieder öffnen“ steuert die Aktionen und Erinnerungen; die laufende Stunden-/Urlaubsanzeige eingerichteter Konten bleibt davon unabhängig. Es gibt keine automatische Kopplung an einen Lohnexport.

Bei Vorlagenabweichungen bleiben Herkunft und Versionsstand erhalten. Eine geänderte Eingabe verwirft ihre Vorschau; die Übernahme sendet genau die geprüften Werte. Historische Exporte beziehen Arbeitszeitwerte auf den ausgewählten Monat und Feiertagsgutschriften auf die Backend-Berechnung.

Prüfergebnisse, bekannte vorbestehende Testfehler und Reviewkorrekturen stehen im gleichnamigen Kontext-Log unter `docs/superpowers/plans/`.
