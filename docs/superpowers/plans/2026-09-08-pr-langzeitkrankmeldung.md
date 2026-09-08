# Langzeitkrankmeldung: Lohnfortzahlung, Krankengeld und Wiedereingliederung abbilden

Schließt #91

## Worum es geht

Fällt ein Mitarbeiter länger aus, zerfällt der Ausfall in Phasen: erst sechs
Wochen Lohnfortzahlung, dann Krankengeld über die Kasse, am Ende oft eine
stufenweise Wiedereingliederung mit reduzierten Stunden. Bisher kannte das
Programm nur "krank" — ohne Anfang, ohne Ende, ohne Unterscheidung. Das Büro
musste selbst wissen, ab wann die Kasse zahlt, und der Verrechnungslohn rechnete
Ausfalltage mit, die gar keine Lohnkosten mehr verursachen.

Diese Änderung bildet den kompletten Verlauf ab: anlegen, Phasen pflegen,
Stufenplan hinterlegen, abschließen. Die 42-Tage-Grenze zur Lohnfortzahlung
wird mitgezählt und angezeigt.

## Was der Nutzer davon hat

**Büro (PC-App).** Neue Seite "Lange Krankheit" unter Personal. Zeitleiste mit
den Phasen, Stufenplan als Tabelle, Restlaufzeit der Lohnfortzahlung im Blick.
Anlegen, ändern und abschließen passiert hier — und nur hier.

**Mitarbeiter (Handy-App).** Nur lesend. Wer in einer laufenden Meldung steckt,
sieht eine Karte auf dem Dashboard und die Phase im Abwesenheiten-Verlauf.
Anlegen oder ändern kann er nichts, das ist bewusst so: Ein Mitarbeiter legt
seine eigene Wiedereingliederung nicht an.

**Ein Fehlklick lässt sich zurücknehmen.** Wer versehentlich auf "Wieder voll im
Einsatz" klickt, bekommt einen Knopf "Doch noch krank" und macht es rückgängig.
Ohne den wäre der einzige Ausweg eine neue Meldung mit späterem Beginn gewesen —
und das hätte den 42-Tage-Zähler verfälscht.

**Urlaubsanträge.** Kommt ein Antrag herein, während eine Krankmeldung läuft,
steht das jetzt als Hinweis beim Antrag. Vorher hat man das übersehen.

**Verrechnungslohn.** Tage, an denen die Kasse zahlt, zählen nicht mehr als
Arbeitszeit ins Jahressoll und nicht mehr in die Lohnkosten. Die Spalte
"ausgeklammerte Tage" zeigt, wie viele Tage herausgerechnet wurden.

Im Menü heißt der Eintrag jetzt "Urlaubsanträge" statt "Anträge": Die Gruppe
darüber wurde zu "Abwesenheiten", damit "Lange Krankheit" hineinpasst — sonst
hätte das Wort "Urlaub" nirgends mehr gestanden.

## Aufgeräumt: die Feiertagslogik lag dreimal im Code

Vier Stellen haben jeweils eigenständig gerechnet, wie viele Sollstunden ein Tag
hat und ob ein Feiertag gutgeschrieben wird — Zeitkonto, Monatssaldo,
Zeiterfassung und Kalender. Die drei Feiertagsvarianten waren dabei nicht einmal
identisch, was beim Schreiben der Spec auffiel.

Das steckt jetzt in einem `TagesSollService`. Vor jeder Umstellung ist ein
Charakterisierungstest entstanden, der das bisherige Verhalten festhält; erst
danach wurde umgestellt. Sechs solcher Tests liegen im PR und pinnen die alten
Zahlen.

Nebenbei fiel dabei ein echter Rechenfehler auf: Der Verrechnungslohn hat den
Krankheitsausfall bei Lohnquelle "Lohnabrechnung" doppelt abgezogen — einmal
steckte er schon im gemeldeten Brutto, einmal kam der Faktor obendrauf.
Gemessen: 32.001,60 € statt 48.000,00 €. Ist behoben und durch einen Test
gepinnt.

## Performance

Die Umstellung auf Zeitraum-Abfragen hat drei N+1-Probleme mitgenommen. Gemessen
über eine Zählsonde am Repository:

| Stelle | vorher | nachher |
| --- | --- | --- |
| Kalender (ein Monat) | 249 Abfragen | 9 |
| Stufenplan (ein Plan) | 162 Abfragen | 3 |
| Urlaubsantrag genehmigen | 45 Abfragen | 3 |

## Datenschutz

Krankheitsdaten sind Gesundheitsdaten nach Art. 9 DSGVO. Zwei Dinge waren dabei
wichtig:

Der Urlaubs-Hinweis lag zunächst unter `/api/urlaub/**` — einer Route, die ohne
Login erreichbar ist. Die Mitarbeiter-ID war frei wählbar, und die Antwort hat
verraten, dass jemand krank ist und seit wann. Das ist jetzt ein eigener Pfad
hinter dem Login (`/api/langzeitkrankmeldungen/urlaubs-hinweise`). Alle sieben
bestehenden Urlaubspfade sind dabei zeichengleich geblieben, statisch und zur
Laufzeit nachgeprüft.

Das Notizfeld heißt bewusst "Interne Notiz — bitte keine Diagnosen eintragen"
und erscheint nur im Detailbereich der Büro-Seite. Weder das Handy-Banner noch
der Urlaubshinweis nennen jemals eine Ursache.

## Technisch

- **Migration V367** legt `langzeitkrankmeldung` und
  `langzeitkrankmeldung_phase` an, plus zwei nullable Fremdschlüssel an
  `abwesenheit`. Native ENUM-Spalten, weil Hibernate 6 sonst beim
  `ddl-auto=validate` aussteigt. Der Aggregatsbaum trägt eine `version` an der
  Wurzel, optimistisches Sperren mit HTTP 409.
- **14 neue Java-Testklassen**, dazu erweiterte Bestandstests.
- **Drei Playwright-Specs** für die neuen Abläufe im Büro.
- **82 Dateien, +14.534/−274.** Der Wissensgraph wurde separat aktualisiert.

## Was nicht drin ist

- `PUT /{id}` und `PUT /{id}/phasen/{phasenId}` sind getestet, aber ohne
  Oberfläche. Ändern geht über die API, nicht über einen Knopf.
- Der Verrechnungslohn-Dialog erklärt, warum das **Jahressoll** kleiner ist. Warum
  die **Lohnsumme** kleiner ist, steht nur in einem Tooltip.
- Wer über seine Stufenplan-Stunden hinaus stempelt, wird wie geplant **nicht
  blockiert** — die im Design vorgesehene **Markierung** für den Chef fehlt aber
  noch. Die Mehrstunden tauchen nur als Plus im Saldo auf, ohne Hinweis auf den
  Stufenplan.
- Ein Urlaubsantrag löst einen Request pro offenem Antrag aus (parallel, kein
  Wasserfall). Bei zwölf Anträgen sind das zwölf Aufrufe.

## Geprüft

Backend 2611 Tests. Vier Fehler sind vorbestehend und hängen an der
Datenbankverbindung, nicht an dieser Änderung — sie waren vor dem ersten Commit
schon da und sind so dokumentiert. Beide Frontends: Lint sauber, alle
Test-Dateien grün, Build durch. Die vier Playwright-Specs rund um das Feature
laufen in allen drei Bildschirmgrößen: 33 Prüfungen, alle grün.
