# Brainstorming-Ergebnis: Langzeitkrankmeldung (vom Nutzer abgenommen)

Status: Design am 08.09.2026 vom Nutzer freigegeben. Ansatz A.
Zusaetzliche ausdrueckliche Freigabe: Auslagerung des Tages-Solls in einen
neuen `TagesSollService` (Abschnitt 3) — inklusive der Buendelung der heute
dreifach duplizierten Feiertagslogik.

## Problem

Das ERP kennt heute nur die einfache Krankmeldung ueber `AbwesenheitsTyp.KRANKHEIT`.
Ein echter Langzeitfall (Lohnfortzahlung 6 Wochen -> Krankengeld -> stufenweise
Wiedereingliederung) laesst sich nicht abbilden. Besonders die Wiedereingliederung
ist heute unmoeglich: der Mitarbeiter kommt mit z.B. 2 Stunden pro Tag zurueck,
das System rechnet aber weiter mit dem vollen Tagessoll und baut jeden Tag
Minusstunden auf.

## 1. Datenmodell

Neue Entitaet `Langzeitkrankmeldung`:
- Mitarbeiter (Verweis)
- Beginn
- Ende (leer = laeuft noch)
- Status
- lohnfortzahlungBis
- optionale interne Notiz
- Versionsspalte (optimistisches Sperren, passend zum Stand nach V364)

Darunter `LangzeitkrankmeldungPhase`:
- Typ: LOHNFORTZAHLUNG / KRANKENGELD / WIEDEREINGLIEDERUNG
- vonDatum / bisDatum
- stundenProTag — nur bei WIEDEREINGLIEDERUNG gefuellt
Mehrere Phasen hintereinander mit steigenden Stunden ergeben den Stufenplan.

`Abwesenheit` bekommt genau zwei neue Spalten: Verweis auf die Meldung und
Verweis auf die Phase. `AbwesenheitsTyp` bleibt unveraendert — KRANKHEIT bleibt
KRANKHEIT, die Phase ist nur die Verfeinerung. Dadurch bleibt jede heutige
Abfrage gueltig.

Migration: `V367__langzeitkrankmeldung.sql`.

DSGVO: Es wird keine Diagnose gespeichert, auch kein Pflicht-Freitext. Das
Notizfeld wird in der Oberflaeche ausdruecklich beschriftet mit
"interne Notiz — bitte keine Diagnosen eintragen". Damit bleibt das Feature
ausserhalb von Art. 9 DSGVO.

## 2. Der 42-Tage-Countdown

Beim Anlegen setzt das System `lohnfortzahlungBis = Beginn + 41 Tage` und legt
die Lohnfortzahlungs-Phase an. Danach rechnet das System nichts von allein
weiter, sondern zeigt an:
- "Noch 12 Tage Lohnfortzahlung"
- "Lohnfortzahlung endete am 14.03. — auf Krankengeld umstellen?"

Erst der Klick des Buerus legt die Krankengeld-Phase an. Kein Hintergrundjob,
der Zustaende verschiebt. Das Datum ist ueberschreibbar — so traegt das Buero
eine Fortsetzungserkrankung als verkuerzten Zeitraum ein.

## 3. Tages-Soll an einer Stelle (ausdruecklich freigegeben)

Heute fragen acht Stellen einzeln `zeitkonto.getSollstundenFuerTag(...)` ab,
jede mit eigener Feiertagsbehandlung. Der Stufenplan muss aber ueberall gelten,
sonst baut ein Mitarbeiter bei 2 Std/Tag jeden Tag 6 Minusstunden auf.

Neuer `TagesSollService` beantwortet an genau einer Stelle die Frage
"was ist heute das Soll fuer Mitarbeiter X":
- Feiertag -> 0 (bzw. die heute vorhandene Halbtags-Behandlung, einmal
  einheitlich, statt dreimal unterschiedlich)
- laufende Wiedereingliederung -> Stunden aus dem Stufenplan
- sonst -> Zeitkonto

Der Nutzer hat ausdruecklich verlangt, dass dabei auch die heute verstreute
Feiertagsabfrage mitgebuendelt wird: "mir waere es recht, wenn die alle ihre
einzelnen Feiertagsabrufungen haben und dass alles unterschiedlich ist, das
auszulagern und einmal vernuenftig zu machen, dass die das alle abfragen, damit
es wartbarer wird."

Nachweislich wortgleich duplizierte Feiertagslogik, jeweils mit eigener
Halbtags-Behandlung:
- `MonatsSaldoService.java:265-266`
- `ZeiterfassungApiService.java:1145-1146`
- `ZeitkontoService.java:122`

Umzustellende Aufrufer: `MonatsSaldoService`, `ZeitkontoService`,
`AbwesenheitService`, `ZeiterfassungApiService`, `ZeitverwaltungController`.

Ausnahme: `VerrechnungslohnService` bleibt bewusst beim rohen Zeitkonto-Wert,
weil es dort um das Jahres-Normalsoll geht.

## 4. Verrechnungslohn

Krankheitsstunden werden nach Phase getrennt:
- Lohnfortzahlungs-Wochen bleiben im Abzug wie heute.
- Krankengeld- und Wiedereingliederungs-Zeitraeume fallen anteilig aus
  Jahressoll und Lohnkosten heraus. Faktor: Anteil der anwesenden Kalendertage
  am Jahr.
- Im Dialog steht zusaetzlich, wie viele Tage ausgeklammert wurden, damit die
  Zahl nachvollziehbar bleibt.

Projektstempeln bleibt vollstaendig unangetastet: die Stunden landen wie bisher
auf dem Projekt, Nachkalkulation und Abrechnung sehen sie komplett.

## 5. Desktop — neue Seite "Langzeitkrankmeldungen"

Eigener Menuepunkt neben "Urlaubsantraege". Liste mit Mitarbeiter, seit wann,
aktueller Phase als farbiger Status, geplanter Rueckkehr; vorgefiltert auf
laufende Faelle. Im Detail eine Zeitleiste der Phasen und der Stufenplan als
kleine Tabelle ("ab 01.04. — 2 Stunden pro Tag"), Zeilen hinzufuegbar.

Wording durchgehend ohne Fachchinesisch: "Lohnfortzahlung durch den Betrieb",
"Krankengeld der Krankenkasse", "Wiedereingliederung", "Wieder voll im Einsatz".

Farben und Komponenten nach dem Design-System des Projekts, Bausteine ueber den
shadcn-MCP. Der Zeiterfassungs-Kalender zeigt die Tage weiter farbig an und
verlinkt nur in die Meldung — in das 1417-Zeilen-Modal kommt kein neuer Code.

## 6. Handy-App (react-zeiterfassung)

Auf dem Dashboard eine Karte, solange eine Meldung laeuft:
"Wiedereingliederung — heute 4 Stunden geplant". Damit weiss der Mitarbeiter,
wogegen er stempelt. Im Abwesenheiten-Verlauf nur lesend; bearbeitet wird
ausschliesslich im Buero.

## 7. Fehlerfaelle

Abgelehnt werden:
- zwei ueberlappende Meldungen fuer denselben Mitarbeiter
- sich ueberlappende oder lueckenhafte Phasen
- eine Wiedereingliederungsphase ohne Stundenangabe
- Stufenplan-Stunden ueber dem normalen Tagessoll

Ein Urlaubsantrag im Krankheitszeitraum gibt eine Warnung.
Wer ueber seine Stufenplan-Stunden hinaus stempelt, wird nicht blockiert,
sondern markiert — Information fuer den Chef, kein Fehler.

## 8. Tests

Backend: Phasenlogik, 42-Tage-Rechnung, Ueberlappungen, TagesSollService,
ein erweitertes Verrechnungslohn-Szenario mit Langzeitfall, Controller-Tests —
alles mit Dummy-Daten.
Frontend: Vitest fuer die neue Seite plus die Playwright-Design-Pruefung in den
festen Bildschirmgroessen.

## Bewusst nicht drin (Nicht-Ziele)

- keine Diagnosespeicherung
- keine automatische Fortsetzungserkrankungs-Berechnung
- kein BEM-Verfahren
- keine eAU-Schnittstelle zur Krankenkasse
- kein automatischer Phasenwechsel im Hintergrund
- keine Aenderung am Projektstempeln
- kein neuer Code im 1417-Zeilen-Zeiterfassungs-Modal
