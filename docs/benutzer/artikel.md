# Artikel

Unter **Vorlagen & Stammdaten → Artikel** steht das Material des Betriebs mit den Einkaufspreisen der Lieferanten. Daraus übernimmt man Material in Angebote und Rechnungen. Einen Lagerbestand führt das Programm bewusst nicht. Jeder angemeldete Benutzer darf Artikel anlegen und Preise eintragen.

## Artikel suchen

In **Vorlagen & Stammdaten → Artikel** wird beim Tippen sofort gefiltert:

- „Freitext“ sucht in Name, Artikelnummer, Produktlinie, Werkstoff (auch „V2A“), der Artikelnummer beim Lieferanten und im Lieferantennamen. Alle eingegebenen Wörter müssen vorkommen.
  - Maße dürfen zusammen oder getrennt geschrieben werden: „flachstahl 20x10“, „Flachstahl 20 x 10“ und „flach 20 10“ finden dasselbe.
  - Zahlen zählen als ganze Zahl: „20x10“ findet nicht „120 x 10“ oder „100 x 20“. „42,4“ und „42.4“ sind gleich, „20“ findet aber nicht „20,5“.
  - Nummern ab vier Zeichen dürfen auch ein Teil sein: „3270“ findet „A-003270“, „4301“ findet den Werkstoff 1.4301.
  - Umlaute und ihre Umschreibung sind gleich: „träger“ findet „Traeger“ und umgekehrt.
- „Lieferant“: nur Artikel, für die dieser Lieferant einen Preis hat; „Bester Preis“ zeigt dann seinen Preis.
- „Kategorie“: Ordner der Artikel, Unterordner sind eingeschlossen.
- „Werkstoff“, „Produktlinie“, „Herstellung“, „Zustand“ und die Schalter „verzinkbar“ und „pulverbeschichtbar“.
- „Filter zurücksetzen“ leert die Felder.

Die Liste zeigt 12 Artikel pro Seite mit Bild, „Artikel-Nr.“, „Bezeichnung“, „Werkstoff“, „Ausführung“, „kg/m“, „Bester Preis“ (günstigster Einkaufspreis) und „Günstigster Anbieter“. Ein Klick auf eine Zeile öffnet den Artikel. Suche und Filter bleiben beim Zurückgehen erhalten.

## Neuen Artikel anlegen

1. **Vorlagen & Stammdaten → Artikel** → „Neu“.
2. Das Fenster „Neuen Artikel anlegen“ hat vier Reiter. Pflicht ist nur der „Produktname“ im ersten Reiter; die anderen sind als „optional“ gekennzeichnet. Ein Häkchen am Reiter zeigt, dass dort etwas eingetragen ist. „Weiter“ springt zum nächsten Reiter.
   - **„Das Wichtigste“:** „Produktname“, z. B. „Flachstahl 20 x 10“, dazu „Einheit“ (Laufende Meter, Quadratmeter, Kilogramm, Stück oder Liter), „Kategorie“ und „Werkstoff“. Die „Artikel-Nr.“ steht schon da, z. B. „A-004143 · wird automatisch vergeben“. Mit „Eigene Nummer“ lässt sie sich selbst vergeben; sie darf nur einmal vorkommen.
   - **„Preis & Lieferant“:** zuerst den „Lieferant“ wählen, dann „Artikelnummer beim Lieferanten“ und „Preis in Euro“ (z. B. 1.234,50) – beides gehört immer zu einem Lieferanten. Bleibt der Preis leer, hat der Artikel noch keinen Preis.
   - **„Bilder & Unterlagen“:** ein Vorschaubild und beliebig viele Unterlagen (Datenblatt, Zulassung, Zeichnung …). Sie werden beim Anlegen mit hochgeladen.
   - **„Mehr Angaben“:** „Produktlinie“, „Beschreibung (intern)“, „VPE (Menge)“ und „Preiseinheit (Text)“.
3. „Artikel anlegen“ – das geht aus jedem Reiter, sobald ein Name drin ist. Danach öffnet sich gleich die Seite des neuen Artikels.

Gut zu wissen:

- Die automatische Nummer ist „A-“ und eine fortlaufende Zahl, eins höher als die bisher höchste. Eigene Nummern in anderem Muster (z. B. „AL-FL-x100-20“) zählen dabei nicht mit. Die angezeigte Nummer ist eine Vorschau; legt gleichzeitig jemand anderes einen Artikel an, kann es eine Nummer weiter sein.
- Lässt sich eine Datei nicht hochladen, ist der Artikel trotzdem angelegt. Die Meldung nennt die Datei; sie lässt sich auf der Artikelseite im Reiter „Bilder & Unterlagen“ nachreichen.

Alles lässt sich danach auf der Artikelseite ändern (siehe unten). Artikel lassen sich nicht löschen.

## Stammdaten bearbeiten

Auf der Artikelseite (Klick auf eine Zeile in **Vorlagen & Stammdaten → Artikel**) öffnet im Reiter „Angaben zum Teil“ der Knopf „Stammdaten bearbeiten“ die Maske. In der „Übersicht“ führt „Angaben ändern“ direkt dorthin. Dort lassen sich ändern:

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
- Nicht geändert werden Angebotstexte und Aufschlag („Angebotsfelder“), Lieferanten, Preise und Dokumente. Dafür gibt es die eigenen Reiter.
- Die „Beschreibung (intern)“ darf höchstens 255 Zeichen lang sein.
- Die Suche findet den Artikel nach dem Ändern unter den neuen Angaben, auch über die EAN.

## Aufschläge einstellen

In **Vorlagen & Stammdaten → Artikel** gibt es den Knopf **„Aufschläge“**. Ihn sehen nur Benutzer, deren Abteilung das Recht „Aufschläge ändern“ hat (Einstellungen → Abteilungen & Rechte) sowie Administratoren. Das Fenster zeigt:

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

Der Menüpunkt **Dokumente → Leistungen & Kategorien** ist etwas anderes: Dort stehen die Ordner Ihrer Leistungen. Dieselben Ordner sind die Gewerke (Kategorien), nach denen Projekte eingeordnet und ausgewertet werden.

## Was zeigt ein Artikel?

Ein Klick auf einen Artikel unter **Vorlagen & Stammdaten → Artikel** öffnet ihn. Die Seite hat fünf Reiter; der offene Reiter steht in der Adresszeile, ein Lesezeichen landet also im richtigen Reiter:

- **„Übersicht“** – Vorschaubild, „Auf einen Blick“ (Artikel-Nr., Einheit, Kategorie, Werkstoff, Maße), bester Preis und wie es im Angebot steht, dazu „Preise je Lieferant“ zum schnellen Ändern.
- **„Preise & Lieferanten“** – alle Lieferanten mit ihrer Artikelnummer, ihrem Preis, dem Abstand zum günstigsten und der Herkunft; darunter der „Preisverlauf“.
- **„Angaben zum Teil“** – Maße, Gewicht je Meter, Oberfläche je Meter, Normen und ob Feuerverzinken und Pulverbeschichten möglich sind; mit „Stammdaten bearbeiten“.
- **„Für Angebote“** – Text und Aufschlag für den Kunden (siehe unten). Ungespeicherte Änderungen bleiben beim Reiterwechsel erhalten; der Reiter zeigt dann einen Punkt.
- **„Bilder & Unterlagen“** – Vorschaubild sowie Zulassung, Zeichnung, Datenblatt oder Montageanleitung (PDF oder Bild, höchstens 10 MB) mit „Hochladen“.

## Preis eines Lieferanten eintragen oder ändern

**Preis ändern:** In der „Übersicht“ (Preise je Lieferant) oder im Reiter „Preise & Lieferanten“ den Preis in der Zeile des Lieferanten anklicken, den neuen Preis tippen (z. B. 12,50) und Enter drücken oder auf das Häkchen klicken. Escape oder das X bricht ab. Genauso ändert der Stift neben „Dessen Artikelnummer“ die Nummer beim Lieferanten.

**Neuer Lieferant:** Im Reiter „Preise & Lieferanten“ „Preis von weiterem Lieferanten eintragen“ klicken, den Lieferanten wählen, Nummer und Preis eintragen und speichern.

Der bisherige Preis bleibt im „Preisverlauf“ erhalten. Neue Angebotspositionen rechnen mit dem günstigsten aktuellen Preis; Angebote und Rechnungen, die schon geschrieben sind, ändern sich nicht. Preise aus Angeboten und Rechnungen von Lieferanten, die per E-Mail kommen, trägt das Programm oft selbst ein; im Preisverlauf steht dann die Herkunft.

## Verkaufspreis und Text für das Angebot festlegen

Beim Artikel (**Vorlagen & Stammdaten → Artikel**) im Reiter „Für Angebote“:

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
