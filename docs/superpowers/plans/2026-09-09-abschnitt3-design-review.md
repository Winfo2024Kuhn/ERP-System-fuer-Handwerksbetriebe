# Abschnitt 3 – unabhängige Designprüfung

Geprüfter Stand: `68e43eeb`, Branch `codex/monatsabschluss-review-3-design`, 09.09.2026. Tasks 5, 8 und 9 einschließlich der bereits zusammengeführten gemeinsamen PC-Bausteine. DATEV-Oberfläche Task 6 und Migrationen 10–14 bleiben planmäßig außerhalb dieses Abschnitts.

**Ampel: ROT vor Nachbesserung.** Die neue Monatsabschluss-Seite ist funktional und gestalterisch schlüssig. Offene Befunde betreffen Tastaturfokus, verbleibende Standard-Checkbox, Aktionshierarchie und Testmigration/-isolation. Korrekturen werden bereits separat umgesetzt; dieser Bericht bewertet ausdrücklich den ursprünglichen Gesamtstand.

## Ausführung und Nachweise

- PC vollständig: `E2E_PORT=5191 npx playwright test --workers=1 --output=/tmp/review3-design-react-pc-frontend-artifacts`: **439 bestanden, 17 fehlgeschlagen, 456 insgesamt**, 8,5 Minuten.
- Mobile vollständig: entsprechender Aufruf auf **5192**, **3/3 bestanden**.
- Größen: PC 1440×900, 1536×960, 1920×1080; Mobile 393×852 mit Touch.
- Logs: `/tmp/review3-design-react-pc-frontend.log`, `/tmp/review3-design-react-zeiterfassung.log`.
- Alle **199** Bilder im PC-Verzeichnis `design/` wurden in 23 beschrifteten Kontaktbögen angesehen (`/tmp/review3-contact-all/`, Manifest mit Originalpfaden). Neue Monatsabschluss-, Arbeitszeit-, Termin-, Tageserfassungs-, Steuerberater- und Stornozustände zusätzlich in Einzelansicht; drei mobile Bilder ebenfalls einzeln angesehen. Kontaktbögen dienen dem Gesamtvergleich, nicht einer behaupteten pixelgenauen Schrift-/Kontrastmessung. Automatische Geometrieprüfungen laufen auf den Originalansichten.
- Mobile Spec schrieb ein Bild noch an einen festen Pfad; gesichert unter `/tmp/review3-mobile-hardcoded-artifacts/task8-kalender-handy.png`.
- Keine Backend- oder echten Personendaten. PC nutzt den bestehenden Fremdhost-Riegel. Mobile Einschränkung siehe D3-4; kein tatsächlicher Fremdhost-Request beobachtet oder nachgewiesen.
- Keine Unit-/Build-Wiederholung durch diesen Reviewer. Nach Abschluss keine Listener auf 5191/5192; temporäre Reproduktions-Specs entfernt. Keine Produktdateien geändert.

## Neue Befunde

### D3-1 – Fokus verlässt den Stornodialog

Im Zeitkalender Korrekturen öffnen und einen Eintrag stornieren. Der Korrekturen-Hintergrund ist inert, aber der gemeinsame Dialog hält den Fokus nicht im obersten Dialog. Den letzten Knopf (zugänglicher Name `Close`) fokussieren und Tab drücken: `storno.locator(':focus')` hat **0 statt 1** Treffer. Separat auf 1440×900 reproduziert; Log `/tmp/review3-storno-focus.log`, Screenshot unter `/tmp/review3-storno-focus/`. Visuell ist der Dialog klar und frei von Überdeckungen, die Tastaturbedienung ist dennoch unvollständig.

Korrektur zentral im vorhandenen Dialog-/Fokusbaustein: Eintritt, Tab/Shift-Tab-Rundlauf, Escape und Rückkehr zum Auslöser prüfen; keine weitere lokale Sonderlösung.

### D3-2 – zwei gleich starke Hauptaktionen

`ZeiterfassungKalender.tsx`: Tageserfassung zeigt sowohl „Neue Buchung“ als auch „Alle Speichern“ vollflächig rose (ursprünglich Zeilen 1485/1509). In allen drei Größen sichtbar, z. B. `design/systemeingaben-task9-zeitkalender--pc-14zoll.png`. Neue Buchung als Nebenaktion, Alle Speichern als eindeutige Hauptaktion gestalten.

### D3-3 – native blaue Checkbox im Arbeitszeitdialog

„Diese Vorlage für diese Person individuell anpassen“ ist noch eine blaue Browser-Checkbox, sichtbar in `design/systemeingaben-task9-arbeitszeit--pc-14zoll.png`, `--pc-uebergang.png` und `--pc-monitor.png`. Die Checkbox konsistent im eigenen Rose-Design gestalten. Root hat verifiziert, dass im PC-Bestand keine eigene Checkbox-Komponente vorhanden ist; der Monatsabschluss nutzt bereits `accent-rose-600`. Eine angeblich vorhandene Checkbox-Komponente wird daher nicht vorausgesetzt. Das ist eine konkrete offene Stelle der Nutzervorgabe zu allen Eingaben, keine Geschmacksfrage.

### D3-4 – Mobile E2E ohne vollständigen Netzwerkriegel

`react-zeiterfassung/e2e/systemeingaben-task-8.spec.ts` importiert den Playwright-Test direkt und stubbt nur API-Routen; fremde Hosts sind nicht vor Navigation abgeriegelt. Außerdem ignoriert der feste Screenshotpfad die getrennte Ausgabe. Gemeinsamen Testbaustein für Kontext-Netzwerkisolation nutzen und `testInfo.outputPath` verwenden. Die drei grünen Mobile-Tests belegen deshalb im ursprünglichen Stand nicht die vollständige Netzwerktrennung.

### D3-5 – zwölf neue Fehler durch veraltete E2E-Verträge

- `mitarbeiter-arbeitszeit-task8.spec.ts:50`: sucht `input[type="number"]` und tippt `7.5`. Nach DecimalInput-Migration muss das beschriftete Textfeld mit `7,5` angesprochen werden; numerischen API-Payload weiterhin prüfen. **3 Fehler**, je Größe einer.
- `monatsabschluss-task9.spec.ts:58/64/70/81/90`: erwartet Punktanzeigen wie `168.0h`/`125.0h`, tatsächlich korrekt deutsch `168,0h`/`125,0h`. **9 Fehler**, drei Abläufe in drei Größen. Fachliche Abschluss-/Wiederöffnungs-/Berechtigungsprüfungen erhalten.

Die gemeinsame Arbeitszeit-Entwurfs-/Sieben-Tage-/Zeitfensterlogik wird parallel auf Hinweis des Code-Reviews gemäß neuer Nutzervorgabe extrahiert; dieser Designbericht ersetzt dessen Codeprüfung nicht.

## Unveränderte fünf Bestandsfehler

Dieselben fünf Fälle und Messwerte wie im [Abschnitt-2-Bericht](2026-09-09-abschnitt2-design-review.md), dort bereits auf dem früheren Stand reproduziert:

1. DocumentEditor Warnung verdeckt Hintergrundtext, `dokument-editor-seite.spec.ts:245`, 1440.
2. Derselbe Hintergrundzustand beim Tab-Schließen, `dokument-editor-tab-schliessen.spec.ts:63`, 1440.
3. Ribbon-Kategorien 805 px Inhalt bei 803 px Platz, `menueleiste-layout.spec.ts:185`, 1440.
4. Zweiter Ribbon-Grenzfall, `menueleiste-layout.spec.ts:290`, ebenfalls 805/803 px, 1440.
5. Projektreiter Tagebuch wechselt um 42 px in die nächste Zeile, `projekt-detail-layout.spec.ts:189`, 1440.

Keine neuen Fehler wurden ohne Reproduktion als Bestand abgetan. Die fünf Fälle erklären nicht die zwölf neu hinzugekommenen Testfehler.

## Sechs Designfragen je Größe

Die folgenden Antworten gelten für die betrachteten neuen Screenshots; die Baseline-Screens wurden zusätzlich auf neue sichtbare Regressionen verglichen. Historische Dokumenteditor-/Projekt-/Kunden-/Lieferanten-/Ribbon-Screens zeigen keine zusätzliche durch diesen Abschnitt eingeführte Abweichung. Die absichtlich minimalen Screens der Design-Helper-Selbsttests sind Testfixtures.

| Frage | 1440×900 | 1536×960 | 1920×1080 | Mobile 393×852 |
|---|---|---|---|---|
| Farben unterscheidbar? | Status klar rose/slate/semantisches amber; D3-2/D3-3 offen | Gleiches Ergebnis | Gleiches Ergebnis | Kalenderauswahl rose, Statushinweise klar |
| Eigenes Design? | Zeit-/Dezimal-/Datumsfelder und Toasts passen, Checkbox D3-3 offen | Gleiches Ergebnis | Gleiches Ergebnis | Eigener Kalender statt Browserpicker, passende Touchflächen |
| Look-and-Feel? | Monatsfilter und Stundentabelle ausgerichtet, Dialoge ruhig | Kein Bruch am Übergang | Breite sinnvoll genutzt, Summen bleiben lesbar | Kalender und Dashboard übersichtlich |
| UX nachvollziehbar? | Auswahlzahl, Teilerfolg, Verlauf, konkrete Zahl-/Zeitfehler verständlich; D3-1/D3-2 offen | Gleiches Ergebnis | Gleiches Ergebnis | Datumsauswahl und ein-/ausgeschaltetes Zeitkonto verständlich |
| Auffindbar? | Abschluss im Kopf, Vorschau/Speichern sichtbar | Sichtbar | Sichtbar | Auswahl/Schließen erreichbar |
| Überdeckungen? | Neue Dialoge ohne bestätigte Aktionsüberdeckung; fünf Baseline-Fälle separat | Keine neue bestätigte Überdeckung | Keine neue bestätigte Überdeckung | Kein sichtbarer Überlauf oder verdeckte Aktion |

Untersucht und **nicht bestätigt**: Toast bei Monatsabschluss-Teilerfolg könnte den Kalenderlink der zweiten Zeile verdecken. Ein eigener Klickversuch nach abgeklungener Animation besteht (`/tmp/review3-toast-occlusion.log`); daraus wird kein Produktfehler konstruiert. Der globale Überschneidungshelfer zählt teils absichtlich verdeckte Hintergrunddialoge mit; für Storno wurde deshalb der oberste Dialog gesondert geometrisch und der Hintergrund auf inert geprüft.

## Nachprüfung

Nach gebündelter Korrektur nur die betroffenen E2E-Specs, zentrale Dialog-Tastaturfälle und gemeinsamen Arbeitszeitfelder auf den drei PC-Größen sowie Mobile-Netzwerkriegel gezielt erneut prüfen. Keine unbegründete Wiederholung der vollständigen Suite. Root meldet bereits 18 PC- und 3 Mobile-Fixfälle grün; das ist zum Zeitpunkt dieses Berichts eine Autorenprüfung und keine unabhängige Abnahme.
