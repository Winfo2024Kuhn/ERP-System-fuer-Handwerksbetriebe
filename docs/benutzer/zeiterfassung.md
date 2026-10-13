# Zeiterfassung

Die Mitarbeiter stempeln ihre Zeiten in der Handy-App. Im Büro prüft und korrigiert man sie unter **Zeiterfassung → Kalender**. Nicht verwechseln mit **Projektmanagement → Kalender** – dort stehen die Termine (siehe [Kalender](kalender.md)). Arbeitszeiten, Urlaubsanträge und Feiertage richtet man unter [Arbeitszeit, Urlaub und Feiertage](arbeitszeit-urlaub-feiertage.md) ein, den Monatsabschluss beschreibt [Monatsabschluss](monatsabschluss.md). Den Bereich **Zeiterfassung** sehen am PC nur Administratoren, die Geschäftsführung und Abteilungen mit dem Recht „Zeiterfassung im Menü sehen“ (siehe [Einstellungen](einstellungen.md)).

## Wie stempeln Mitarbeiter mit dem Handy?

In der Handy-App (Anmeldung einmalig per QR-Code, siehe [Arbeitszeit, Urlaub und Feiertage](arbeitszeit-urlaub-feiertage.md)):

1. „Zeit erfassen“ antippen.
2. Projekt wählen, dann die Kategorie (oder „Ohne Kategorie fortfahren“) und die Tätigkeit.
3. „Zeiterfassung starten“.

Während die Zeit läuft, lassen sich Tätigkeit, Auftrag und Kategorie wechseln. „Pause“ unterbricht, „Arbeit fortsetzen“ geht weiter, „Gehen (Feierabend)“ beendet den Tag. Die App funktioniert auch ohne Netz und überträgt später. Unter „Heute gearbeitet“ sieht der Mitarbeiter seine Buchungen, ändern kann er sie dort nicht – das macht das Büro.

Wählbar sind nur Projekte, die nicht beendet sind. Ohne eingerichtete Arbeitszeit zeigt die App „Zeiterfassung derzeit nicht verfügbar“.

## Zeiten eines Mitarbeiters ansehen

1. **Zeiterfassung → Kalender**.
2. Oben den „Mitarbeiter“ wählen (mit dem Filter davor auch nicht aktive), dann „Monat“ und „Jahr“.

Oben stehen „Soll-Stunden“, „Ist-Stunden“ und „Differenz“ des Monats, dazu das „Gesamtstundenkonto“ (Überstunden seit Eintritt) und der „Resturlaub“ des Jahres mit Anspruch, genommen und geplant. Halbe Urlaubstage zählen dabei als 0,5. „Ist-Stunden“ enthält auch Urlaub, Krankheit, Feiertage und Korrekturen.

Im Monatsraster zeigt jeder Tag seine Buchungen (z. B. Projektname, „URLAUB“, „KRANK“, „PAUSE“) und rechts oben die Stunden des Tages. Feiertage sind rosa hinterlegt. Bei Geschäftsführern steht nur die erfasste Arbeitszeit, ohne Soll und ohne Monatsabschluss.

## Zeiten am PC eintragen oder ändern

1. In **Zeiterfassung → Kalender** den Mitarbeiter und Monat wählen.
2. Doppelklick auf den Tag. Es öffnet sich die „Tageserfassung“.
3. Mit „Neue Buchung“ eine Zeile anlegen oder eine vorhandene ändern:
   - „Von“ und „Bis“ (z. B. 7, 7:30 oder 0730),
   - „Projekt / Auftrag“ (Suchfenster),
   - „Tätigkeit“,
   - „Produktkategorie (optional)“,
   - „Bemerkung“.
4. „Pause“ fügt 12:00–12:30 ein und teilt überlappende Buchungen automatisch.
5. „Alle Speichern“. Überschneiden sich Zeiten, fragt das Programm nach.

Jede Buchung braucht ein Projekt. Eine Buchung ohne „Bis“ gilt als laufend; das Programm beendet sie spätestens zum eingestellten Feierabend bzw. um 23:59 und meldet das in der Glocke. „Schließen“ verwirft nicht gespeicherte Änderungen.

## Buchung löschen

In **Zeiterfassung → Kalender** den Tag doppelklicken und in der „Tageserfassung“ bei der Buchung auf den Papierkorb klicken, Rückfrage bestätigen. Die Buchung ist sofort gelöscht, ohne „Alle Speichern“. Das Programm hält jede Änderung und Löschung im Hintergrund fest.

## Urlaub, Krankheit oder Zeitausgleich eintragen

Normalerweise beantragen Mitarbeiter Urlaub in der Handy-App (siehe [Arbeitszeit, Urlaub und Feiertage](arbeitszeit-urlaub-feiertage.md)). Im Büro geht es unter **Zeiterfassung → Kalender** auch direkt:

- **In der Tageserfassung** (Doppelklick auf den Tag): „Urlaub“ (ganzer oder halber Tag), „Krankheit“ (ganzer Tag) oder „Zeitausgleich“ (Stunden wählbar).
- **Per Rechtsklick** auf einen Tag: Menü „Abwesenheit buchen“ mit „Urlaub (ganzer Tag)“, „Urlaub (halber Tag)“, „Krankheit“, „Fortbildung“ und „Zeitausgleich“. Fortbildung gibt es nur hier.
- **Mehrere Tage**: mit gedrückter Maustaste über die Tage ziehen (oder Umschalt-Klick), dann Rechtsklick in die Markierung. Gebucht werden nur Montag bis Freitag ohne Feiertage. Ein Rechtsklick auf einen Tag außerhalb der Markierung bucht nur diesen einen Tag.

Nicht möglich ist eine Abwesenheit an Feiertagen und an Tagen ohne Soll-Stunden. Zeitausgleich braucht genug Überstunden. War der Mitarbeiter an einem Krankheitstag schon arbeiten, füllt die Krankheit nur den Rest bis zum Soll auf. Entfernen lässt sich eine Abwesenheit in der Tageserfassung über den Papierkorb.

## Stunden oder Urlaubstage korrigieren

In **Zeiterfassung → Kalender** oben „Korrekturen“ (oder Rechtsklick → „Zeitkonto-Korrektur“) öffnet „Korrekturen verwalten“:

1. „Neue Korrektur“.
2. „Typ“: „Stunden“ oder „Urlaubstage“.
3. „Datum“, Anzahl und Vorzeichen (+ oder −).
4. „Begründung“ eintragen (Pflicht) und „Speichern“.

Stunden-Korrekturen zählen im Monat des Datums, Urlaubs-Korrekturen im Jahr des Datums – so überträgt man z. B. Resturlaub aus dem Vorjahr. Korrekturen werden nicht gelöscht, sondern über den Papierkorb mit Grund storniert.

## Abgeschlossene Monate

Ist ein Monat abgeschlossen, öffnet ein Doppelklick in **Zeiterfassung → Kalender** den Hinweis „Monatsabschluss ist festgeschrieben“: „Nur ansehen“ zeigt die Zeiten schreibgeschützt, „Monatsabschluss zurücksetzen“ öffnet den Monat wieder. Wieder öffnen darf nur, wer das Recht „Monate abschließen und wieder öffnen“ hat. Mehr in [Monatsabschluss](monatsabschluss.md).

## Wer darf Zeiten ändern?

Jeder angemeldete Benutzer sieht und ändert unter **Zeiterfassung → Kalender** die Zeiten aller Mitarbeiter. Zum Speichern muss der eigene Benutzer einem Mitarbeiter zugeordnet sein – das erledigt ein Admin unter „Benutzer verwalten“ (siehe [Einstellungen](einstellungen.md)). Auf dem Handy sieht jeder nur seine eigenen Zeiten.

## Stunden eines Projekts auswerten

Unter **Zeiterfassung → Auswertung**:

1. „Projekt“ wählen (auch beendete).
2. „Auswertung laden“.
3. Unter „Gruppierung“ wählen, wie die Stunden sortiert werden: nach „Arbeitsgang“, „Qualifikation“, „Mitarbeiter“, „Datum“ oder „Produktkategorie“.

Jede Gruppe zeigt Mitarbeiter, Tätigkeit, Datum, Zeit, Dauer und Bemerkung mit Summe. „PDF Export“ erstellt einen Stundennachweis ohne Preise für den Kunden (Regiebericht). Pausen zählen nicht mit.

## Stunden ans Steuerbüro schicken

Unter **Zeiterfassung → Steuerberater**:

1. „Monat“ und „Jahr“ wählen, „Vorschau laden“.
2. Die Mitarbeiter anhaken, die übermittelt werden sollen.
3. „Per E-Mail senden“, Empfänger und Text prüfen, „Senden“.

Senden geht erst, wenn der Monat für alle gewählten Mitarbeiter abgeschlossen ist. Den Steuerberater mit Ansprechpartner legt ein Admin unter **Vorlagen & Stammdaten → Firma**, Reiter „Steuerberater“ an. Wer mit DATEV arbeitet, nutzt stattdessen den [DATEV-Export](datev-lodas-export.md).
