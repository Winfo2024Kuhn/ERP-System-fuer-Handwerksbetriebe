# Projekte

Unter **Projektmanagement → Projekte** stehen alle Aufträge des Betriebs. Die Seite öffnet sich auch direkt nach der Anmeldung. Jeder angemeldete Benutzer darf Projekte ansehen, anlegen und ändern. Rechnungen zu einem Projekt beschreibt [Rechnungen schreiben und stornieren](rechnungen.md).

## Projekte finden

In **Projektmanagement → Projekte** wird beim Tippen sofort gefiltert:

- „Freitext“ sucht in Bauvorhaben, Kundenname, Kundennummer und Auftragsnummer.
- „Kunde“ sucht nur im Kundennamen.
- „Status“: „Alle“, „In Arbeit“ oder „Beendet“.
- „Jahr“: das Jahr, in dem das Projekt angelegt wurde.
- „Filter zurücksetzen“ leert alle Felder.

Die Projekte stehen als Karten, 12 pro Seite („zurück“ / „Weiter“). Jede Karte zeigt „Bezahlt“ (grün) oder „Offen“ (gelb), bei abgeschlossenen Projekten zusätzlich „Beendet“, dazu Bauvorhaben, Kunde, Auftragsnummer, Anlagedatum und Auftragssumme brutto. Wurde ein Angebot oder eine Auftragsbestätigung digital verschickt, steht dort auch „… angenommen“, „… wartet auf Kunde“ oder „… – Link abgelaufen“. Ein Klick auf die Karte öffnet das Projekt.

„Offen“ heißt nur: noch nicht vollständig bezahlt – auch ein Projekt ganz ohne Rechnung ist „Offen“.

## Liste der laufenden Projekte drucken

In **Projektmanagement → Projekte** öffnet „Liste: Projekte in Arbeit“ ein PDF mit allen Projekten, die nicht beendet sind: Auftragsnummer, Kunde, Bauvorhaben, Anlagedatum, Betrag und Gesamtsumme. Die Filter der Seite gelten für diese Liste nicht. Das PDF lässt sich herunterladen oder in einem neuen Tab öffnen.

## Neues Projekt anlegen

1. **Projektmanagement → Projekte** → „Neues Projekt“.
2. Im Fenster „Neues Projekt anlegen“ entweder eine Anfrage übernehmen (Reiter „Von Anfrage übernehmen“, siehe unten) oder „Ohne Anfrage anlegen“ wählen.
3. Ausfüllen:
   - **Kunde:** „Kunde auswählen“, im Suchfenster den Kunden suchen oder mit „Neuen Kunden anlegen“ gleich neu erfassen.
   - **Bauvorhaben**, z. B. „Neubau Einfamilienhaus“.
   - **Auftragsnummer:** wird vorgeschlagen (siehe nächster Abschnitt).
   - **Projektart:** „Pauschalpreis“, „Regie“, „Internes Projekt“ oder „Garantie“.
   - **Produktkategorien:** mit „Kategorie hinzufügen“ die Gewerke wählen und je eine Menge eintragen.
   - **E-Mail-Adressen:** die Adressen des Kunden sind schon dabei; weitere (z. B. Statiker) lassen sich ergänzen.
   - **Projektadresse:** mit „Kundenadresse übernehmen“ oder von Hand.
4. „Projekt anlegen“.

Kunde, Bauvorhaben und Auftragsnummer sind Pflicht. Die Auftragssumme wird nicht eingegeben – das Programm übernimmt sie aus Angebot, Auftragsbestätigung und Nachtragsangeboten, sonst aus den Rechnungen.

## Wie entsteht die Auftragsnummer?

Beim Anlegen eines Projekts unter **Projektmanagement → Projekte** schlägt das Programm die Auftragsnummer vor: Jahr und Monat, dann eine fünfstellige Zahl. Ist ein Kunde gewählt, enthält die Zahl eine Kundennummer für das Jahr und zählt die Aufträge dieses Kunden im Jahr mit. Ein Satz unter dem Feld erklärt die Nummer, z. B. dass es der zweite Auftrag des Kunden in diesem Jahr ist.

Die laufende Zahl lässt sich ändern. Mit „Auftragsnummer manuell vergeben“ lassen sich auch Jahr und Monat ändern. Eine Nummer, die schon vergeben ist, lehnt das Programm ab.

## Projekt aus einer Anfrage übernehmen

1. **Projektmanagement → Projekte** → „Neues Projekt“, Reiter „Von Anfrage übernehmen“.
2. Die Anfrage suchen (Bauvorhaben, Kunde, Kundennummer, Ort, E-Mail) und anklicken. Angezeigt werden nur Anfragen, aus denen noch kein Projekt wurde.
3. Kunde, Bauvorhaben, Adresse und Produktkategorien sind vorausgefüllt. Auftragsnummer und Projektart prüfen, dann „Projekt anlegen“.

Das Projekt übernimmt aus der Anfrage die Dateien, E-Mails, Tagebuch-Einträge mit Bildern, Angebote und Auftragsbestätigungen sowie die Termine. Danach ist die Anfrage nicht mehr unter Anfragen zu finden.

Nimmt ein Kunde ein Angebot digital an, legt das Programm das Projekt selbst an (siehe [Anfragen](anfragen.md)).

## Was zeigt ein Projekt?

Ein geöffnetes Projekt unter **Projektmanagement → Projekte** zeigt oben Bauvorhaben, „Bezahlt“ oder „Offen“, Kunde, Adresse und Auftragsnummer. Daneben stehen:

- **„Brutto“** und **„Netto“**: die Auftragssumme (Netto = Brutto geteilt durch 1,19).
- **„Arbeitskosten“**: gebuchte Stunden mal Stundensatz der Tätigkeit.
- **„Material“**: von Hand erfasste Materialkosten, Artikel aus dem Projekt und zugeordnete Lieferantenrechnungen.
- **„Gewinn“**: Netto minus Arbeitskosten minus Material.

Darunter die Reiter „Zeiten“, „Material“, „E-Mails“, „Geschäftsdokumente“, „Dateien“, „Beschreibung“ und „Tagebuch“. Rechts stehen unter „Projektdaten“ Kunde, Kundennummer, Ansprechpartner, Auftragsnummer, Anlagedatum, Kunden-E-Mails, Kategorien und die Projektadresse mit Karte.

## Arbeitszeiten im Projekt ansehen

Im Projekt (**Projektmanagement → Projekte**) zeigt der Reiter „Zeiten“ alle gebuchten Stunden, sortiert nach Produktkategorie, Tätigkeit und Mitarbeiter, jeweils in Stunden und Euro. Hier wird nur angezeigt. Gebucht wird in der Handy-App oder im Büro unter **Zeiterfassung → Kalender** (siehe [Zeiterfassung](zeiterfassung.md)).

## Materialkosten im Projekt erfassen

Im Projekt (**Projektmanagement → Projekte**) Reiter „Material“:

- **„Kosten erfassen“**: Fenster „Materialkosten erfassen“ mit „Beschreibung“ (z. B. Kleinmaterial), „Rechnungsnummer (Optional)“, „Betrag (€)“ und „Lieferant (Optional)“, dann „Speichern“.
- **„Artikel aus Lager“**: Artikel aus der Artikelliste suchen, ankreuzen, Menge eintragen und mit „Abschließen“ übernehmen. Stückware nur in ganzen Stück. Einen Lagerbestand führt das Programm nicht – es geht nur um die Kosten.

Einträge in beiden Listen lassen sich über den Papierkorb am Zeilenende („Aus dem Projekt entfernen“) wieder entfernen.

Lieferantenrechnungen, die dem Projekt zugeordnet sind, stehen im Reiter „Geschäftsdokumente“ und zählen ebenfalls zum Material.

## E-Mails, Dokumente und Dateien im Projekt

- **„E-Mails“:** alle E-Mails zu diesem Projekt, mit Suche und Filter „Eingang“ / „Ausgang“. „Neue E-Mail“ schreibt eine E-Mail, die gleich beim Projekt abgelegt wird; geöffnete E-Mails lassen sich beantworten. Mehr dazu in [E-Mail-Center](email-center.md).
- **„Geschäftsdokumente“:** Angebote, Auftragsbestätigungen und Rechnungen. „Dokument erstellen“ legt ein neues an (siehe [Rechnungen schreiben und stornieren](rechnungen.md)). Darunter stehen die zugeordneten Lieferantenrechnungen.
- **„Dateien“:** Ordner „Bilder“, „Geschäftsdokumente“, „Planungsdokumente“, „Kalkulationsdokumente“, „Dokumentation 1090“, „Eingangsrechnungen“ und „Diverse Dokumente“. Dateien in einen geöffneten Ordner ziehen oder „durchsuchen“ wählen. Beim Drüberfahren über eine Datei erscheinen „Öffnen“, „Herunterladen“ und „Löschen“.

## Beschreibung und Tagebuch

Im Projekt (**Projektmanagement → Projekte**):

- **„Beschreibung“:** freier Text zum Projekt, mit „Speichern“ sichern.
- **„Tagebuch“:** „Neuer Eintrag“ schreibt eine Notiz. „In mobiler App anzeigen“ (voreingestellt) zeigt sie auch auf den Handys der Mitarbeiter, „Nur für mich sichtbar“ macht sie privat. Mit „Bild hinzufügen“ hängen Fotos an einem Eintrag. Einträge lassen sich über Stift und Papierkorb ändern und löschen.

Für das Tagebuch muss der eigene Benutzer einem Mitarbeiter zugeordnet sein; das erledigt ein Admin unter „Benutzer verwalten“ (siehe [Einstellungen](einstellungen.md)).

## Projekt bearbeiten

Im Projekt (**Projektmanagement → Projekte**) oben auf „Bearbeiten“ klicken. Im Fenster „Projekt bearbeiten“ lassen sich Kunde, Bauvorhaben, Auftragsnummer, Projektart, Kategorien, E-Mail-Adressen und Adresse ändern, dann „Änderungen speichern“. Eine Kategorie, auf die schon Arbeitszeiten gebucht sind, lässt sich nicht entfernen, nur gegen eine andere tauschen.

## Projekt beenden oder wieder öffnen

- Auf der Karte in **Projektmanagement → Projekte** das Kästchen „Beendet“ anklicken – oder im Fenster „Projekt bearbeiten“ das Häkchen „Projekt beendet“ setzen. Zum Wiederöffnen das Häkchen entfernen. Es gibt keine Rückfrage.
- Ist ein Projekt komplett bezahlt, setzt das Programm „Beendet“ von selbst. Hat jemand das Häkchen bei einem Projekt einmal von Hand gesetzt oder entfernt, fasst das Programm es dort nicht mehr an.

Beendete Projekte sind auf der Karte ausgegraut, fehlen in „Liste: Projekte in Arbeit“ und lassen sich bei neuen Zeitbuchungen nicht mehr auswählen.

## Projekt mit einer Anfrage zusammenführen

Hat jemand ein Projekt ohne Anfrage angelegt, obwohl es eine passende Anfrage gibt: Im Projekt (**Projektmanagement → Projekte**) „mit Anfrage zusammenführen“ wählen, die Anfrage desselben Kunden aussuchen und mit „Zusammenführen und Anfrage löschen“ bestätigen. Dateien, Geschäftsdokumente, Beschreibung, Tagebuch und E-Mails wandern ins Projekt, danach verschwindet die Anfrage. Das geht nur, solange das Projekt selbst noch kein Angebot, keine Auftragsbestätigung und keine Rechnung hat.

## Projekt löschen

Ein Projekt lässt sich nicht löschen. Wird ein Projekt nicht mehr gebraucht, das Häkchen „Beendet“ setzen.
