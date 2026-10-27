# Einstellungen, Firma und Benutzer

Die Grundeinrichtung des Programms darf nur ein Admin vornehmen. Er findet sie unter **Vorlagen & Stammdaten → Einstellungen** (Reiter „E-Mail“, „Dateien“, „KI-Funktionen“, „Zeiterfassung“, „Telefon“, „Abteilungen & Rechte“), unter **Vorlagen & Stammdaten → Firma** und oben rechts im Benutzermenü unter „Benutzer verwalten“. Für alle anderen Benutzer sind diese Menüpunkte ausgeblendet. Jeder Reiter speichert für sich; ein grünes Häkchen am Reiter heißt „Eingerichtet“.

## Ersteinrichtung

1. Auf der Anmeldeseite im Reiter „Konto anlegen“ das erste Konto erstellen. Es wird automatisch Admin. Danach verschwindet der Reiter; weitere Konten entstehen nur noch über „Benutzer verwalten“.
2. Nach der Anmeldung öffnet sich die Seite „SYSTEM EINRICHTEN“. Mindestens das E-Mail-Postfach einrichten (siehe nächster Abschnitt), die KI ist freiwillig.
3. „Einrichtung abschließen“. Solange das E-Mail-Postfach unvollständig ist, kommt der Admin immer wieder auf diese Seite.

## E-Mail-Postfach einrichten

Unter **Einstellungen → E-Mail**, Karte „Postfächer“ → „Neues Postfach“:

1. „E-Mail-Adresse“ und „Passwort“ eintragen, optional „Angezeigter Name“ (den sieht der Empfänger).
2. Beim **ersten** Postfach sind die „Server-Einstellungen“ schon aufgeklappt: „Server für den Versand (SMTP)“ und „Server für den Abruf (IMAP)“ sind Pflicht (meist Ports 465 und 993). Weitere Postfächer übernehmen die Server vom Hauptpostfach.
3. Mit „Verbindung testen“ prüfen (optional mit einer Adresse für eine Test-Mail).
4. „Speichern“.

Häkchen im Postfach:
- „Hauptpostfach“: darüber geht alles raus, wofür kein anderes Postfach passt. Es gibt genau eins.
- „Für Rechnungen & Mahnungen“: Rechnungen, Mahnungen, Angebote und Auftragsbestätigungen gehen über dieses Postfach raus.
- „Eingeschaltet“: aus heißt keine Abholung und nicht als Absender wählbar.
- „Läuft aus“: E-Mails kommen noch an, neue gehen aber nicht mehr darüber raus.

Ein Postfach, das schon E-Mails hat, lässt sich nicht löschen, nur ausschalten. Welcher Benutzer welches Postfach als eigenes hat, legt man unter „Benutzer verwalten“ fest.

## Wer sieht welches Postfach?

Unter **Einstellungen → Abteilungen & Rechte**, Karte „E-Mail-Postfächer – wer sieht welches Postfach?“, für jedes Postfach „Alle im Betrieb“ oder „Nur bestimmte“ wählen und bei „Nur bestimmte“ Abteilungen und Benutzer anhaken, dann „Speichern“. Hauptpostfach und Rechnungs-Postfach sieht immer jeder; der Inhaber und Admins sehen ihr Postfach immer.

## Abteilungen anlegen, umbenennen, löschen

Unter **Einstellungen → Abteilungen & Rechte**, ganz oben die Karte „Abteilungen“:

- **Anlegen:** Namen ins Feld „Neue Abteilung …“ tippen (z. B. „Montage“) und „Abteilung anlegen“. Die neue Abteilung erscheint sofort darunter bei „Rechte pro Abteilung“; dort ihre Rechte anhaken. Zwei Abteilungen mit demselben Namen gibt es nicht (Groß-/Kleinschreibung zählt nicht).
- **Umbenennen:** Stift neben der Abteilung. Nur der Name ändert sich; Mitarbeiter, Arbeitsgänge und Rechte bleiben zugeordnet.
- **Löschen:** Papierkorb. Das geht erst, wenn kein Mitarbeiter (auch kein ausgeschiedener) und kein Arbeitsgang mehr zur Abteilung gehört; sonst sagt das Programm, was noch zugeordnet ist. Mit der Abteilung verschwinden ihre Rechte und ihre Freigaben für E-Mail-Postfächer.

Welcher Mitarbeiter zu welcher Abteilung gehört, legt man beim Mitarbeiter fest. Die Arbeitsgänge einer Abteilung pflegt der Admin unter **Vorlagen & Stammdaten → Arbeitsgänge**; diese Seite sehen nur Admins, und nur Admins dürfen Arbeitsgänge und Stundensätze anlegen, ändern oder löschen. Die Auswahl der Arbeitsgänge beim Stempeln und in der Zeiterfassung sehen weiterhin alle.

## Rechte pro Abteilung

Unter **Einstellungen → Abteilungen & Rechte**, Karte „Rechte pro Abteilung“, für jede Abteilung anhaken und „Speichern“:

- **„Dokumente & Belege“:** je Belegart (Angebot, Auftragsbestätigung, Lieferschein, Rechnung, Gutschrift, Sonstiges, Belege für Kasse und Bank, Werkstoffzeugnis) „Sehen“ und „Scannen“ (mit dem Handy hochladen).
- **„Eingangsrechnungen“:** „Rechnungen genehmigen“ oder „Genehmigte Rechnungen sehen“.
- **„Zeiterfassung, Monatsabschluss & Telefon“:** „Zeiterfassung im Menü sehen“, „Monate abschließen und wieder öffnen“ sowie „Anrufe & Anrufbeantworter“.
- **„Preise“:** „Aufschläge ändern“. Wer das Recht hat (die Abteilung angehakt oder Admin), sieht in der Artikelübersicht den Knopf „Aufschläge“. Alle anderen sehen den Knopf nicht. Der Aufschlag direkt am Artikel („Angebotsfelder“) bleibt davon unberührt und kann weiterhin jeder ändern. Neue Abteilungen haben das Recht zunächst nicht.
- **„Push-Nachrichten aufs Handy“:** „Kunde hat angenommen“ und „Neue Anfrage über Webseite“.

Die Rechte wirken über den Mitarbeiter, mit dem ein Benutzer verknüpft ist. Ein Benutzer ohne verknüpften Mitarbeiter bekommt diese Rechte nicht – auch ein Admin braucht für Monatsabschluss und Telefon ein solches Abteilungsrecht.

„Zeiterfassung im Menü sehen“ steuert, wer am PC den Bereich **Zeiterfassung** bekommt: Ohne Haken verschwindet er aus dem Menü, und das Programm gibt Zeiten, Abwesenheiten, Urlaubsanträge, Lange Krankheit und Monatsabschlüsse anderer Mitarbeiter an diesen Zugang nicht mehr heraus. Auch die Arbeitszeit im Mitarbeiter-Stammblatt und die offenen Urlaubsanträge in der Glocke sieht er dann nicht. Im Termin-Kalender sieht er kranke Kollegen nur als „Abwesend“. Administratoren und die Geschäftsführung sehen die Zeiterfassung immer. Nach dem Update ist der Haken bei allen Abteilungen aus: Zunächst sehen nur Administratoren und die Geschäftsführung die Zeiterfassung und die Krankheit anderer. Hake danach die Abteilungen an, die die Zeiterfassung brauchen (meist das Büro). Ebenfalls ohne Zugang sind Zugänge ohne verknüpften Mitarbeiter (außer Admin), Mitarbeiter ohne Abteilung und inaktive Mitarbeiter. Wer „Monate abschließen“ soll, braucht zusätzlich diesen Haken. Die Handy-App (Stempeln, Urlaub beantragen) ist davon nicht betroffen. Abteilungen legt man oben im selben Reiter an (siehe „Abteilungen anlegen, umbenennen, löschen“) und weist sie beim Mitarbeiter zu.

## Ablageort für Zeichnungen und Tabellen

Unter **Einstellungen → Dateien**, Karte „Wo sollen Zeichnungen und Dateien liegen?“, den „Ordner auf diesem Rechner“ eintragen (z. B. C:\Zeichnungen), mit „Ordner prüfen“ testen und mit „Datei-Ordner speichern“ sichern. Optional die „Netzwerk-Adresse für Kollegen“ (beginnt mit \\). Dort legt das Programm Zeichnungen (HiCAD, Tenado) und Excel-Tabellen aus den Projekten ab. „So gibst du den Ordner im Netzwerk frei“ erklärt die Freigabe unter Windows.

Läuft auf dem Server die **Ordner-Freigabe des ERP** (eingerichtet vom Admin, siehe `deployment/docker-server/README.md`), zeigt die Karte stattdessen den Kasten „Ordner-Freigabe des ERP ist eingeschaltet“ mit der Adresse (z. B. `\\erp-dateien.….ts.net\CADdrawings`). „Diese Adresse eintragen“ klicken und speichern – eine Windows-Freigabe braucht es dann nicht. Der Ordner ist nur über Tailscale erreichbar, im Büro wie unterwegs; die tägliche Sicherung nimmt ihn mit.

## Dateien direkt am PC öffnen (Excel, HiCAD, Tenado)

Damit eine Excel-Tabelle oder Zeichnung aus einem Projekt oder einer Anfrage direkt im passenden Programm aufgeht statt im Download-Ordner zu landen, braucht jeder PC einmal ein kleines Hilfsprogramm. Unter **Einstellungen → Dateien**, Karte „Dateien direkt am PC öffnen“, gibt es dafür „Für Windows herunterladen“ und „Befehl für Mac erstellen“ (geht erst, wenn die Netzwerk-Adresse eingetragen und gespeichert ist).

- **Windows:** Die heruntergeladene Datei doppelt anklicken. Warnt Windows („Der Computer wurde durch Windows geschützt“), auf „Weitere Informationen“ und „Trotzdem ausführen“ klicken. Administrator-Rechte braucht es nicht.
- **Mac:** „Befehl für Mac erstellen“ und „Kopieren“ klicken. Am Mac das Programm „Terminal“ öffnen (⌘ + Leertaste, „Terminal“ tippen, Enter), den Befehl einfügen (⌘ + V) und Enter drücken. Fertig ist es, wenn „Fertig!“ erscheint. Der Befehl gilt 10 Minuten und nur einmal; danach (oder wenn das Terminal „error: 404“ meldet) einfach einen neuen erstellen. Den Befehl gibt es nur, wenn das Programm über die https-Adresse geöffnet ist (z. B. die Tailscale-Adresse). Eine heruntergeladene Einrichtungs-Datei würde macOS sperren („Apple konnte nicht überprüfen …“), über das Terminal gibt es diese Sperre nicht. Administrator-Rechte braucht es nicht.

Beim ersten Klick auf eine Datei fragt der Browser, ob er den Link öffnen darf – erlauben. Mit der Ordner-Freigabe des ERP bringt die Einrichtung Benutzer und Passwort des Ordners gleich mit, am PC muss niemand etwas eintippen. Bei einer Windows-Freigabe fragt der PC beim ersten Verbinden einmal danach. Das Hilfsprogramm hält sich selbst aktuell, wenn das Programm über https geöffnet wird (z. B. über die Tailscale-Adresse); eine neue Ordner-Adresse holt es sich beim ersten Klick. Wurde die Adresse auf die Ordner-Freigabe des ERP umgestellt, das Hilfsprogramm an jedem PC einmal neu einrichten, damit der Zugang dazukommt.

**Von unterwegs** klappt es über Tailscale: mit der Ordner-Freigabe des ERP von selbst, bei einer Windows-Freigabe, wenn auf dem Rechner mit dem Ordner einmal `deployment/openfile-launcher/Freigabe-Tailscale.ps1` als Administrator ausgeführt wurde.

## KI-Funktionen einschalten

Unter **Einstellungen → KI-Funktionen** einen Schlüssel von Google Gemini eintragen, mit „Schlüssel testen“ prüfen und „Schlüssel speichern“. Die KI ist freiwillig. Ohne Schlüssel fehlen die KI-Hilfe in der Menüleiste, die Text-Verbesserung beim E-Mail-Schreiben, das automatische Auslesen von Belegen (E-Rechnungen liest das Programm trotzdem) und die Spracheingabe in der Handy-App. Mit Schlüssel gehen Belege, Texte und Sprachaufnahmen zur Verarbeitung an Google. „Schlüssel entfernen“ löscht den gespeicherten Schlüssel; danach sind alle KI-Funktionen aus, schon ausgelesene Daten bleiben erhalten.

Auf derselben Seite sortiert „Spaß-Anfragen automatisch aussortieren“ offensichtlichen Unsinn aus dem Anfrageformular der Webseite aus.

## Zeiterfassung einrichten

Unter **Einstellungen → Zeiterfassung**:
- „Start der Zeiterfassung“ – siehe [Monatsabschluss](monatsabschluss.md).
- „Stunden ans Steuerbüro & DATEV“ – siehe [DATEV-Export](datev-lodas-export.md).
- „Mobile Zeiterfassung“ – die Adresse der Handy-App zum Kopieren.
- „Zugang von unterwegs“ – wie die Handys die App außerhalb des Büros erreichen (empfohlen über die Tailscale-App).
- „QR-Codes für Mitarbeiter“ – führt zum Mitarbeiter, dort „QR-Code“ (siehe [Arbeitszeit, Urlaub und Feiertage](arbeitszeit-urlaub-feiertage.md)).

## Telefon (FRITZ!Box) anbinden

Unter **Einstellungen → Telefon**:
1. In der FRITZ!Box einen Benutzer anlegen und den Zugriff für Anwendungen zulassen – die rechte Spalte „So richten Sie die FRITZ!Box ein“ beschreibt die Schritte.
2. „Telefon-Anbindung aktiv“ anhaken, „Adresse der FRITZ!Box“, „Benutzer“ und „Passwort“ eintragen.
3. „Verbindung testen“. Danach die „Geschäftsnummern“ und „Anrufbeantworter“ anhaken, die das Programm abholen soll.
4. „Telefon-Einstellungen speichern“.

„Ältere Anrufe nachholen“ holt einmalig frühere Anrufe. Unter „Telefon an diesem Rechner“ wählt man, welches Telefon bei „Zurückrufen“ klingelt. Wer die Anrufe sieht, regelt das Abteilungsrecht „Anrufe & Anrufbeantworter“.

## Firmendaten pflegen

Unter **Vorlagen & Stammdaten → Firma**, Reiter „Firmendaten“: Firmenlogo (erscheint auf allen PDFs), Firmenname, Adresse, Kontaktdaten, „Bewertungs-Link (Google)“, „Firmenfarbe“, „Steuerliche Angaben“ (Steuernummer, USt-IdNr.), „Bankverbindung“ und das „Automatisches Mahnverfahren“ (siehe [Offene Posten und Mahnungen](offene-posten-und-mahnungen.md)). Ohne Steuernummer oder USt-IdNr. lassen sich keine E-Rechnungen erstellen.

Weitere Reiter: „Lohn-Stammdaten“, „Unfallversicherung“, „Steuerberater“ (Kontakte für die Stundenübermittlung) und „Steuerprüfung“ (Prüfpaket für das Finanzamt).

## Benutzer verwalten

Oben rechts auf den eigenen Namen klicken → „Benutzer verwalten“ (nur Admins). Links stehen die Benutzer; das Plus legt einen neuen an. Felder:
- „Anzeigename“, „Benutzername“ und „Passwort“ (mindestens 8 Zeichen),
- „Rolle“: „User“ oder „Admin“,
- „Aktiv“,
- „Mitarbeiter zuordnen“: nötig, damit der Benutzer Zeiten, Korrekturen und Tagebuch-Einträge speichern kann und die Rechte seiner Abteilung bekommt,
- „Eigenes Postfach“: steht beim Schreiben neuer E-Mails unter „Senden von“ vorne.

„Erstellen“ bzw. „Speichern“. Löschen über den Papierkorb beim Drüberfahren. Mindestens ein aktiver Admin muss bleiben.
