# Anfragen

Unter **Projektmanagement → Anfragen** stehen alle Kundenanfragen, aus denen noch kein Auftrag geworden ist. Hier wird das Angebot geschrieben. Wird der Auftrag erteilt, geht die Anfrage in einem Projekt auf. Jeder angemeldete Benutzer darf Anfragen ansehen, anlegen, ändern und löschen.

## Anfragen finden

In **Projektmanagement → Anfragen** wird beim Tippen sofort gefiltert:

- „Freitext“ sucht in Bauvorhaben, Kundenname, Kundennummer, Ansprechpartner, Telefon, Ort und E-Mail-Adressen.
- „Jahr“: das Jahr, in dem die Anfrage angelegt wurde.
- „Angebots-Status“: „Angebot angenommen“, „Wartet auf Kunden“ oder „Link abgelaufen“.
- „Status“: „Nur offene“ (voreingestellt), „Nur beendete“ oder „Offene und beendete“.
- „Herkunft“: „Über die Webseite“ oder „Selbst angelegt“.
- „Sortierung“: „Neueste zuerst“ oder „Älteste zuerst“.
- „Filter zurücksetzen“ leert alle Felder.

Die Anfragen stehen als Karten, 12 pro Seite. Jede Karte zeigt Bauvorhaben, Kunde, Anlagedatum, Betrag und „Offen“ oder „Beendet“. Anfragen über das Formular der Webseite tragen „Webseite · neu“ bzw. „Webseite“. Wurde das Angebot digital verschickt, steht dort „… angenommen“, „… wartet auf Kunde“ oder „… – Link abgelaufen“.

## Neue Anfrage anlegen

1. **Projektmanagement → Anfragen** → „Neue Anfrage“.
2. Im Fenster „Neue Anfrage anlegen“ unter „Kunde“ den Kunden suchen oder mit „Neuen Kunden anlegen“ neu erfassen.
3. „Bauvorhaben“ eintragen (Pflicht).
4. Bei Bedarf die Projektadresse („Kundenadresse übernehmen“ oder von Hand) und weitere E-Mail-Adressen ergänzen, z. B. die des Architekten.
5. „Anfrage anlegen“. Die neue Anfrage steht dann in der Liste.

Eine eigene Anfragenummer gibt es nicht: Als Nummer der Anfrage gilt die Nummer ihres Angebots. Sie entsteht beim ersten Speichern des Angebots.

## Anfragen von der Webseite

Schickt jemand das Anfrageformular auf der Webseite des Betriebs ab, legt das Programm die Anfrage selbst an:

- Den Kunden findet es über die E-Mail-Adresse oder legt ihn neu an.
- Die Nachricht und die Fotos stehen als erster Eintrag im Reiter „Tagebuch“ („Anfrage über Webseite vom …“).
- Der Interessent bekommt automatisch eine Bestätigungs-E-Mail. Ihren Text pflegt man unter **Kommunikation → E-Mail Vorlagen**, Vorlage „Webseite — Anfragebestätigung“.
- Die Glocke oben rechts meldet die Anfrage unter „Neu von der Webseite“, bis sie beendet ist.
- Mitarbeiter einer Abteilung mit dem Recht „Neue Anfrage über Webseite“ bekommen eine Nachricht aufs Handy (Recht vergibt ein Admin unter **Einstellungen → Abteilungen & Rechte**).

Die Karte trägt „Webseite · neu“, bis aus der Anfrage ein Projekt wird oder sie beendet ist.

## Was zeigt eine Anfrage?

Ein Klick auf eine Anfrage unter **Projektmanagement → Anfragen** öffnet sie. Oben stehen Bauvorhaben, Kunde, Adresse, Anlagedatum, „Brutto“ und „Netto“ sowie die Knöpfe „Bearbeiten“ und „Löschen“. Den Betrag gibt man nicht von Hand ein: Er kommt aus dem Angebot bzw. der Auftragsbestätigung. Rechts stehen unter „Anfragedaten“ Kunde, Kundennummer, Ansprechpartner, Kunden-E-Mails, Telefon und die Projektadresse mit Karte.

Reiter:
- **„E-Mails“:** alle E-Mails zu dieser Anfrage, mit Suche, Filter „Eingang“ / „Ausgang“, „Neue E-Mail“ und „Antworten“.
- **„Geschäftsdokumente“:** Angebot bzw. Auftragsbestätigung (siehe unten).
- **„Dateien“:** Ordner für Bilder, Pläne, Kalkulationen und sonstige Dateien; hochladen per Ziehen oder „durchsuchen“.
- **„Beschreibung“:** freier Text zur Anfrage mit „Speichern“.
- **„Tagebuch“:** Notizen mit Fotos, wie im Projekt („Neuer Eintrag“, „Bild hinzufügen“).

## Angebot schreiben und verschicken

1. In der Anfrage (**Projektmanagement → Anfragen**) Reiter „Geschäftsdokumente“ → „Dokument erstellen“ → „Angebot“.
2. Das Angebot öffnet sich im Dokument-Editor in einem neuen Tab. Positionen, Texte und Rabatte fügt man dort genauso ein wie bei Rechnungen (siehe [Rechnungen schreiben und stornieren](rechnungen.md), Abschnitt „Rechnung im Editor ausfüllen“).
3. Zum Verschicken im Editor auf „E-Mail“ klicken und das PDF-Format wählen.
4. Bei „Wie lange soll der Annahme-Link gültig sein?“ 7, 14, 21 oder 30 Tage oder ein eigenes Datum wählen, dann „Weiter zum E-Mail-Versand“.
5. Empfänger, Betreff und Text prüfen und „E-Mail senden“. Die E-Mail steht danach im Reiter „E-Mails“.

Pro Anfrage gibt es ein Angebot. Ein Angebot bleibt änderbar, bis der Kunde es annimmt. „Entwurf“ im Editor verschickt das Angebot zur Ansicht mit dem Aufdruck „ENTWURF“ und ohne Annahme-Link.

## Was passiert, wenn der Kunde das Angebot digital annimmt?

Über den Link in der E-Mail öffnet der Kunde das Angebot im Browser. Er bekommt einen Code per E-Mail, gibt Vor- und Nachnamen ein und nimmt an. Unter **Projektmanagement → Anfragen** zeigt die Karte vorher „… wartet auf Kunde“, danach „… angenommen“ mit Datum; nach Ablauf der Frist „… – Link abgelaufen“.

Nach der Annahme macht das Programm von selbst weiter:
- Das Angebot wird festgeschrieben und lässt sich nicht mehr ändern.
- Aus der Anfrage wird ein Projekt (Projektart „Pauschalpreis“, Auftragsnummer automatisch). Alles aus der Anfrage wandert dorthin.
- Eine Auftragsbestätigung entsteht und geht per E-Mail an den Kunden.
- Abteilungen mit dem Recht „Kunde hat angenommen“ bekommen eine Nachricht aufs Handy.

## Anfrage in ein Projekt übernehmen

In der Anfrage selbst gibt es dafür keinen Knopf. Es gibt drei Wege:
- **Projektmanagement → Projekte** → „Neues Projekt“ → Reiter „Von Anfrage übernehmen“ (siehe [Projekte](projekte.md)).
- Im schon angelegten Projekt „mit Anfrage zusammenführen“.
- Automatisch, wenn der Kunde das Angebot digital annimmt.

In allen drei Fällen ist die Anfrage danach nicht mehr unter Anfragen zu finden; ihr Inhalt steht im Projekt.

## Anfrage absagen oder beenden

Wird aus einer Anfrage nichts: auf ihrer Karte unter **Projektmanagement → Anfragen** das Kästchen „Beendet“ anklicken. Die Anfrage verschwindet aus der Standardansicht; mit „Status“ → „Nur beendete“ ist sie wieder zu sehen. Zum Wiederöffnen das Häkchen entfernen. Ein Grund wird nicht abgefragt, und der Kunde bekommt keine Nachricht.

## Anfrage löschen

In der Anfrage (**Projektmanagement → Anfragen**) oben „Löschen“ wählen und bestätigen. Löschen geht nur, solange noch keine E-Mails, Dateien, Angebote oder eigene Tagebuch-Einträge an der Anfrage hängen; sonst nennt das Programm den Grund. Dann die Anfrage stattdessen „Beendet“ setzen.

Hängt der Kunde nur an dieser einen Anfrage und hat keine Projekte, wird er mitgelöscht – darauf weist die Rückfrage hin.
