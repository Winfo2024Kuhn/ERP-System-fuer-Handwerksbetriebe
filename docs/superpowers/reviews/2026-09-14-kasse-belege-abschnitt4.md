# Abschnitt 4 – kombinierter Code- und Design-Review (Tasks 14 und 15)

## Ergebnis

**AMPEL: 🟡 – abgenommen.** Keine offenen blockierenden Befunde. In der gemeinsamen Reviewrunde wurden die konkrete Belegöffnung, der an den ZIP-Anhang gebundene Exportzeitraum, die fehlgeschlagene Vorprüfung, die DATEV-Kennnummern, Zahlungsart-Ausnahmen, Detail-Datenverlust und das Layout des langen Einstellungsdialogs korrigiert und zielgenau nachgewiesen. Gelb bleiben nur zwei Accessibility-Hinweise am neuen Paketdialog; gemäß Nutzerentscheidung lösen sie keine Stilrunde aus.

## Codeprüfung

- Task 14: Die fünf DATEV-Felder werden geladen und vollständig gespeichert. Berater-/Mandantennummer sowie Kassen-/Bankkonto erlauben leer oder ausschließlich Ziffern innerhalb ihrer Grenzen; fehlerhafte Werte erzeugen Inlinehinweise und keinen PUT. Der Wirtschaftsjahresbeginn nutzt das eigene Select. Die Einmalzahlung liegt in den Einstellungen; der alte Shortcut ist entfernt. Header und Footer bleiben fest, allein der Formularinhalt scrollt.
- Task 15: Die Vorprüfung läuft beim Öffnen und bei Monats-/Jahreswechsel. Während Laden oder nach Fehler sind Download und Mail gesperrt; ein 409 ersetzt die sichtbare Vorprüfung. Offene Belege öffnen über `?belegId=` tatsächlich den Detaildialog. ZIP-Dateiname, Monat und Jahr werden gemeinsam an das Mailmodal übergeben; Betreff/Text verwenden den exportierten Zeitraum, `attachments` wird im Multipart-Request gesendet, der alte Tabellenmodus ohne Anhang bleibt erhalten.
- Backend: Auth/Rechte, Jahr-/Monatsgrenzen, 409-Body, fünf ZIP-Bausteine, Batch-Abfragen, Pfadnormalisierung/Whitelist, Fehlbelegliste und PDF-Tempcleanup geprüft. Privatentnahme, Privateinlage und Umbuchung werden ohne Zahlungsart nicht mehr fälschlich als offen gemeldet; fehlendes Konto bleibt separat offen. Interne Dateinamen verwenden einheitlich `YYYY-MM`.
- Späte Task-13-Zielkorrekturen geprüft: jedes geöffnete Element lädt einmal das Detail-DTO; Zahlstatus/-datum, einfache und komplexe Splits sowie Vorschläge stammen daraus. Späte Antworten überschreiben keine Nutzereingaben, Speichern wartet auf Details und eine gelöschte einfache Zuordnung sendet `[]`. Der Rechnungslink nutzt die Detail-ID und die bestehende Route `/rechnungsuebersicht`.
- Keine Secrets, neue unsichere Dateipfade, SQL-Konkatenation, nativen Browserdialoge oder Datenverlustpfade im geprüften Diff gefunden.

## Gates und Nachweise

- Backend vollständig: **3.070 Tests, 0 Fehler, 0 Fehlschläge, 17 übersprungen**, `BUILD SUCCESS` (`/tmp/kasse-section4-backend.log`).
- PC Unit vollständig auf dem integrierten Produktstand: **131 Dateien / 1.445 Tests grün** (`/tmp/kasse-section4-pc-unit-final.log`). Nach den letzten Detail-Regressionsfällen: betroffene Seite **11/11 grün** (`/tmp/kasse-section4-pc-unit-target-final.log`).
- PC Lint und Build auf finalem HEAD: **grün** (`/tmp/kasse-section4-pc-lint-final-head.log`, `/tmp/kasse-section4-pc-build-final-head.log`); nur die bekannte Vite-Chunkwarnung.
- PC E2E: Vollsuite zunächst **549/552 grün**; die drei Größenfehler waren eine durch das neue Pflicht-Detail-GET veraltete Fixture. Nach Fixture- und Layoutfix sind die betroffenen Systemeingaben- und Einstellungsabläufe **6/6 grün** (`/tmp/kasse-section4-pc-e2e.log`, `/tmp/kasse-section4-pc-e2e-final-target.log`). Damit sind alle **552** Vollsuite-Fälle auf dem finalen Verhalten nachgewiesen.
- Mobile Unit: **22 Dateien / 217 Tests grün**, Build/PWA **grün**, E2E **17/17 grün** (`/tmp/kasse-section4-mobile-unit.log`, `/tmp/kasse-section4-mobile-build.log`, `/tmp/kasse-section4-mobile-e2e.log`).

## Designprüfung – sechs Fragen je Zustand und Größe

Fragen: **1** Farben/Zustände klar? **2** Design-System konsistent? **3** Look-and-Feel geordnet? **4** Ablauf verständlich? **5** Hauptaktion erreichbar? **6** Kein Überlauf/Überdecken?

### Kassen-Einstellungen – Startposition

Angeschaut: `test-results/design/kasse-einstellungen-dialog--pc-14zoll.png`, `--pc-uebergang.png`, `--pc-monitor.png`.

- **pc-14zoll (1440×900):** 1 Ja, Rose/Slate ruhig und eindeutig. 2 Ja, eigene Inputs/Selects, Lucide und Radien passen. 3 Ja, drei Abschnitte sind klar gegliedert. 4 Ja, Hilfetexte erklären Zweck und Standards. 5 Ja, Speichern ist fest sichtbar. 6 Ja, Inhalt scrollt innerhalb des Dialogs; Header/Footer überdecken nichts.
- **pc-uebergang:** 1 Ja, Akzent und Textkontrast klar. 2 Ja, konsistente Komponenten. 3 Ja, trotz vieler Felder ausgewogen. 4 Ja, Reihenfolge Mindestbestand → Steuerberater → Ehegattengehalt ist nachvollziehbar. 5 Ja, feste Fußaktionen. 6 Ja, kein horizontaler Überlauf oder abgeschnittener Text.
- **pc-monitor (1920×1080):** 1 Ja. 2 Ja. 3 Ja, die begrenzte Dialogbreite hält Zeilen lesbar. 4 Ja. 5 Ja, Primäraktion bleibt sichtbar. 6 Ja, der Formularscroll bleibt sauber zwischen festem Kopf und Fuß.

### Kassen-Einstellungen – nach Fokus/Scroll und mehreren Toasts

Angeschaut: `test-results/design/task10-kasseneinstellung--pc-14zoll.png`, `--pc-uebergang.png`, `--pc-monitor.png`.

- **pc-14zoll:** 1 Ja, Fehler/Erfolg sind semantisch getrennt. 2 Ja. 3 Ja, Dialog und Meldungsbereich bleiben visuell getrennt. 4 Ja, der bearbeitete Ehegattenblock bleibt lesbar. 5 Ja, Speichern und Abbrechen stehen fest. 6 Ja; der zunächst gefundene Input hinter Header/X ist nach `a2efed01` verschwunden.
- **pc-uebergang:** 1 Ja. 2 Ja. 3 Ja. 4 Ja, Scrollposition und Feldkontext bleiben erkennbar. 5 Ja. 6 Ja, keine interaktive Überschneidung; automatischer Designcheck grün.
- **pc-monitor:** 1 Ja. 2 Ja. 3 Ja, breite Toasts und schmaler Dialog konkurrieren nicht. 4 Ja. 5 Ja. 6 Ja, kein Abschneiden oder Überdecken.

### Steuerberater-Paket – vollständig

Angeschaut: `test-results/design/steuerberater-paket-fertig--pc-14zoll.png`, `--pc-uebergang.png`, `--pc-monitor.png`.

- **pc-14zoll:** 1 Ja, Erfolg grün, fehlende Kennung indigo und Primäraktion Rose. 2 Ja, Vorgabefarbe Indigo ist hier ausdrücklich Teil des Taskbriefs. 3 Ja, kompakt und ruhig. 4 Ja, Monat/Jahr, Hinweis und zwei Ausgabewege sind unmittelbar verständlich. 5 Ja, Paket erstellen bleibt sichtbar. 6 Ja, kein Überlauf oder Überdecken.
- **pc-uebergang:** 1 Ja. 2 Ja. 3 Ja, Dialog nutzt die Breite sinnvoll. 4 Ja. 5 Ja, Mail und Download klar getrennt. 6 Ja.
- **pc-monitor:** 1 Ja. 2 Ja. 3 Ja, Dialog bleibt kompakt. 4 Ja. 5 Ja. 6 Ja.

### Steuerberater-Paket – drei offene Punkte

Angeschaut: `test-results/design/steuerberater-paket-offen--pc-14zoll.png`, `--pc-uebergang.png`, `--pc-monitor.png`.

- **pc-14zoll:** 1 Ja, Amber signalisiert prüfbare Lücken. 2 Ja. 3 Ja, drei Belegkarten sind sauber scanbar. 4 Ja, Zeilen sind klickbar und die Folge von „Erst prüfen“/„Trotzdem erstellen“ wird erklärt. 5 Ja; die Fußaktionen umbrechen auf zwei Reihen, bleiben aber vollständig sichtbar und eindeutig. 6 Ja, keine Überdeckung.
- **pc-uebergang:** 1 Ja. 2 Ja. 3 Ja. 4 Ja. 5 Ja, beide Entscheidungswege sichtbar. 6 Ja, kein Überlauf oder abgeschnittener Belegtext.
- **pc-monitor:** 1 Ja. 2 Ja. 3 Ja, großzügige Abstände ohne verlorene Gruppierung. 4 Ja. 5 Ja. 6 Ja.

## Nicht blockierende Hinweise

- 💡 Die Monats- und Jahres-Selects im eigengebauten Paketdialog haben sichtbare Labels, aber keine programmatische Zuordnung (`aria-label`/`id`); Screenreader hören derzeit nur den ausgewählten Wert.
- 💡 Der Paketdialog verwendet nicht die gemeinsame `Dialog`-Komponente und besitzt daher keinen vergleichbaren Escape-/Fokusfang. Mausbedienung, sichtbares X und getestete Abläufe funktionieren.

Keine Reviewer-Quellcodeedits oder Commits. Graph, Log und Build-/Testartefakte bleiben wie vereinbart beim Hauptagenten; alle gestarteten Maven-, Vite- und Playwright-Prozesse sind beendet.
