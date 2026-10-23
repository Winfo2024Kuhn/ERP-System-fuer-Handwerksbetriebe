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

Abteilungen selbst legt ein Admin unter **Einstellungen → Abteilungen & Rechte**, Karte „Abteilungen“, an.

## Handy-App einrichten (QR-Code)

Beim Mitarbeiter (**Vorlagen & Stammdaten → Mitarbeiter**, Mitarbeiter öffnen) oben auf „Token erstellen“ klicken, bzw. auf „QR-Code“, wenn schon einer besteht. Der Mitarbeiter öffnet die Handy-App, tippt „Kamera öffnen & QR-Code scannen“ und scannt den Code – danach bleibt er angemeldet. „Neu generieren“ erzeugt nach einer Rückfrage („Neuen Code erstellen“) einen neuen Code; der alte gilt dann nicht mehr, und der Mitarbeiter muss die Handy-App mit dem neuen Code neu einrichten. Den QR-Code sehen nur Admins und der Mitarbeiter selbst.

Die Adresse der Handy-App steht unter **Einstellungen → Zeiterfassung**, Karte „Mobile Zeiterfassung“.

## Arbeitszeit-Vorlagen anlegen

Unter **Zeiterfassung → Zeitkonten**, Karte „Arbeitszeit-Vorlagen“ → „Vorlage anlegen“:
- „Bezeichnung“, z. B. „Werkstatt Vollzeit“,
- Stunden für „Montag“ bis „Sonntag“,
- optional „Spätestes Ende“: Läuft eine Buchung darüber hinaus, beendet das Programm sie automatisch und meldet das in der Glocke („Kein Feierabend gestempelt“).

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

Unter **Zeiterfassung → Feiertage** stehen die Feiertage des gewählten Jahres für das Bundesland aus den Firmendaten (**Vorlagen & Stammdaten → Firma**, Feld „Bundesland“). Dort schlägt das Programm das Bundesland anhand der Postleitzahl vor („Übernehmen“) und zeigt aufklappbar, welche Feiertage damit gelten. Die gesetzlichen Feiertage lädt das Programm selbst. Nach einem Wechsel des Bundeslands rechnet es alle noch nicht abgeschlossenen Monate neu; abgeschlossene Monate bleiben unverändert.

Administratoren können je Feiertag:
- „Halber Tag“ einschalten: Der Tag bleibt ein Arbeitstag mit vollem Soll, gutgeschrieben wird die Hälfte (z. B. Heiligabend, Silvester).
- „Gilt bei uns“ ausschalten: für Feiertage, die nur in einem Teil des Bundeslands gelten (z. B. Mariä Himmelfahrt). Der Tag zählt dann als normaler Arbeitstag und ist mit „Abgeschaltet“ markiert.
- mit „Eigenen Feiertag hinzufügen“ einen Betriebsfeiertag anlegen (Datum, Bezeichnung, optional halber Tag). Eigene Feiertage lassen sich wieder löschen, gesetzliche nur abschalten.

„Feiertage neu generieren“ lädt das angezeigte und das folgende Jahr neu; eigene Feiertage und Einstellungen wie „Halber Tag“ oder „Abgeschaltet“ bleiben erhalten.

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

Beim Genehmigen bucht das Programm für jeden Tag, an dem der Mitarbeiter laut Arbeitszeit Sollstunden hat, die Abwesenheit mit dem Tagessoll – also auch den Samstag, wenn samstags gearbeitet wird. Feiertage und freie Tage (z. B. ein freier Mittwoch) kosten keinen Urlaub. Geschäftsführer ohne Arbeitszeit: Montag bis Freitag. Läuft für den Mitarbeiter gerade eine lange Krankheit, weist ein gelber Kasten darauf hin. Hat der Mitarbeiter für einen der Tage keine Arbeitszeit oder liegt ein Tag in einem abgeschlossenen Monat, klappt das Genehmigen nicht; das Programm sagt dann, woran es liegt.

## Genehmigten Urlaub zurücknehmen

Unter **Zeiterfassung → Urlaubsanträge** hat jeder offene und genehmigte Antrag den Knopf „Stornieren“. Nach der Rückfrage bekommt der Antrag den Stand „Storniert“, und alle Urlaubstage, die er im Kalender eingetragen hat, werden gelöscht. Der Resturlaub steigt entsprechend. Liegt ein Urlaubstag in einem abgeschlossenen Monat, zuerst den Monat wieder öffnen.

## Resturlaub

Resturlaub = Jahresurlaub plus Rest aus dem Vorjahr minus verfallene Tage minus genehmigte Urlaubstage des Jahres plus Urlaubs-Korrekturen. Offene Anträge zählen noch nicht. Ein halber Urlaubstag zählt als 0,5 Tage, auch Korrekturen dürfen halbe Tage sein. Er steht in **Zeiterfassung → Kalender** („Resturlaub“), bei offenen Anträgen unter **Zeiterfassung → Urlaubsanträge** (Stand zum ersten Urlaubstag) und in der Handy-App unter „Saldenauswertung“ und im Urlaubsantrag.

Der Jahresurlaub folgt dem Bundesurlaubsgesetz:

- Gesetzlicher Mindesturlaub sind 24 Werktage bei einer 6-Tage-Woche, also 20 Tage bei 5 und 16 Tage bei 4 Arbeitstagen. Die Arbeitstage kommen aus der Arbeitszeit des Mitarbeiters (Tage mit Soll-Stunden), ohne Arbeitszeit gilt die 5-Tage-Woche. Liegt der Jahresurlaub darunter, gilt das Minimum.
- Wer spätestens am 1. Juli anfängt, erfüllt die Wartezeit von sechs Monaten (§ 4 BUrlG) noch im selben Jahr und bekommt den vollen Jahresurlaub – auch den Teil über dem gesetzlichen Minimum, solange Arbeits- oder Tarifvertrag nichts anderes sagen.
- Wer später anfängt, bekommt ein Zwölftel des Jahresurlaubs je vollem Beschäftigungsmonat bis zum 31.12. Bruchteile ab einem halben Tag werden auf einen ganzen Tag aufgerundet, kleinere auf einen halben Tag (§ 5 Abs. 2 BUrlG).
- Sehen Arbeits- oder Tarifvertrag auch bei Eintritt bis 1. Juli Zwölftel vor, unter **Einstellungen → Zeiterfassung** in der Karte „Urlaub im Eintrittsjahr“ „Immer anteilig“ wählen. Dann gibt es ein Zwölftel je Monat, bei Eintritt bis 1. Juli aber mindestens den gesetzlichen Mindesturlaub.
- Beispiele bei 30 Tagen Jahresurlaub und 5-Tage-Woche: Eintritt 15.03. → 30 Tage („immer anteilig“: 22,5 → 23); Eintritt 01.06. → 30 Tage („immer anteilig“: 17,5 → 18, angehoben auf 20); Eintritt 01.08. → 12,5 → 13 Tage.
- Die Arbeitstage je Woche nimmt das Programm aus der Arbeitszeit des Jahres; gibt es mehrere, zählt die höchste.
- Ausscheiden im Jahr und Urlaub, den ein früherer Arbeitgeber im selben Jahr schon gewährt hat (§ 6 BUrlG), rechnet das Programm nicht. Diese Tage als Urlaubs-Korrektur abziehen.

Der Kalender, die Handy-App und **Mitarbeiter → Konditionen** zeigen neben dem Anspruch, warum er abweicht („anteilig wegen Eintritt am …“, „gesetzlicher Mindesturlaub“). Der Info-Button dort erklärt die Regeln.

Ein neuer Urlaubsantrag wird gegen den Resturlaub geprüft: Tage bis zum Verfallstag dürfen den Rest aus dem Vorjahr nutzen, Tage danach nur den Jahresurlaub.

### Rest aus dem Vorjahr

Was am Jahresende übrig ist, nimmt das Programm von selbst ins neue Jahr mit (erstmals ins Jahr nach dem Update). Urlaub im neuen Jahr zählt zuerst gegen diesen Rest. Wie lange er gilt, steht unter **Einstellungen → Zeiterfassung → Resturlaub ins neue Jahr**: Voreingestellt ist die gesetzliche Regel, nach dem 31. März verfällt der nicht genommene Rest. Der Betrieb kann einen anderen Tag wählen oder „Resturlaub verfällt nicht“ einstellen. Ein Tag vor dem 31. März ist nur erlaubt, wenn der Tarifvertrag das vorsieht; das Programm fragt dann vor dem Speichern nach. Der Rest wird immer neu ausgerechnet: Wer im alten Jahr Urlaub nachträgt oder den Jahresurlaub ändert, ändert damit auch den Übertrag. Wurde im alten Jahr mehr genommen als da war, ist der Übertrag 0 – abgezogen wird nichts. Gesetzlich verfällt Resturlaub nur, wenn die Mitarbeiter vorher rechtzeitig darauf hingewiesen wurden; die Handy-App erinnert zwei Monate vor dem Verfallstag unter der Glocke.

Stimmt der ausgerechnete Rest nicht (z. B. weil früher auf Papier geplant wurde), unter **Mitarbeiter → Konditionen → Resturlaub aus dem Vorjahr** den richtigen Wert eintragen. „Wieder ausrechnen lassen“ nimmt den Handwert zurück.

## Lange Krankheit

Unter **Zeiterfassung → Lange Krankheit** behält man längere Krankheiten im Blick: erst „Lohnfortzahlung durch den Betrieb“ (42 Tage), dann „Krankengeld der Krankenkasse“, dann „Wiedereingliederung“. Die Krankheitstage selbst kommen weiter aus dem Kalender oder der Krankmeldung in der App.

1. „Krankmeldung anlegen“: Mitarbeiter, „Beginn“; „Lohnfortzahlung bis“ schlägt das Programm vor. In die interne Notiz keine Diagnosen eintragen.
2. Ist die Lohnfortzahlung vorbei, auf der Karte „Auf Krankengeld umstellen“. Im Fenster „Krankengeld ab“ steht schon der Tag nach dem Ende der Lohnfortzahlung; bei Bedarf ändern und „Umstellen“ wählen. Die Lohnfortzahlung endet dann automatisch am Vortag.
3. Für die Wiedereingliederung unter „Details“ im „Stufenplan“ Stunden pro Tag hinterlegen; sie ersetzen dann das Tagessoll.
4. Zum Schluss „Wieder voll im Einsatz“. Eine falsche Meldung lässt sich mit „Zurücknehmen“ verwerfen.

Die Auswahl oben zeigt „Läuft noch“, „Abgeschlossen“ oder „Zurückgenommen“. Weiterschalten muss das Büro selbst; das Programm macht das nicht automatisch.
