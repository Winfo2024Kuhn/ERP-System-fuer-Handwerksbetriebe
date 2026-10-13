# Artikel

Unter **Vorlagen & Stammdaten → Artikel** steht das Material des Betriebs mit den Einkaufspreisen der Lieferanten. Daraus übernimmt man Material in Angebote und Rechnungen. Einen Lagerbestand führt das Programm bewusst nicht. Jeder angemeldete Benutzer darf Artikel anlegen und Preise eintragen.

## Artikel suchen

In **Vorlagen & Stammdaten → Artikel** wird beim Tippen sofort gefiltert:

- „Freitext“ sucht in Name, Artikelnummer, Produktlinie, Werkstoff (auch „V2A“), der Artikelnummer beim Lieferanten und im Lieferantennamen. Alle eingegebenen Wörter müssen vorkommen.
- „Lieferant“: nur Artikel, für die dieser Lieferant einen Preis hat; „Bester Preis“ zeigt dann seinen Preis.
- „Kategorie“: Ordner der Artikel, Unterordner sind eingeschlossen.
- „Werkstoff“, „Produktlinie“, „Herstellung“, „Zustand“ und die Schalter „verzinkbar“ und „pulverbeschichtbar“.
- „Filter zurücksetzen“ leert die Felder.

Die Liste zeigt 12 Artikel pro Seite mit Bild, „Artikel-Nr.“, „Bezeichnung“, „Werkstoff“, „Ausführung“, „kg/m“, „Bester Preis“ (günstigster Einkaufspreis) und „Günstigster Anbieter“. Ein Klick auf eine Zeile öffnet den Artikel. Suche und Filter bleiben beim Zurückgehen erhalten.

## Neuen Artikel anlegen

1. **Vorlagen & Stammdaten → Artikel** → „Neu“.
2. Im Fenster „Neuen Artikel anlegen“ den „Produktname“ eintragen, z. B. „Quadratrohr 40x40x2“ (Pflicht).
3. Bei Bedarf „Produktlinie“, „Beschreibung / Text“, „Kategorie“, „Werkstoff“ und „Verrechnungseinheit“ (Laufende Meter, Quadratmeter, Kilogramm oder Stück) wählen.
4. Den Einkaufspreis mit „Preis (€)“, „Lieferant (Optional)“ und „Artikelnummer (Extern)“ (die Nummer beim Lieferanten) eintragen. Preis und Lieferanten-Nummer immer zusammen mit dem Lieferanten eintragen – nur dann erscheinen sie in der Liste und beim Artikel.
5. „Artikel anlegen“.

Name, Einheit, Werkstoff und Kategorie lassen sich nach dem Anlegen derzeit nicht mehr ändern. Artikel lassen sich nicht löschen.

## Aufschläge einstellen

In **Vorlagen & Stammdaten → Artikel** gibt es den Knopf **„Aufschläge“**. Ihn sehen nur Benutzer, deren Abteilung das Recht „Aufschläge ändern“ hat (Einstellungen → Berechtigungen) sowie Administratoren. Das Fenster zeigt:

- **„Allgemeiner Aufschlag“:** Prozent auf den Einkaufspreis für alle Artikel, die weder einen eigenen noch eine Kategorie mit Aufschlag haben. Leer lassen heißt: kein allgemeiner Aufschlag.
- **„Aufschlag je Kategorie“:** je Kategorie ein eigener Aufschlag. Leer lassen heißt: kein eigener. Die Spalte „Es gilt“ zeigt, was für die Artikel der Kategorie am Ende gilt, z. B. „25 % (von „Stahl“)“.

Es gilt immer der **genaueste** Aufschlag: erst der am Artikel („Angebotsfelder“), dann der der Kategorie (hat sie keinen, der der nächsten Oberkategorie), zuletzt der allgemeine. 0 Prozent ist ein eigener Wert (zum Beispiel für Durchlaufposten), leer heißt „nicht eingestellt“. Ist nirgends einer eingestellt, übernimmt das Programm den Einkaufspreis unverändert und warnt „ohne Aufschlag“.

- Erlaubt sind 0 bis 999,99 Prozent, mit Komma.
- Der Preisvorschlag auf der Artikelseite und beim Einfügen von Material in ein Angebot oder eine Rechnung ändert sich sofort. Bereits erstellte Dokumente bleiben unverändert.
- Nach dem Update ändert sich nichts, bis jemand einen Aufschlag einstellt.

## Ordner für Artikel anlegen

In **Vorlagen & Stammdaten → Artikel** öffnet „Kategorien anlegen“ das Fenster „Kategorien verwalten“. Links steht die Ordnerstruktur. Ohne Auswahl entsteht mit „Hauptkategorie anlegen“ ein neuer Hauptordner; ist links ein Ordner gewählt, entsteht mit „Unterkategorie anlegen“ ein Unterordner darin. Umbenennen oder löschen lassen sich Ordner nicht.

Der Menüpunkt **Vorlagen & Stammdaten → Kategorien** ist etwas anderes: Dort stehen die Gewerke (Produktkategorien), die man Projekten und Leistungen zuordnet.

## Was zeigt ein Artikel?

Ein Klick auf einen Artikel unter **Vorlagen & Stammdaten → Artikel** öffnet ihn. Die Abschnitte:

- **„Was ist das für ein Teil?“** – Maße, Gewicht je Meter, Oberfläche je Meter, Normen (nur Anzeige).
- **„Bilder & Unterlagen“** – Vorschaubild sowie Zulassung, Zeichnung, Datenblatt oder Montageanleitung (PDF oder Bild, höchstens 10 MB) mit „Hochladen“.
- **„Oberfläche“** – ob Feuerverzinken und Pulverbeschichten möglich sind.
- **„Für Angebote und Rechnungen“** – Text und Aufschlag für den Kunden (siehe unten).
- **„Wer liefert das?“** – alle Lieferanten mit ihrer Artikelnummer, ihrem Preis und dem Abstand zum günstigsten.
- **„Preisverlauf“** – wie sich die Preise je Lieferant entwickelt haben.

## Preis eines Lieferanten eintragen oder ändern

1. Den Artikel unter **Vorlagen & Stammdaten → Artikel** öffnen.
2. Im Abschnitt „Wer liefert das?“ unter „Preis nachtragen“ mit „Lieferant auswählen“ den Lieferanten wählen.
3. „Dessen Artikelnummer“ (die Nummer beim Lieferanten) und den „Preis in Euro“ eintragen, z. B. 12,50.
4. „Speichern“.

Der bisherige Preis bleibt im „Preisverlauf“ erhalten. Preise aus Angeboten und Rechnungen von Lieferanten, die per E-Mail kommen, trägt das Programm oft selbst ein; im Preisverlauf steht dann die Herkunft.

## Verkaufspreis und Text für das Angebot festlegen

Beim Artikel (**Vorlagen & Stammdaten → Artikel**) im Abschnitt „Für Angebote und Rechnungen“:

- „Kurzbeschreibung“: nur für den Betrieb, steht nicht auf dem Angebot.
- „Beschreibung“: das liest der Kunde auf Angebot und Rechnung.
- „Aufschlag auf den Einkaufspreis“ in Prozent. Bleibt das Feld leer, steht darunter, was stattdessen gilt, zum Beispiel „Leer: es gilt 25 % von „Stahl““.
- „Angebotsfelder speichern“.

Einen festen Verkaufspreis gibt es nicht: Der Preis im Angebot ist der günstigste Einkaufspreis plus Aufschlag. Welcher Aufschlag gilt, siehe „Aufschläge einstellen“ weiter oben. Darunter steht nach dem Speichern, wie es im Angebot aussieht („So steht es im Angebot: …“). Gelbe Hinweise zeigen, was fehlt, z. B. „Kein Aufschlag hinterlegt“ oder „Kein Preis hinterlegt“.

## Material in ein Angebot oder eine Rechnung übernehmen

1. Im Dokument-Editor oben auf „Material“ klicken.
2. Im Fenster „Material auswählen“ suchen wie in der Artikelliste, Artikel ankreuzen und je eine Menge eintragen.
3. „Übernehmen“.

Jeder Artikel wird eine Position am Ende des Dokuments, mit Menge, Einheit und Preis (Einkauf plus Aufschlag). Als Text steht dort die Beschreibung für den Kunden. Fehlt etwas, zeigt die Liste im Fenster gelbe Hinweise wie „ohne Aufschlag“, „kein Preis“ oder „kein Kundentext“.

## Artikel für ein Projekt erfassen

Material, das für ein Projekt verbraucht wurde, trägt man im Projekt ein: Reiter „Material“ → „Artikel aus Lager“ (siehe [Projekte](projekte.md)). Das zählt nur die Kosten; einen Bestand bucht das Programm nicht ab.
