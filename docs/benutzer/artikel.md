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

Name, Einheit, Werkstoff und Kategorie lassen sich danach über „Stammdaten bearbeiten“ (siehe unten) bearbeiten. Artikel lassen sich nicht löschen.

## Stammdaten bearbeiten

Auf der Artikelseite (Klick auf eine Zeile in **Vorlagen & Stammdaten → Artikel**) öffnet „Stammdaten bearbeiten“ die Maske. Dort lassen sich ändern:

- „Produktname“ (Pflicht), „Produktlinie“, „Beschreibung (intern)“, „Artikelnummer“ und „EAN / Strichcode“,
- „Kategorie“ (im Auswahlfenster lassen sich auch neue Kategorien anlegen),
- „Verrechnungseinheit“, „Verpackungseinheit (Menge)“, „Preiseinheit (Text)“ und „Werkstoff“,
- „Form“, „Herstellverfahren“, „Fertigungszustand“, „Maßnorm“ und „Werkstoffnorm“,
- „Feuerverzinken“ und „Pulverbeschichten“: ob das Teil dafür **geeignet** ist. Standard ist „Vom Werkstoff übernehmen“; für diesen einen Artikel lässt sich „Geeignet“ oder „Nicht geeignet“ festlegen. Die Artikelseite zeigt dahinter, woher die Eignung kommt,
- bei Rohren, Profilen und Blechen die Maße (Durchmesser, Höhe, Breite, Wandstärke, Stegdicke, Flanschdicke) und die „Übliche Länge (mm)“.

Gut zu wissen:

- Speichern ersetzt alle diese Angaben. Was man leert, ist danach leer; nur der Produktname muss bleiben.
- Artikelnummer und EAN dürfen nur bei einem Artikel vorkommen. Gibt es sie schon, meldet das Programm es mit Klartext. Die EAN wird auf die richtige Prüfziffer kontrolliert (erlaubt sind 8, 12, 13 oder 14 Ziffern).
- Die Maske merkt sich den Stand, mit dem sie geladen wurde. Hat jemand den Artikel inzwischen geändert, wird das Speichern abgelehnt („Bitte die Seite neu laden“), damit keine Änderung unbemerkt überschrieben wird.
- Eine andere Einheit rechnet bereits eingetragene Mengen **nicht** um. Bestehende Angebote und Rechnungen bleiben unverändert. Das (i) an der Einheit erklärt es.
- Nicht geändert werden Angebotstexte und Aufschlag („Angebotsfelder“), Lieferanten, Preise und Dokumente. Dafür gibt es weiter die eigenen Bereiche.
- Die „Beschreibung (intern)“ darf höchstens 255 Zeichen lang sein.
- Die Suche findet den Artikel nach dem Ändern unter den neuen Angaben, auch über die EAN.

## Aufschläge einstellen

In **Vorlagen & Stammdaten → Artikel** gibt es den Knopf **„Aufschläge“**. Ihn sehen nur Benutzer, deren Abteilung das Recht „Aufschläge ändern“ hat (Einstellungen → Berechtigungen) sowie Administratoren. Das Fenster zeigt:

- **„Allgemeiner Aufschlag“:** Prozent auf den Einkaufspreis für alle Artikel, die weder einen eigenen noch eine Kategorie mit Aufschlag haben. Leer lassen heißt: kein allgemeiner Aufschlag.
- **„Aufschlag je Kategorie“:** je Kategorie ein eigener Aufschlag. Leer lassen heißt: kein eigener. Die Spalte „Es gilt“ zeigt, was für die Artikel der Kategorie am Ende gilt, z. B. „25 % (von „Stahl“)“.

Es gilt immer der **genaueste** Aufschlag: erst der am Artikel („Angebotsfelder“), dann der der Kategorie (hat sie keinen, der der nächsten Oberkategorie), zuletzt der allgemeine. 0 Prozent ist ein eigener Wert (zum Beispiel für Durchlaufposten), leer heißt „nicht eingestellt“. Ist nirgends einer eingestellt, übernimmt das Programm den Einkaufspreis unverändert und warnt „ohne Aufschlag“.

- Erlaubt sind 0 bis 999,99 Prozent, mit Komma.
- Der Preisvorschlag auf der Artikelseite und beim Einfügen von Material in ein Angebot oder eine Rechnung ändert sich sofort. Bereits erstellte Dokumente bleiben unverändert.
- Nach dem Update ändert sich nichts, bis jemand einen Aufschlag einstellt.

## Werkstoffe pflegen

Der „Werkstoff“ am Artikel kommt aus einer eigenen Liste. Sie lässt sich in **Vorlagen & Stammdaten → Artikel** mit „Werkstoffe pflegen“ ändern (Fenster „Werkstoffe“, daneben „Kategorien anlegen“). Die Liste zeigt je Werkstoff den Namen, den Werkstattnamen, die Dichte, die Eignung für die Oberfläche und wie viele Artikel ihn benutzen.

- „Neuer Werkstoff“ öffnet das Formular. Eintragen lassen sich: „Name (technisch)“ (Pflicht, z. B. S235JR oder 1.4301), „Anzeigename“, „Werkstattname“ (so sagt man in der Werkstatt dazu, die Artikelsuche findet den Werkstoff auch darüber), „Norm“, „Dichte (kg/dm³)“ (0,1 bis 25; Stahl 7,85, Aluminium 2,7), „Feuerverzinken geht“, „Pulverbeschichten geht“ und ein „Hinweis zur Beschichtung“.
- Der Stift ändert einen Werkstoff, der Papierkorb löscht ihn nach einer Rückfrage.
- Ein Name kommt nur einmal vor (Groß- und Kleinschreibung zählt nicht).
- Ein Werkstoff, den Artikel benutzen, lässt sich **nicht löschen**. Die Meldung nennt, wie viele Artikel ihn benutzen.
- Die Eignung für Verzinken und Pulvern gilt für alle Artikel aus diesem Werkstoff. Sie erscheint auf der Artikelseite und in den Filtern „verzinkbar“ und „pulverbeschichtbar“ der Artikelsuche; Preise und bestehende Angebote ändern sich dadurch nicht.
- Der technische Name wird beim Artikel-Import benutzt, um Werkstoffe wiederzuerkennen. Wird er geändert, legt der nächste Import den alten Namen als neuen Werkstoff an.
- Die Dichte wird beim Werkstoff gespeichert. Das Programm rechnet damit im Moment noch nichts aus; an Artikeln, Preisen und Angeboten ändert sich dadurch nichts.

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
