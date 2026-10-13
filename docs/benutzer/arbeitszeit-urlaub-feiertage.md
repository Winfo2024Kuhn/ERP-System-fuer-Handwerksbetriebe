# Arbeitszeit, Urlaub und Feiertage

Damit die Zeiterfassung rechnen kann, braucht jeder Mitarbeiter eine Arbeitszeit (Soll-Stunden je Wochentag), einen Jahresurlaub und den Zugang zur Handy-App. Die Seiten dazu stehen unter **Vorlagen & Stammdaten → Mitarbeiter** und im Reiter **Zeiterfassung** („Zeitkonten“, „Feiertage“, „Urlaubsanträge“, „Lange Krankheit“). Jeder angemeldete Benutzer darf dort arbeiten.

## Mitarbeiter für die Zeiterfassung einrichten

1. **Vorlagen & Stammdaten → Mitarbeiter** → „Neu“.
2. Wichtig für die Zeiterfassung:
   - „Abteilung(en)“: bestimmt, welche Tätigkeiten der Mitarbeiter in der App sieht, und welche Rechte er hat.
   - „Eintrittsdatum“: ab hier zählt das Soll.
   - „Jahresurlaub (Tage)“ (Pflicht).
   - „Geschäftsführer“: Geschäftsführer haben kein Soll und keinen Monatsabschluss; ihr Urlaub wird direkt eingetragen.
   - „Aktiv“: nur aktive Mitarbeiter können sich in der App anmelden.
   - „Arbeitszeit erfassen“ (voreingestellt): ausschalten für Personen, die nicht stempeln.
3. „Speichern“. Danach beim Mitarbeiter „Arbeitszeit einrichten“ wählen (siehe unten).

Abteilungen selbst legt man unter **Vorlagen & Stammdaten → Arbeitsgänge** an.

## Handy-App einrichten (QR-Code)

Beim Mitarbeiter (**Vorlagen & Stammdaten → Mitarbeiter**, Mitarbeiter öffnen) oben auf „Token erstellen“ klicken, bzw. auf „QR-Code“, wenn schon einer besteht. Der Mitarbeiter öffnet die Handy-App, tippt „Kamera öffnen & QR-Code scannen“ und scannt den Code – danach bleibt er angemeldet. „Neu generieren“ erzeugt einen neuen Code; der alte gilt dann nicht mehr. Den QR-Code sehen nur Admins und der Mitarbeiter selbst.

Die Adresse der Handy-App steht unter **Einstellungen → Zeiterfassung**, Karte „Mobile Zeiterfassung“.

## Arbeitszeit-Vorlagen anlegen

Unter **Zeiterfassung → Zeitkonten**, Karte „Arbeitszeit-Vorlagen“ → „Vorlage anlegen“:
- „Bezeichnung“, z. B. „Werkstatt Vollzeit“,
- Stunden für „Montag“ bis „Sonntag“,
- optional „Spätestes Ende“: Läuft eine Buchung darüber hinaus, beendet das Programm sie automatisch und meldet das in der Glocke („Kein Feierabend gestempelt“). „Frühester Start“ lässt sich ebenfalls eintragen.

Eine geänderte Vorlage gilt nicht von selbst: Nach dem Speichern öffnet sich „Vorlagenänderung übernehmen“. Dort „Gültig ab“ wählen, bei den betroffenen Mitarbeitern „Vorschau“ laden, anhaken und „Für Auswahl übernehmen“. Eine Vorlage, die schon einmal jemandem zugewiesen war, lässt sich nicht mehr löschen.

## Arbeitszeit eines Mitarbeiters ändern

Unter **Zeiterfassung → Zeitkonten**, Karte „Mitarbeiter mit Zeitkonto“, beim Mitarbeiter „Arbeitszeit ändern“ (oder beim Mitarbeiter selbst „Arbeitszeit einrichten“):

1. „Gültig ab“ wählen.
2. Eine Vorlage wählen oder „Individuelle Arbeitszeit“ und die Stunden je Wochentag eintragen.
3. „Vorschau laden“ – sie zeigt je Monat, wie sich das Soll ändert.
4. „Übernehmen“.

Ab dem Stichtag gilt die neue Arbeitszeit, frühere Zeiten bleiben erhalten. Abgeschlossene Monate ändern sich nie; liegt ab dem Stichtag schon ein abgeschlossener Monat, einen späteren Tag wählen oder den Monat erst wieder öffnen. Schon gebuchte Urlaubs- und Krankheitsstunden passen sich nicht an.

Ausschalten lässt sich die Arbeitszeit beim Mitarbeiter mit „Arbeitszeit ausschalten“; die bisherigen Stunden bleiben erhalten.

## Feiertage

Unter **Zeiterfassung → Feiertage** stehen die Feiertage des gewählten Jahres. Das Programm lädt sie selbst – derzeit immer die bayerischen, einschließlich Mariä Himmelfahrt. Ein anderes Bundesland, eigene Betriebsfeiertage oder halbe Feiertage (Heiligabend, Silvester) lassen sich nicht eintragen. „Feiertage neu generieren“ lädt das angezeigte und das folgende Jahr neu.

Feiertage zählen im Zeitkonto als bezahlter Tag mit dem vollen Tagessoll. An Feiertagen lässt sich kein Urlaub buchen, und sie zählen nicht als Urlaubstage.

## Urlaub beantragen (Handy)

In der Handy-App „Abwesenheit beantragen“:
1. Art wählen: „Urlaub“, „Krankheit“, „Fortbildung“ oder „Zeitausgleich“.
2. „Vom (Erster Tag)“ und „Bis (Letzter Tag)“, optional eine Bemerkung.
3. Senden, z. B. mit „Antrag senden“.

Bei Urlaub zeigt die App vorher die übrigen Urlaubstage; reichen sie nicht, lässt sich nicht senden. Unter „Übersicht“ sieht der Mitarbeiter seine Anträge und ob sie genehmigt sind. Solange ein Antrag noch offen ist, kann er ihn dort mit „Zurückziehen“ selbst zurücknehmen; genehmigten Urlaub nimmt nur das Büro zurück.

Wird ein Antrag genehmigt oder abgelehnt, bekommt der Mitarbeiter eine Push-Nachricht aufs Handy (wenn er Benachrichtigungen erlaubt hat). Zusätzlich steht die Entscheidung in der Handy-App unter der Glocke oben rechts neben dem Zahnrad. Dort sammelt die App alle Nachrichten, auch die Warnung vor verfallendem Resturlaub. Mit „Gelesen“ verschwindet eine Nachricht aus der Liste.

## Urlaubsanträge genehmigen oder ablehnen

Unter **Zeiterfassung → Urlaubsanträge** zeigt die Auswahl oben zuerst „Offene Anträge“; umschaltbar auf „Genehmigte Anträge“, „Abgelehnte Anträge“ und „Stornierte Anträge“. Jeder Antrag zeigt Art, Name, Zeitraum, Jahresurlaub und Bemerkung. „Genehmigen“ oder „Ablehnen“ wählen und bestätigen. Die Glocke oben rechts meldet neue Anträge.

Beim Genehmigen bucht das Programm für jeden Arbeitstag (Montag bis Freitag, ohne Feiertage) die Abwesenheit mit dem Tagessoll. Läuft für den Mitarbeiter gerade eine lange Krankheit, weist ein gelber Kasten darauf hin. Hat der Mitarbeiter für einen der Tage keine Arbeitszeit oder liegt ein Tag in einem abgeschlossenen Monat, klappt das Genehmigen nicht; das Programm sagt dann, woran es liegt.

## Genehmigten Urlaub zurücknehmen

Unter **Zeiterfassung → Urlaubsanträge** hat jeder offene und genehmigte Antrag den Knopf „Stornieren“. Nach der Rückfrage bekommt der Antrag den Stand „Storniert“, und alle Urlaubstage, die er im Kalender eingetragen hat, werden gelöscht. Der Resturlaub steigt entsprechend. Liegt ein Urlaubstag in einem abgeschlossenen Monat, zuerst den Monat wieder öffnen.

## Resturlaub

Resturlaub = Jahresurlaub minus genehmigte Urlaubstage des Jahres plus Urlaubs-Korrekturen. Offene Anträge zählen noch nicht. Ein halber Urlaubstag zählt als 0,5 Tage, auch Korrekturen dürfen halbe Tage sein. Er steht in **Zeiterfassung → Kalender** („Resturlaub“) und in der Handy-App unter „Saldenauswertung“. Resturlaub aus dem Vorjahr überträgt das Programm nicht von selbst; dafür unter **Zeiterfassung → Kalender** eine Korrektur „Urlaubstage“ anlegen (siehe [Zeiterfassung](zeiterfassung.md)).

## Lange Krankheit

Unter **Zeiterfassung → Lange Krankheit** behält man längere Krankheiten im Blick: erst „Lohnfortzahlung durch den Betrieb“ (42 Tage), dann „Krankengeld der Krankenkasse“, dann „Wiedereingliederung“. Die Krankheitstage selbst kommen weiter aus dem Kalender oder der Krankmeldung in der App.

1. „Krankmeldung anlegen“: Mitarbeiter, „Beginn“; „Lohnfortzahlung bis“ schlägt das Programm vor. In die interne Notiz keine Diagnosen eintragen.
2. Ist die Lohnfortzahlung vorbei, auf der Karte „Auf Krankengeld umstellen“. Im Fenster „Krankengeld ab“ steht schon der Tag nach dem Ende der Lohnfortzahlung; bei Bedarf ändern und „Umstellen“ wählen. Die Lohnfortzahlung endet dann automatisch am Vortag.
3. Für die Wiedereingliederung unter „Details“ im „Stufenplan“ Stunden pro Tag hinterlegen; sie ersetzen dann das Tagessoll.
4. Zum Schluss „Wieder voll im Einsatz“. Eine falsche Meldung lässt sich mit „Zurücknehmen“ verwerfen.

Die Auswahl oben zeigt „Läuft noch“, „Abgeschlossen“ oder „Zurückgenommen“. Weiterschalten muss das Büro selbst; das Programm macht das nicht automatisch.
