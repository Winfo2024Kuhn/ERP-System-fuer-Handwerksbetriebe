# E-Mail-Center

Unter **Kommunikation → E-Mail Center** stehen die E-Mails aller Postfächer des Betriebs, die man sehen darf. Das Programm holt neue E-Mails jede Minute ab und ordnet sie, wo es geht, selbst einem Projekt, einer Anfrage oder einem Lieferanten zu. Postfächer richtet ein Admin unter [Einstellungen](einstellungen.md) ein.

## Welche Ordner gibt es?

Links in **Kommunikation → E-Mail Center**:
- „Posteingang“: neue E-Mails, die noch nirgends zugeordnet sind.
- „Gesendet“, „Entwürfe“, „Markiert“ (mit Stern), „Newsletter“, „Spam“, „Papierkorb“.
- Unter „Zugeordnet“: „Nicht zugeordnet“ (E-Mails von bekannten Kunden-Adressen, die noch keinem Vorgang zugeordnet sind), „Projekte“, „Anfragen“, „Lieferanten“ und „Steuerberater“.

Die Zahl neben einem Ordner nennt die ungelesenen E-Mails. Alle Postfächer stehen gemischt in denselben Ordnern; ein kleines Schild an jeder E-Mail (z. B. „info@“) zeigt, in welchem Postfach sie liegt. „Gelesen“ gilt für jeden Benutzer einzeln, der Stern („Markiert“) für alle gemeinsam.

## E-Mails suchen

Oben in der Liste (**Kommunikation → E-Mail Center**):
- „Ordner“ sucht im geöffneten Ordner, „Alle Ordner“ in allen (ab 2 Zeichen) – in Betreff, Absender und Text.
- „Alle“ / „Ungelesen“ / „Gelesen“ filtert nach Lesestatus.
- Der Pfeil-Knopf dreht die Reihenfolge (neueste oder älteste zuerst).
- „Alle gelesen“ markiert alle ungelesenen im Ordner als gelesen.

## E-Mail lesen und Anhänge öffnen

Ein Klick auf eine E-Mail zeigt rechts den ganzen Verlauf. Unter „Gehört zu“ steht, zu welchem Projekt, welcher Anfrage oder welchem Lieferanten sie gehört; ein Klick darauf öffnet den Vorgang. Ältere zitierte Teile sind eingeklappt („Zitierten Verlauf anzeigen“).

Anhänge stehen unter der E-Mail. PDFs öffnen sich in einer Vorschau mit „Download“, Bilder in einem Bildbetrachter, andere Dateien werden heruntergeladen. Bei mehreren Anhängen lädt „Alle Anhänge herunterladen“ (im Menü „…“) alle als ZIP-Datei.

## E-Mail einem Projekt oder einer Anfrage zuordnen

Bei der geöffneten E-Mail im Menü „…“ → „Zuordnen“. Im Fenster „E-Mail zuordnen“ „Projekt“ oder „Anfrage“ wählen; passende Vorgänge stehen unter „Vorschläge“, sonst darunter suchen. Der ganze Verlauf wandert mit. Mehrere markierte E-Mails lassen sich mit „Zuordnen“ auf einmal zuordnen.

Automatisch ordnet das Programm so zu: Antworten folgen der vorigen E-Mail. E-Mails von der Adresse oder Firma eines Lieferanten gehen zum Lieferanten. E-Mails von einer Kunden-Adresse gehen zum passenden Projekt bzw. zur passenden Anfrage. Ist das nicht eindeutig, bleibt die E-Mail unter „Nicht zugeordnet“.

## Neue E-Mail schreiben

1. **Kommunikation → E-Mail Center** → „Neue E-Mail“.
2. „An“ (Name, Firma oder Adresse), bei Bedarf „+ CC“.
3. „Von“: das Postfach, über das die E-Mail rausgeht. Voreingestellt ist das eigene Postfach, sonst das Hauptpostfach.
4. „Betreff“ und „Nachricht“. Die eigene Standard-Signatur steht schon darunter.
5. „Gehört zu“: Projekt oder Anfrage wählen. Dann schlägt das Programm die Adressen des Kunden vor, und „Dateien aus Projekt hinzufügen“ hängt Dateien aus dem Vorgang an.
6. Anhänge in das Feld ziehen oder anklicken. Große Fotos werden verkleinert; der Balken zeigt, wie viel Platz noch ist.
7. „E-Mail senden“.

Ohne „Gehört zu“ fragt das Programm vor dem Senden nach. Mit „Einzeln verschicken – jeder sieht nur sich selbst“ bekommt jeder Empfänger eine eigene E-Mail. „KI-Optimierung“ formuliert den eigenen Text neu, wenn ein KI-Schlüssel eingerichtet ist.

## Antworten und Weiterleiten

Bei einer geöffneten E-Mail „Antworten“ oder „Allen antworten“; „Weiterleiten“ steht im Menü „…“. Antworten gehen über das Postfach raus, in dem die E-Mail ankam, und bleiben beim selben Projekt bzw. derselben Anfrage. Beim Weiterleiten gehen die Anhänge der ursprünglichen E-Mail nicht mit – bei Bedarf neu anhängen.

## Entwürfe

Eine E-Mail wird beim Schreiben automatisch als Entwurf gespeichert; unten steht „Entwurf gespeichert“. Einen eigenen Speichern-Knopf gibt es nicht. Entwürfe stehen im Ordner „Entwürfe“ und lassen sich per Klick weiterschreiben oder mit dem Papierkorb löschen.

## Löschen, Spam und Newsletter

Im Menü „…“ einer E-Mail:
- „In Papierkorb“ (im Papierkorb: „Wiederherstellen“ oder „Endgültig löschen“ – dann auch vom Mailserver),
- „Verschieben nach“,
- „Als Spam markieren“ bzw. „Kein Spam“,
- „Kein Newsletter“ / „Newsletter bestätigen“,
- „Absender sperren“: löscht alle bisherigen E-Mails dieses Absenders und blockiert neue.

Mehrere E-Mails markiert man mit Strg- bzw. Cmd-Klick oder Umschalt-Klick; E-Mails lassen sich auch auf einen Ordner links ziehen. Papierkorb, Spam und Newsletter leert das Programm nach 30 Tagen von selbst.

## Rechnungen von Lieferanten aus E-Mails

Dafür ist nichts zu tun: Kommt eine E-Mail von einem bekannten Lieferanten, liest das Programm PDF- und XML-Anhänge aus und legt sie beim Lieferanten im Reiter „Dokumente“ ab (siehe [Lieferanten](lieferanten.md)). Voraussetzung ist, dass die E-Mail-Adresse beim Lieferanten eingetragen ist.

## E-Mail-Vorlagen für Angebote, Rechnungen und Mahnungen

Unter **Kommunikation → E-Mail Vorlagen** stehen die Texte, die beim Verschicken von Dokumenten aus dem Dokument-Editor und bei automatischen E-Mails verwendet werden – gruppiert nach „Dokumente“, „Mahnwesen“ und „Webseite & Anfragen“. Pro Dokumentart gibt es genau eine Vorlage.

„Neue Vorlage“ oder „Bearbeiten“: „Anzeigename“, „Dokumenttyp“, „Betreff“ und „Nachricht“. Platzhalter wie `{{KUNDENNAME}}`, `{{DOKUMENTNUMMER}}` oder `{{BETRAG}}` setzt das Programm beim Versand ein. Nur eine Vorlage mit Häkchen „Vorlage aktiv“ wird benutzt. Im normalen Schreibfenster des E-Mail-Centers lassen sich Vorlagen nicht einfügen.

## Signatur einrichten

Unter **Kommunikation → E-Mail-Einstellungen**, Reiter „Signaturen“ → „Neue Signatur“: Name, Kontakt, Bilder und Aussehen eintragen, rechts erscheint die Vorschau. „Speichern & als meine Standard-Signatur“ macht sie zur Signatur unter jeder neuen E-Mail. Admins sehen alle Signaturen, legen die System-Signatur für automatische E-Mails fest und können eine Firmen-Vorlage bestimmen.

## Abwesenheitsnotiz einrichten

Unter **Kommunikation → E-Mail-Einstellungen**, Reiter „Abwesenheitsnotizen“ → „Neue Abwesenheit“:
1. „Titel“ (z. B. Betriebsferien Weihnachten) und „Zeitraum“.
2. Die Postfächer anhaken, für die sie gilt.
3. Betreff und Text der automatischen Antwort; `{{start}}` und `{{ende}}` setzen die Daten ein.
4. „Abwesenheit planen“.

Die Notiz schaltet sich zum Zeitraum selbst ein und aus. Jeder Absender bekommt pro Notiz nur eine Antwort.

## Wer sieht welches Postfach?

Das Hauptpostfach und das Postfach für Rechnungen & Mahnungen sieht jeder im Betrieb. Bei allen anderen legt ein Admin unter **Einstellungen → Abteilungen & Rechte** fest, ob „Alle im Betrieb“ sie sehen oder nur bestimmte Abteilungen und Benutzer. Der Inhaber eines Postfachs und Admins sehen es immer. E-Mails, die einem Projekt, einer Anfrage oder einem Lieferanten zugeordnet sind, sieht jeder in dessen Reiter „E-Mails“.
