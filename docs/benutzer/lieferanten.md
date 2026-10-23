# Lieferanten

Unter **Vorlagen & Stammdaten → Lieferanten** stehen alle Lieferanten mit ihren Belegen: Angebote, Auftragsbestätigungen, Lieferscheine, Rechnungen und Werkstoffzeugnisse. Die meisten Belege kommen von selbst aus den E-Mails. Jeder angemeldete Benutzer darf Lieferanten anlegen und ändern; welche Belege jemand sieht, hängt an den Rechten seiner Abteilung. Rechnungen von Lieferanten bezahlt man unter [Offene Posten](offene-posten-und-mahnungen.md).

## Lieferanten finden

In **Vorlagen & Stammdaten → Lieferanten** wird beim Tippen sofort gefiltert: „Freitext“ (Name, zweiter Name, Telefon, Ort, Straße, Vertreter), „Name“, „Typ“ und „Ort“; „Filter zurücksetzen“ leert alle Felder. Nach E-Mail-Adressen sucht das Freitextfeld nicht. Die Lieferanten stehen als Karten nach Namen sortiert, 12 pro Seite. Ein Klick öffnet den Lieferanten, der Stift beim Drüberfahren öffnet gleich das Bearbeiten.

## Neuen Lieferanten anlegen

1. **Vorlagen & Stammdaten → Lieferanten** → „Neuer Lieferant“.
2. „Name“ eintragen (Pflicht). Ein Lieferant mit gleichem Namen lässt sich nicht doppelt anlegen.
3. Bei Bedarf ausfüllen:
   - „Zweiter Name (optional)“: wie die Firma im Betrieb genannt wird, z. B. „Kfz Meier“ – darüber wird sie auch gefunden.
   - „Rollen“: was der Lieferant liefert, z. B. „Stahlhandel“ oder „Beschichtung / Verzinkerei“. Danach schlägt das Programm ihn beim Preiseintragen für passende Artikel vor.
   - „Eigene Kundennummer (beim Lieferanten)“.
   - „Standard-Kostenstelle“: neue Rechnungen dieses Lieferanten werden automatisch ganz auf diese Kostenstelle gebucht.
   - „Wird immer im Voraus bezahlt“: Rechnungen, die per E-Mail kommen, gelten sofort als bezahlt und stehen nicht in den Offenen Posten.
   - Adresse, Telefon, Mobil, Vertreter.
   - „E-Mail-Adressen“: Adresse eintragen und „Hinzufügen“. Das Programm ergänzt die info@-Adresse derselben Firma; sie lässt sich wieder entfernen.
4. „Speichern“.

## Warum die E-Mail-Adressen eines Lieferanten wichtig sind

Über die E-Mail-Adressen erkennt das Programm, welche E-Mails von einem Lieferanten kommen. Solche E-Mails landen im E-Mail-Center unter „Lieferanten“, und ihre PDF- und XML-Anhänge werden zu Belegen im Reiter „Dokumente“ des Lieferanten. Nach dem Speichern eines Lieferanten sucht das Programm außerdem ältere, noch nicht zugeordnete E-Mails dieser Firma heraus und ordnet sie zu.

## Lieferant ändern oder löschen

In der Lieferantenansicht oben „Bearbeiten“ klicken, ändern, „Speichern“. Einen Lieferanten löschen geht nicht.

## Was zeigt die Lieferantenansicht?

Ein Klick auf einen Lieferanten unter **Vorlagen & Stammdaten → Lieferanten** öffnet ihn. Oben stehen Name, Rollen, „Zahlung im Voraus“ (falls gesetzt) und vier Zahlen:
- „Gesamtkosten“: alle Rechnungen netto,
- „Bestellungen“: Anzahl der Auftragsbestätigungen,
- „Artikel“: Artikel, für die dieser Lieferant einen Preis hat,
- „Lieferzeit Ø“: aus den Auftragsbestätigungen berechnet.

Reiter: „E-Mail-Verlauf“ (die neuesten 50 E-Mails, nur lesen), „Anrufe“ (nur mit dem Recht „Anrufe & Anrufbeantworter“), „Dokumente“, „Notizen“ und „Reklamationen“. Rechts stehen die „Kontaktdaten“ mit „Standard-Kostenstelle“, „Bezahlung“ und einer Karte des Standorts.

## Belege eines Lieferanten finden

In der Lieferantenansicht, Reiter „Dokumente“:
- „Intelligente Suche“ findet Belege nach Nummer, Referenznummer, Material oder Charge (z. B. S235JR), Betrag (z. B. 952,00) oder Datum (z. B. 25.9.2025).
- Die Auswahl daneben grenzt auf Angebote, Auftragsbestätigungen, Lieferscheine, Werkstoffzeugnisse, Rechnungen oder Gutschriften ein.
- „Dokumentenketten“ zeigt zusammengehörige Belege untereinander (Auftragsbestätigung → Lieferschein → Rechnung). Der Pfeil klappt die Artikelpositionen auf.
- „Einzeldokumente“ sind Belege ohne Kette, sortierbar nach Nummer, Datum, Betrag und Typ.

„Manuelle Prüfung erforderlich“ (rot) heißt: Das Auslesen war unsicher – bitte die Angaben prüfen. „Kein Projekt zugeordnet“ (gelb) heißt: Der Beleg ist noch keinem Projekt und keiner Kostenstelle zugeordnet.

## Beleg von Hand hochladen

In der Lieferantenansicht, Reiter „Dokumente“ → „Dokument importieren“:
1. Ein PDF oder XML wählen. Mit eingerichteter KI liest das Programm die Angaben selbst aus. Ohne KI liest es nur E-Rechnungen; bei anderen Dateien die Angaben von Hand eintragen.
2. Angaben prüfen: „Dokumenttyp“, „Dokumentnummer“ (Pflicht), „Dokumentdatum“, Beträge, „Zahlungsziel“, „Bestellnummer“, „Zahlungsart“, „Referenznummer“, Skonto und „Bereits bezahlt“.
3. „Importieren & Speichern“.

Eine Dokumentnummer, die es bei diesem Lieferanten schon gibt, lehnt das Programm ab. Bei Lieferanten mit Zahlung im Voraus beim Hochladen „Bereits bezahlt“ selbst anhaken.

## Beleg prüfen und korrigieren

In der Lieferantenansicht, Reiter „Dokumente“, auf die Nummer eines Belegs (oder auf eine Zeile in einer Kette) klicken. Im Fenster „Dokument bearbeiten“ steht links das PDF, rechts die Angaben: Typ, Nummer, Datum, Liefertermin, Beträge, Bestell- und Referenznummer, bei Rechnungen die „Zahlungsbedingungen“ (Zahlungsziel, Skonto, „Bereits bezahlt“, „Mit Skonto bezahlt“), bei Werkstoffzeugnissen Werkstoff, Charge und Abmessung. „Speichern“ sichert die Änderungen.

Es kann immer nur eine Person einen Beleg bearbeiten. Arbeitet gerade jemand daran, steht dort sein Name, und die Felder sind gesperrt.

## Rechnung, Lieferschein und Auftragsbestätigung verbinden

Das Programm verbindet zusammengehörige Belege meist selbst. Fehlt eine Verbindung:
- Bei einem Einzeldokument „Zu Kette zuordnen“ wählen, oder in einer Kette „Dokument hinzufügen“.
- Im Fenster schlägt das Programm passende Belege mit Trefferquote vor. Den richtigen anklicken (mit „Vorschau“ prüfen) und „Gehört dazu“ wählen.

Lösen lässt sich eine falsche Verbindung unter **Projektmanagement → Bestellungen**.

## Lieferantenrechnung einem Projekt zuordnen

Ist eine Rechnung schon zugeordnet, steht in der Lieferantenansicht (Reiter „Dokumente“) beim Beleg „Zuordnungen“ mit „Bearbeiten“. Im Fenster lässt sich die Rechnung prozentual, mit festen Beträgen oder nach Positionen auf Projekte („Projekt hinzufügen...“) und Kostenstellen („Kostenstelle...“) verteilen, dann „Zuordnen & Abschließen“.

Eine Rechnung zum ersten Mal einem Projekt zuordnen geht unter **Projektmanagement → Bestellungen**. Rechnungen von Lieferanten mit „Standard-Kostenstelle“ ordnet das Programm selbst zu.

## Reklamation an einen Lieferanten

In der Lieferantenansicht, Reiter „Reklamationen“ → „Neue Reklamation“: Grund beschreiben, bei Bedarf den Lieferschein suchen und Fotos hinzufügen, dann „Reklamation erstellen“. Mit „Per E-Mail melden“ gehen Beschreibung, Fotos und Lieferschein an den Lieferanten. Ist die Sache erledigt, „Abschließen“.

## Beleg löschen

Löschen darf nur ein Admin: in der Lieferantenansicht, Reiter „Dokumente“, beim Drüberfahren über ein Einzeldokument der Papierkorb. Rechnungen und Gutschriften lassen sich wegen der Aufbewahrungspflicht nicht löschen. War ein Beleg falsch erkannt, zuerst den Typ auf „Sonstiges“ ändern.

## Wer sieht welche Belege?

Ein Admin legt unter **Einstellungen → Abteilungen & Rechte** je Abteilung fest, welche Belegarten ihre Mitarbeiter sehen dürfen („Sehen“) und welche sie mit dem Handy hochladen dürfen („Scannen“). Admins sehen alles. Wer keinem Mitarbeiter zugeordnet ist, sieht keine Belege. Ohne das Recht für Rechnungen stehen die „Gesamtkosten“ eines Lieferanten auf 0.

Preise eines Lieferanten pflegt man beim Artikel (siehe [Artikel](artikel.md)).
