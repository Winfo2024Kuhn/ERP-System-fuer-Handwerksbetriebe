# Spec: Langzeitkrankmeldung

Issue: #91 — https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/91

Status: Grundlage ist das vom Nutzer am 08.09.2026 abgenommene Brainstorming
(`docs/superpowers/specs/2026-09-08-langzeitkrankmeldung-brainstorming.md`,
Ansatz A). Diese Spec erfindet nichts neu, sie überführt das abgenommene
Design in eine Umsetzungsgrundlage für den Grobplan. Die Auslagerung der
Tages-Soll-Logik in einen neuen `TagesSollService` (inkl. Bündelung der
dreifach duplizierten Feiertagslogik) ist vom Nutzer ausdrücklich freigegeben
und fester Bestandteil dieses Umfangs, kein optionaler Vorschlag.

## Ziel

Das Büro (die Person, die im Betrieb Lohn- und Zeitthemen bearbeitet) kann
einen echten Langzeitkrankheitsfall im ERP abbilden — von der Lohnfortzahlung
über das Krankengeld bis zur stufenweisen Wiedereingliederung. Heute kennt das
System nur die einfache Krankmeldung (`AbwesenheitsTyp.KRANKHEIT`), bei der
immer mit dem vollen Tagessoll gerechnet wird. Das führt bei einer
Wiedereingliederung (z.B. 2 Std/Tag) dazu, dass das System jeden Tag
Minusstunden aufbaut, obwohl der Mitarbeiter vereinbarungsgemäß nur reduziert
arbeitet. Ziel ist, dass das Büro eine Langzeitkrankmeldung anlegt, den
42-Tage-Lohnfortzahlungszeitraum im Blick behält, bei Bedarf auf Krankengeld
umstellt und einen Wiedereingliederungs-Stufenplan hinterlegt — und dass sich
danach *überall* im System (Zeitkonto, Monatssaldo, Verrechnungslohn,
Zeiterfassungs-Kalender) automatisch das richtige Tagessoll ergibt, ohne dass
der Mitarbeiter künstlich ins Minus rutscht.

## Nicht-Ziele

Wörtlich aus dem Brainstorming übernommen:

- keine Diagnosespeicherung
- keine automatische Fortsetzungserkrankungs-Berechnung
- kein BEM-Verfahren
- keine eAU-Schnittstelle zur Krankenkasse
- kein automatischer Phasenwechsel im Hintergrund
- keine Änderung am Projektstempeln
- kein neuer Code im 1417-Zeilen-Zeiterfassungs-Modal

## Architektur/Ablauf

### 1. Datenmodell

Neue Entität `Langzeitkrankmeldung` (Aggregate Root, eigene `version`-Spalte
für optimistisches Sperren — passend zur Konvention aus
`V364__aggregat_versionsspalten.sql`, die jedem Aggregate-Root eine
`version BIGINT NOT NULL DEFAULT 0`-Spalte gibt):

- Mitarbeiter (Verweis, analog zu `Abwesenheit.mitarbeiter`)
- Beginn
- Ende (leer = läuft noch)
- Status
- `lohnfortzahlungBis`
- optionale interne Notiz
- Versionsspalte

Darunter `LangzeitkrankmeldungPhase` (Kind-Entität, keine eigene
`version`-Spalte — Speicherung läuft über den Wurzel-Aggregat, analog zur
Begründung in `V364`):

- Typ: `LOHNFORTZAHLUNG` / `KRANKENGELD` / `WIEDEREINGLIEDERUNG`
- `vonDatum` / `bisDatum`
- `stundenProTag` — nur bei `WIEDEREINGLIEDERUNG` gefüllt

Mehrere Phasen hintereinander mit steigenden Stunden ergeben den Stufenplan.

`Abwesenheit` (`src/main/java/org/example/kalkulationsprogramm/domain/Abwesenheit.java`)
bekommt genau zwei neue Spalten: Verweis auf die Langzeitkrankmeldung und
Verweis auf die Phase. Das Muster dafür existiert bereits — `Abwesenheit` hat
schon eine analoge `@ManyToOne`-Beziehung `urlaubsantrag` (Zeile 31–33), die
neuen Spalten folgen demselben Muster. `AbwesenheitsTyp` bleibt unverändert
(verifiziert: `URLAUB`, `KRANKHEIT`, `FORTBILDUNG`, `ZEITAUSGLEICH` in
`domain/AbwesenheitsTyp.java`) — `KRANKHEIT` bleibt `KRANKHEIT`, die Phase ist
nur die Verfeinerung. Dadurch bleibt jede heutige Abfrage auf
`AbwesenheitsTyp.KRANKHEIT` gültig, ohne Anpassung.

`Abwesenheit` ist eine Pro-Tag-Entität (ein Datensatz je Tag mit Sollstunden),
das passt zum Phasenmodell: jeder Tag referenziert die zu diesem Zeitpunkt
gültige Phase.

Migration: `V367__langzeitkrankmeldung.sql`. Verifiziert: Die höchste heute
vorhandene Migration in `src/main/resources/db/migration/` ist
`V366__datensatz_lock_entitaet_typ_enum.sql`, `V367` ist damit tatsächlich die
nächste freie Nummer — keine Abweichung zum Brainstorming.

DSGVO: Es wird keine Diagnose gespeichert, auch kein Pflicht-Freitext. Das
Notizfeld wird in der Oberfläche ausdrücklich beschriftet mit
"interne Notiz — bitte keine Diagnosen eintragen". Damit bleibt das Feature
außerhalb von Art. 9 DSGVO (keine Gesundheitsdaten im engeren Sinn, nur
Zeiträume und Phasen).

### 2. Der 42-Tage-Countdown

Beim Anlegen setzt das System `lohnfortzahlungBis = Beginn + 41 Tage` und legt
die Lohnfortzahlungs-Phase an. Danach rechnet das System nichts von allein
weiter, sondern zeigt im Büro an:

- "Noch 12 Tage Lohnfortzahlung"
- "Lohnfortzahlung endete am 14.03. — auf Krankengeld umstellen?"

Erst der Klick des Büros legt die Krankengeld-Phase an. Es gibt **keinen**
Hintergrundjob, der Zustände von allein verschiebt (siehe Nicht-Ziele). Das
Datum ist überschreibbar — so trägt das Büro eine Fortsetzungserkrankung als
verkürzten Zeitraum ein.

### 3. Tages-Soll an einer Stelle: `TagesSollService`

**Ausgangslage (verifiziert):** `Zeitkonto.getSollstundenFuerTag(int
dayOfWeek)` (`domain/Zeitkonto.java:93`) wird an 12 Stellen im
Hauptquellcode aufgerufen. Nach Abzug der Definition selbst sind das 11
Aufrufstellen:

| Datei | Zeile(n) |
| --- | --- |
| `service/AbwesenheitService.java` | 60 |
| `controller/ZeitverwaltungController.java` | 502, 512 |
| `service/MonatsSaldoService.java` | 263 |
| `service/UrlaubsantragService.java` | 112 |
| `service/VerrechnungslohnService.java` | 462, 471, 506, 535 |
| `service/ZeiterfassungApiService.java` | 1142 |
| `service/ZeitkontoService.java` | 118 |

Davon sind die vier Aufrufe in `VerrechnungslohnService` bewusst ausgenommen
(siehe unten). `UrlaubsantragService.java:112` ist ein Aufrufer, der im
Brainstorming weder in der Umstellungsliste noch in der Ausnahmeliste genannt
wird — siehe "Offene Punkte".

Drei der verbleibenden Stellen dupliziert wortgleich (jeweils mit eigener
Halbtags-Behandlung) die Feiertagsprüfung — nachweislich verifiziert:

- `MonatsSaldoService.java:265-266` (Methode `berechneFeiertagsStunden`,
  ab Zeile 258): prüft `feiertagService.istFeiertag(tag)` nur, wenn
  `tagesSoll > 0`, addiert bei `istHalberFeiertag` 50%, sonst voll.
- `ZeiterfassungApiService.java:1145-1146` (Methode `berechneFeiertagsStunden`,
  ab Zeile 1137): identische Logik, eigenständig kopiert.
- `ZeitkontoService.java:122` (Methode `berechneSollstundenFuerZeitraum`,
  ab Zeile 113): **andere Zwecksetzung** — hier zählen Feiertage grundsätzlich
  als normale (bezahlte) Arbeitstage mit vollen Sollstunden, nur bei
  `istHalberFeiertag` wird auf 50% reduziert. Es gibt hier keine gesonderte
  `istFeiertag`-Prüfung wie in den anderen beiden Stellen.

Neuer `TagesSollService` beantwortet an genau einer Stelle die Frage
"was ist heute das Soll für Mitarbeiter X":

- Feiertag → 0 (bzw. die heute vorhandene Halbtags-Behandlung, einmal
  einheitlich statt dreimal unterschiedlich)
- laufende Wiedereingliederung → Stunden aus dem Stufenplan
- sonst → Zeitkonto

Umzustellende Aufrufer (Kernvorgabe aus dem Brainstorming, wörtlich): `MonatsSaldoService`,
`ZeitkontoService`, `AbwesenheitService`, `ZeiterfassungApiService`,
`ZeitverwaltungController`.

Ausnahme: `VerrechnungslohnService` bleibt bewusst beim rohen Zeitkonto-Wert
(`getSollstundenFuerTag` direkt, ohne `TagesSollService`), weil es dort um das
Jahres-Normalsoll geht, nicht um das tatsächliche Tagessoll unter
Berücksichtigung von Krankheit/Wiedereingliederung.

### 4. Verrechnungslohn

Krankheitsstunden werden nach Phase getrennt:

- Lohnfortzahlungs-Wochen bleiben im Abzug wie heute.
- Krankengeld- und Wiedereingliederungs-Zeiträume fallen anteilig aus
  Jahressoll und Lohnkosten heraus. Faktor: Anteil der anwesenden
  Kalendertage am Jahr.
- Im Dialog steht zusätzlich, wie viele Tage ausgeklammert wurden, damit die
  Zahl nachvollziehbar bleibt.

Betroffen: `VerrechnungslohnService.java` (Aufrufstellen von
`getSollstundenFuerTag` bei Zeile 462, 471, 506, 535 bleiben unverändert,
siehe Ausnahme oben — die neue Phasenaufteilung kommt als zusätzliche Logik
dazu, nicht als Ersatz für diese Aufrufe).

Projektstempeln bleibt vollständig unangetastet: die Stunden landen wie
bisher auf dem Projekt, Nachkalkulation und Abrechnung sehen sie komplett
(siehe Nicht-Ziele).

### 5. Desktop — neue Seite "Langzeitkrankmeldungen"

Frontend: `react-pc-frontend`. Vergleichbare bestehende Seite zur
Orientierung: `Urlaubsanträge`
(`react-pc-frontend/src/pages/Urlaubsantraege.tsx`), eingehängt in
`react-pc-frontend/src/App.tsx:36` (Import) und `:110` (Route
`/urlaubsantraege`), sowie im Menü in
`react-pc-frontend/src/components/layout/RibbonNav.tsx:127` (Desktop-Ribbon,
Eintrag "Anträge") und
`react-pc-frontend/src/components/layout/MobileBottomNav.tsx:42`
(Bottom-Nav, Eintrag "Urlaub"). Die neue Seite "Langzeitkrankmeldungen"
bekommt nach demselben Muster einen eigenen Import/Route-Eintrag in
`App.tsx` und einen eigenen Menüpunkt neben "Urlaubsanträge".

Inhalt: Liste mit Mitarbeiter, seit wann, aktueller Phase als farbiger
Status, geplanter Rückkehr; vorgefiltert auf laufende Fälle. Im Detail eine
Zeitleiste der Phasen und der Stufenplan als kleine Tabelle
("ab 01.04. — 2 Stunden pro Tag"), Zeilen hinzufügbar.

Wording durchgehend ohne Fachchinesisch: "Lohnfortzahlung durch den Betrieb",
"Krankengeld der Krankenkasse", "Wiedereingliederung", "Wieder voll im
Einsatz".

Farben und Komponenten nach dem Design-System des Projekts, Bausteine über
den shadcn-MCP. Der Zeiterfassungs-Kalender zeigt die Tage weiter farbig an
und verlinkt nur in die Meldung — in das 1417-Zeilen-Modal kommt kein neuer
Code (siehe Nicht-Ziele).

### 6. Handy-App (react-zeiterfassung)

Frontend: `react-zeiterfassung`. Betroffene Dateien:
`react-zeiterfassung/src/pages/DashboardPage.tsx` (Dashboard-Karte) und
`react-zeiterfassung/src/pages/AbwesenheitenPage.tsx` (lesende Anzeige im
Abwesenheiten-Verlauf).

Auf dem Dashboard eine Karte, solange eine Meldung läuft:
"Wiedereingliederung — heute 4 Stunden geplant". Damit weiß der Mitarbeiter,
wogegen er stempelt. Im Abwesenheiten-Verlauf nur lesend; bearbeitet wird
ausschließlich im Büro.

### 7. Fehlerfälle

Abgelehnt werden:

- zwei überlappende Meldungen für denselben Mitarbeiter
- sich überlappende oder lückenhafte Phasen
- eine Wiedereingliederungsphase ohne Stundenangabe
- Stufenplan-Stunden über dem normalen Tagessoll

Ein Urlaubsantrag im Krankheitszeitraum gibt eine Warnung.
Wer über seine Stufenplan-Stunden hinaus stempelt, wird nicht blockiert,
sondern markiert — Information für den Chef, kein Fehler.

### 8. Tests

Backend: Phasenlogik, 42-Tage-Rechnung, Überlappungen, `TagesSollService`,
ein erweitertes Verrechnungslohn-Szenario mit Langzeitfall,
Controller-Tests — alles mit Dummy-Daten (z.B. `Max Mustermann`), keine
echten Mitarbeiterdaten.

Frontend: Vitest für die neue Seite plus die Playwright-Design-Prüfung in den
festen Bildschirmgrößen.

## Betroffene Bereiche

**Backend** (`src/main/java/org/example/kalkulationsprogramm/`):

- Neu: `domain/Langzeitkrankmeldung.java`, `domain/LangzeitkrankmeldungPhase.java`,
  zugehörige Repository(s), Service (Anlegen/Phasenwechsel/Abfragen), Controller,
  DTOs.
- Neu: `service/TagesSollService.java`.
- Geändert: `domain/Abwesenheit.java` (zwei neue Spalten/Beziehungen).
- Geändert (Umstellung auf `TagesSollService`): `service/MonatsSaldoService.java`,
  `service/ZeitkontoService.java`, `service/AbwesenheitService.java`,
  `service/ZeiterfassungApiService.java`, `controller/ZeitverwaltungController.java`.
- Unverändert bezüglich Tagessoll-Berechnung (bewusste Ausnahme):
  `service/VerrechnungslohnService.java` — bekommt aber die neue
  Phasenaufteilung für die Jahressoll-/Lohnkosten-Berechnung dazu.
- Neu: `src/main/resources/db/migration/V367__langzeitkrankmeldung.sql`.

**Frontend Desktop** (`react-pc-frontend/`):

- Neu: Seite "Langzeitkrankmeldungen" (analog `src/pages/Urlaubsantraege.tsx`).
- Geändert: `src/App.tsx` (Import + Route), `src/components/layout/RibbonNav.tsx`
  und `src/components/layout/MobileBottomNav.tsx` (Menüpunkt).

**Frontend Handy** (`react-zeiterfassung/`):

- Geändert: `src/pages/DashboardPage.tsx` (neue Karte bei laufender Meldung),
  `src/pages/AbwesenheitenPage.tsx` (lesende Anzeige der Phase).

## Offene Punkte für den Grobplan

- **`UrlaubsantragService.java:112`**: Dieser Aufrufer von
  `getSollstundenFuerTag` taucht im Brainstorming weder in der
  Umstellungsliste (`MonatsSaldoService`, `ZeitkontoService`,
  `AbwesenheitService`, `ZeiterfassungApiService`, `ZeitverwaltungController`)
  noch in der Ausnahmeliste (`VerrechnungslohnService`) auf. Der Grobplan muss
  entscheiden, ob `UrlaubsantragService` ebenfalls auf `TagesSollService`
  umgestellt wird oder bewusst wie `VerrechnungslohnService` beim rohen
  Zeitkonto-Wert bleibt.
- **Genauer Schnitt/Reihenfolge der Umstellung**: Die fünf im Brainstorming
  genannten Aufrufer verteilen sich auf sieben konkrete Codestellen (siehe
  Tabelle oben). Reihenfolge und ob das in einem oder mehreren Schritten
  passiert, ist noch offen.
- **Exakter Methoden-/Rückgabewert-Zuschnitt von `TagesSollService`**: Die
  drei duplizierten Feiertagsblöcke haben nicht identische Semantik —
  `MonatsSaldoService`/`ZeiterfassungApiService` ermitteln eine separate
  "Feiertagsstunden"-Gutschrift (nur wenn `tagesSoll > 0` und Feiertag),
  während `ZeitkontoService.berechneSollstundenFuerZeitraum` Feiertage
  grundsätzlich als volle (bzw. bei Halbtag: halbe) bezahlte Arbeitstage in
  das Periodensoll einrechnet, ohne separate Feiertagsprüfung. Der Grobplan
  muss festlegen, welche Methode(n) `TagesSollService` genau anbietet, damit
  beide bisherigen Verwendungszwecke abgedeckt sind.
- **Zusammenspiel Feiertag und Wiedereingliederungs-Stufenplan**: Das
  Brainstorming legt fest, dass `TagesSollService` bei laufender
  Wiedereingliederung die Stunden aus dem Stufenplan liefert. Offen ist, was
  gilt, wenn ein Wiedereingliederungstag zugleich ein (halber) Feiertag ist —
  reduziert sich der Stufenplan-Wert dann zusätzlich, oder gilt er als fester
  Override unabhängig vom Feiertag.
- **Status-Werte und Übergänge der `Langzeitkrankmeldung`**: Das Brainstorming
  nennt ein "Status"-Feld auf der Entität, ohne die konkreten Statuswerte
  (z.B. laufend/beendet/etc.) und deren Übergänge aufzuzählen. Das muss der
  Grobplan konkretisieren.
- **Menüposition/Icon** der neuen Desktop-Seite "Langzeitkrankmeldungen" in
  `RibbonNav.tsx`/`MobileBottomNav.tsx` — das Brainstorming sagt nur
  "eigener Menüpunkt neben Urlaubsanträge", ohne Icon oder genaue Position.
