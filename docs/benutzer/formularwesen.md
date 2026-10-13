# Formularwesen: Briefvorlagen und feste Texte

Unter **Vorlagen & Stammdaten → Formularwesen** richtet ein Admin ein, wie Angebote, Rechnungen, Mahnungen und die übrigen Dokumente aussehen. Oben stehen die **Feste Texte** (zum Beispiel die Widerrufsbelehrung), darunter die **Briefvorlagen**. Ändern dürfen nur Admins; alle anderen Benutzer sehen die Vorlagen, weil der Dokument-Editor sie lädt.

## Briefvorlage anlegen und bearbeiten

1. **Vorlagen & Stammdaten → Formularwesen**, unter „Briefvorlagen“ auf „Neue Vorlage“ oder bei einer vorhandenen Vorlage auf „Bearbeiten“.
2. Links unter „Bausteine“ einen Baustein anklicken oder auf die Seite ziehen: „Text“, „Anschrift“, „Nummer & Datum“, „Positionen“, „Seitenzahl“, „Logo / Bild“. Darunter stehen die Ebenen der aktuellen Seite.
3. Baustein auf der Seite anklicken: Ziehen verschiebt ihn, die Griffe ändern die Größe. Beim Ziehen rastet er an Seitenrändern, Seitenmitte, anderen Bausteinen und den Norm-Linien ein.
4. Rechts stehen die Eigenschaften des gewählten Bausteins: Position und Größe in Millimetern, Inhalt mit Platzhaltern, Schriftgröße, Fett und Farbe, „Auf der Seite ausrichten“, nach vorn oder hinten, Duplizieren, Löschen.
5. Ohne gewählten Baustein zeigt die rechte Spalte das Briefpapier (je ein Bild für Seite 1 und für die Folgeseiten), „Verwenden für“ (welche Dokumentarten die Vorlage nutzen) und „Auf Standard-Layout zurücksetzen“.
6. Oben „Speichern“. Neben dem Namen steht „Gespeichert“ oder „Ungespeicherte Änderungen“. Wer mit ungespeicherten Änderungen auf „Alle Vorlagen“ geht, wird gefragt.

Tastenkürzel: Pfeiltasten verschieben (mit Umschalt in größeren Schritten), Entf löscht, Strg + D dupliziert, Strg + Z / Strg + Y macht rückgängig bzw. wiederholt, Esc hebt die Auswahl auf. Auf dem Mac gilt Cmd statt Strg.

Oben über der Seite wechselt man zwischen „Seite 1“ und „Folgeseiten“ und zoomt mit −, + und „Einpassen“.

## Sitzt die Anschrift im Fenster des Umschlags?

Der Schalter „Norm-Hilfslinien“ blendet die Maße nach DIN 5008 ein: das Fenster eines Umschlags DIN lang (90 × 45 mm), darin oben die Zeilen für Rücksendeangabe und Vermerke und darunter die Anschriftzone, dazu den Infoblock sowie die Falz- und Lochmarken am linken Rand. Die Linien erscheinen nur im Editor und in der Vorschau, nie im fertigen PDF.

- „Form B“ ist der übliche Geschäftsbrief (Anschrift weiter unten). „Form A“ setzt die Anschrift höher. Die Form wählen, für die das Briefpapier gedruckt ist. Die Umstellung verschiebt keine Bausteine.
- Liegt die Anschrift ganz in der Anschriftzone, steht darunter „Anschrift sitzt im Fenster“. Sonst kommt ein gelber Hinweis mit dem Knopf „Anschrift ins Fenster setzen“. Er setzt den Anschrift-Baustein genau auf die Anschriftzone; „Rückgängig“ holt die alte Position zurück.
- In der **Vorschau** (Knopf oben) stehen Seite 1 und die Folgeseiten mit Beispieldaten nebeneinander. Die „Umschlag-Ansicht“ dunkelt alles außer dem Fenster ab – so sieht man, was der Empfänger durch das Fenster sieht.

## Standard-Texte je Dokumentart

Der Knopf „Standard-Texte“ in der Vorlage legt fest, welche Textbausteine beim Anlegen eines Dokuments automatisch vor und nach den Positionen eingefügt werden – je Dokumentart. Das gilt auch für Mahnungen: Für „Zahlungserinnerung“, „1. Mahnung“ und „2. Mahnung“ zugeordnete Textbausteine kommen ins Mahnungs-PDF über und unter die Box mit der offenen Forderung. Ohne Zuordnung nimmt das Programm seine eingebauten Mahntexte. Die Textbausteine selbst pflegt man unter **Vorlagen & Stammdaten → Textvorlagen**.

## Widerrufsbelehrung bearbeiten

Nimmt ein Kunde ein Angebot online an, schickt das Programm automatisch die Auftragsbestätigung. Die Widerrufsbelehrung hängt als PDF daran, zusammen mit dem Muster-Widerrufsformular und der Erklärung des Kunden zum Beginn der Arbeiten.

1. **Vorlagen & Stammdaten → Formularwesen**, unter „Feste Texte“ bei „Widerrufsbelehrung“ auf „Bearbeiten“.
2. Text ändern. Der Editor kennt Fett, Überschrift, Aufzählung und Nummerierung.
3. Rechts unter „Platzhalter“ setzt ein Klick Firmenangaben ein, z. B. Name, Anschrift, Telefon und E-Mail. Beim Erzeugen des PDFs kommen die Werte aus **Vorlagen & Stammdaten → Firma**.
4. „Vorschau (PDF)“ zeigt das PDF mit dem noch nicht gespeicherten Text.
5. „Speichern“.

Was beim Speichern passiert:

- Jedes Speichern mit geändertem Text legt eine neue Fassung mit dem heutigen Datum als Stand an. Ab dann geht nur noch der neue Text raus; schon verschickte Belehrungen ändern sich nicht.
- Frühere Fassungen bleiben erhalten und stehen rechts unter „Frühere Fassungen“. Ein Klick zeigt sie an, „In den Editor übernehmen“ holt den Text zurück (gespeichert wird erst mit „Speichern“).
- Bei jeder Online-Annahme merkt sich das Programm, welche Fassung verschickt wurde.
- „Auf Standardtext zurücksetzen“ setzt den mitgelieferten Text nach dem gesetzlichen Muster als neue Fassung ein.
- Das Muster-Widerrufsformular und die Erklärung zum Beginn der Arbeiten hängt das Programm immer selbst an; sie sind nicht bearbeitbar.

**Wichtig:** Die Widerrufsbelehrung steht zusätzlich auf der Firmen-Website (Seite /widerruf/), die der Kunde beim Annehmen sieht. Wer den Text im Programm ändert, muss ihn auf der Website genauso ändern – sonst steht in der Mail etwas anderes als auf der Website.

Unter „Vorschlag: Online-Widerruf“ liegt der Satz aus dem gesetzlichen Muster für einen Widerrufs-Knopf auf der Website (§ 356a BGB). Nur einfügen, wenn es diesen Knopf auf der Website gibt, und „[Internet-Adresse eintragen]“ durch die Adresse ersetzen.
