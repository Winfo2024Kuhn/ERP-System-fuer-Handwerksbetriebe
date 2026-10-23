# Offene Posten und Mahnungen

Unter **Finanzen & Controlling → Offene Posten** stehen alle Rechnungen, die noch nicht bezahlt sind. Die Seite hat zwei Reiter: „Ausgangsrechnungen“ (Rechnungen an Kunden) und „Eingangsrechnungen“ (Rechnungen von Lieferanten). Mahnungen an Kunden verschickt das Programm automatisch, sobald ein Admin das Mahnverfahren eingeschaltet hat.

## Welche Kundenrechnungen stehen in den offenen Posten?

Unter **Finanzen & Controlling → Offene Posten**, Reiter „Ausgangsrechnungen“, steht jede Rechnung, Abschlags-, Teil- und Schlussrechnung, sobald sie gebucht ist – also gedruckt, als PDF gespeichert oder per E-Mail verschickt (siehe [Rechnungen schreiben und stornieren](rechnungen.md)). Entwürfe stehen hier nicht. Eine stornierte Rechnung verschwindet von selbst, Stornorechnungen erscheinen nicht. Gutschriften erscheinen nicht als eigene Zeile: Sie mindern den Betrag ihrer Rechnung. Die Spalte „Betrag“ zeigt dann den Rest, darunter klein „Rechnung … · Gutschrift −…“, und die „Offene Summe“ rechnet mit dem Rest. Eine Gutschrift über die ganze Rechnung erledigt sie. Bezahlte Kundenrechnungen blendet die Seite aus; der Schalter „Bezahlte anzeigen“ holt die der letzten 12 Monate dazu.

Oben stehen „Offene Summe“, „Rechnungen“, „Überfällig“ und „Mit Mahnung“. Die Tabelle zeigt „Projekt“ (Klick öffnet das Projekt), „Kunde“, „Rechnungsnr.“, „Datum“, „Versandt“, „Fällig“, „Tage“, „Betrag“, „Dokument“ und „Status“. Die neuesten Rechnungen stehen oben. Suche und Filter gibt es hier nicht.

## Wann ist eine Rechnung überfällig?

Fällig ist eine Rechnung am Rechnungsdatum plus Zahlungsziel. Das Zahlungsziel steht in der Rechnung (Summenzeile im Dokument-Editor), sonst gilt das des Kunden.

In **Finanzen & Controlling → Offene Posten** zeigt die Spalte „Tage“, wie viele Tage noch bleiben: Gelb heißt „Heute fällig“, Rot heißt überfällig („Überfällig seit … Tagen“ beim Drüberfahren). Überfällige Rechnungen meldet außerdem die Glocke oben rechts unter „Finanzen“; ein Klick springt direkt zur Zeile.

## Kundenrechnung als bezahlt markieren

1. **Finanzen & Controlling → Offene Posten**, Reiter „Ausgangsrechnungen“.
2. In der Zeile der Rechnung in der Spalte „Status“ das Häkchen „Bezahlt“ setzen.

Die Rechnung verschwindet sofort aus der Liste. Es gibt keine Rückfrage.

Ein versehentlich gesetztes „Bezahlt“ nimmt man zurück, indem man „Bezahlte anzeigen“ einschaltet und bei der Rechnung das Häkchen „Bezahlt“ wieder entfernt. Die Rechnung steht dann wieder in den offenen Posten und kann wieder gemahnt werden; ein Projekt, das dadurch nicht mehr voll bezahlt ist, gilt wieder als offen. Stornierte Rechnungen erscheinen dort nicht und bleiben erledigt.

Teilzahlungen, ein Zahlungsdatum und Skonto gibt es bei Kundenrechnungen nicht – eine Rechnung ist entweder offen oder bezahlt. Einen Abgleich mit dem Bankkonto oder einen Kontoauszug-Import gibt es nicht.

Sind alle Rechnungen eines Projekts bezahlt und decken sie den Auftrag ab, zeigt das Projekt „Bezahlt“ und wird automatisch „Beendet“.

## Barzahlung über die Kasse buchen

Zahlt ein Kunde bar, geht es auch über **Finanzen & Controlling → Belege & Kasse**, Reiter „Kassenbuch“ → „Neue Buchung“ → „Geld eingenommen“ („Kunde hat bar bezahlt“). Im Feld „Zu welcher Rechnung?“ die offene Rechnung auswählen und buchen. Die Rechnung wird dann automatisch als bezahlt markiert. Wird diese Kassenbuchung später storniert, ist die Rechnung wieder offen.

## Wie funktionieren Mahnungen?

Mahnungen verschickt das Programm **automatisch per E-Mail** – von Hand lässt sich keine Mahnung anlegen. Es gibt drei Stufen: „Zahlungserinnerung“, „1. Mahnung“ und „2. Mahnung“. Nach der 2. Mahnung schickt das Programm nichts mehr.

Gemahnt wird jeden Morgen um 9 Uhr, und nur, wenn:
- das automatische Mahnverfahren eingeschaltet ist (siehe nächster Abschnitt),
- die Rechnung mit dem Programm geschrieben wurde, zu einem Projekt gehört, gebucht und nicht bezahlt ist,
- der Kunde eine E-Mail-Adresse hat (es wird die erste genommen),
- für die Stufe eine aktive E-Mail-Vorlage besteht.

Die Mahnung bekommt die Rechnungsnummer mit Endung `-Z` (Zahlungserinnerung), `-M1` oder `-M2`. Das PDF zeigt die offene Forderung: Rechnungsnummer, Rechnungsdatum, ursprüngliche Fälligkeit, Tage überfällig, offener Betrag und ein neues Zahlungsziel. Mahngebühren oder Verzugszinsen rechnet das Programm nicht dazu.

## Automatisches Mahnen einschalten und einstellen

Das darf nur ein Admin:

1. **Vorlagen & Stammdaten → Firma**, Reiter „Firmendaten“, Abschnitt „Automatisches Mahnverfahren“.
2. Häkchen „Aktiv“ setzen.
3. Die Abstände eintragen: „Tage nach Fälligkeit“ (bis zur Zahlungserinnerung), „Tage nach der Zahlungserinnerung“ (bis zur 1. Mahnung) und „Tage nach der 1. Mahnung“ (bis zur 2. Mahnung). Voreingestellt sind je 7 Tage.
4. Unter „Neues Zahlungsziel auf der Mahnung (Tage)“ festlegen, wie viel Zeit der Kunde auf jeder Mahnung noch bekommt.

Rechnungen, die beim Einschalten schon überfällig sind, beginnen mit der Zahlungserinnerung.

Den Text der Mahn-E-Mails pflegt man unter **Kommunikation → E-Mail Vorlagen** in der Gruppe „Mahnwesen“. Verschickt wird über das Postfach, das unter **Einstellungen → E-Mail** mit „Für Rechnungen & Mahnungen“ markiert ist, sonst über das Hauptpostfach. Die Mahn-E-Mail wird im Projekt abgelegt.

## Verschickte Mahnungen ansehen

In **Finanzen & Controlling → Offene Posten** steht jede verschickte Mahnung als gelbe Zeile unter ihrer Rechnung („↳ Zahlungserinnerung“, „↳ 1. Mahnung“ …). „Fällig“ zeigt dort das neue Zahlungsziel, „Versandt“ den Versandtag; das Symbol „Mahnung öffnen“ zeigt das PDF. An der Rechnungsnummer hängt ein gelbes Zeichen mit der letzten Mahnstufe. Mahnungen stehen auch im Projekt (Reiter „Geschäftsdokumente“) und beim Kunden (Reiter „Dokumente“).

## Mahnung vorher ansehen (ohne Versand)

Unter **Projektmanagement → Dokumente**, Reiter „Ausgang“, hat jede Rechnung in der Spalte „Mahn-Vorschau“ die Knöpfe „ZE“, „1.“ und „2.“. Sie zeigen das PDF der jeweiligen Mahnstufe – nur zur Ansicht, nichts wird verschickt.

## Lieferantenrechnung als bezahlt markieren

1. **Finanzen & Controlling → Offene Posten**, Reiter „Eingangsrechnungen“.
2. In der Spalte „Status“ das Häkchen setzen. Als Zahltag gilt heute.

**Skonto:** Wird innerhalb der Skontofrist abgehakt, zieht das Programm den Skonto selbst ab; die Spalte „Brutto“ zeigt dann „gezahlt: … €“. Die Spalte „Skonto“ zeigt die verbleibenden Tage („noch … Tage“, gelb und rot, wenn es knapp wird), „abgelaufen“ oder „✓ genutzt“. Skonto-Prozent und -Tage stehen in der Lieferantenrechnung unter „Zahlungsbedingungen“.

**Zurücknehmen:** „Bezahlte anzeigen“ anhaken und das Häkchen wieder entfernen.

Gutschriften von Lieferanten stehen eingerückt unter der Rechnung, zu der sie gehören. Überfällige Lieferantenrechnungen sind rot markiert; die Glocke meldet sie ab drei Tagen vor Fälligkeit.

## Lieferantenrechnungen genehmigen und wer was sieht

In **Finanzen & Controlling → Offene Posten**, Reiter „Eingangsrechnungen“, gibt das Schild-Symbol in der Spalte „Genehmigt“ eine Rechnung frei. Noch nicht genehmigte Rechnungen lassen sich über das Dokument-Symbol korrigieren, genehmigte nur noch ansehen.

Die Eingangsrechnungen hängen an Abteilungsrechten, die ein Admin unter **Einstellungen → Abteilungen & Rechte** vergibt: „Rechnungen genehmigen“ (sieht alle und darf genehmigen) oder „Genehmigte Rechnungen sehen“. Ohne eines davon bleibt der Reiter leer. Den Reiter „Ausgangsrechnungen“ sieht jeder angemeldete Benutzer und darf dort Rechnungen als bezahlt markieren.
