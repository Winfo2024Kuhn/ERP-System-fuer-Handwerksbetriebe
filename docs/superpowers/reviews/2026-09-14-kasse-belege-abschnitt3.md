# Abschnitt 3 – kombinierter Code- und Design-Review (Tasks 8, 12, 13)

## Ergebnis

**AMPEL: 🟡 – abgenommen.** Keine offenen blockierenden Befunde. Die erste Reviewrunde fand vier konkrete Integrationsprobleme; sie wurden in `4d7607d5`/`5ef3f0a3`, `c847a273`/`c7c46155`, `00b7b708`/`d5aa3421` und `ad06b7be` behoben und zielgenau nachgewiesen. Ein gelber Hinweis bleibt nur zur Qualität des Design-Screenshots (kaputte, nicht gestubbte Bildvorschau); das Produktverhalten ist davon nicht betroffen.

## Codeprüfung

- Task 8: Auth und Recht (`findCaller` + `darfSehen`), Jahr/Monat 2000–2999 bzw. 1–12, 409-Vorprüfung, fünf ZIP-Bausteine, DATEV-Eingaben, Batch-Abfragen für Splits und Lieferantendokumente, normalisierte Upload-Basis mit `startsWith`, Dateiendungs-Whitelist, fehlende Dateien in `LIESMICH.txt` sowie PDF-Löschung im `finally` geprüft. Keine offene Korrektheits-, Security-, DSGVO- oder N+1-Lücke gefunden.
- Task 12: exakt sechs Kacheln und Buchungsarten, Multipart-Teile `daten`/`datei`, 400/409-Unterscheidung, Unterdeckungs-Einlage, Monatssperr-Hinweis, Rechnungsauswahl und Ersatzbeleg ohne Vorsteuer geprüft. Backend-Ableitung deckt alle sechs Arten ab.
- Task 13: feste Fragenreihenfolge, Zahlungsart→Kategorie, Zahlstatus, KI-Vorschläge, Lieferantenkonflikt, private/Transfer-Semantik, Festschreibungs-Verhalten und komplexe Splits geprüft. Nachbesserungen erhalten Privat-/Transferbelege ohne Zahlungsart, zeigen einzelne komplexe Splits weiter, behandeln ein fachlich wirkungsloses Ein-Jahr-Startjahr als einfache Zuordnung und übernehmen den KI-Namen als Suchvorbelegung.
- Keine Secrets, neuen unsicheren URLs, nativen Alert-/Confirm-Aufrufe oder unsichere Dateipfade im Abschnittsdiff gefunden.

## Gates und Nachweise

- Backend vollständig: **3.068 Tests, 0 Fehler, 0 Fehlschläge, 17 übersprungen**, `BUILD SUCCESS` (`/tmp/kasse-section3-backend.log`).
- PC Lint: **grün** (`/tmp/kasse-section3-pc-lint.log`).
- PC Build: **grün** (`/tmp/kasse-section3-pc-build.log`).
- PC Unit vollständig lief zunächst 1.439/1.441 grün; zwei durch die neue Zahlungsartpflicht veraltete Fixtures deckten dabei zusätzlich die notwendige Privat-/Transfer-Ausnahme auf. Nach Fix: `BelegeKasseEditor.test.tsx` **7/7**, abschließende betroffene Units **10/10** grün (`/tmp/kasse-section3-fix1-unit.log`, `/tmp/kasse-section3-final-unit.log`). Die Vollsuite wurde gemäß Orchestratorvorgabe nicht doppelt gestartet.
- PC E2E vollständig: neue Task-12/13-Abläufe grün; 537 Tests grün, drei gleiche Größenfehler in einer Bestandsspec deckten den verborgenen Einzel-Split auf. Nach Fix: `beleg-pruefen` **3/3** und Split/Systemeingaben-Nachweis abschließend **3/3** grün (`/tmp/kasse-section3-final-e2e.log`, `/tmp/kasse-section3-final-system-e2e.log`). Die vollständige Suite wurde gemäß Orchestratorvorgabe nicht doppelt gestartet.
- Geprüfte Größen: `pc-14zoll` 1440×900, `pc-uebergang`, `pc-monitor` 1920×1080. Keine Mobile-Änderung und kein Mobile-Lauf.

## Designprüfung – sechs Fragen je Zustand und Größe

### Neue Buchung – Kachelauswahl

Angeschaut: `test-results/design/neue-buchung-auswahl--pc-14zoll.png`, `--pc-uebergang.png`, `--pc-monitor.png`.

**pc-14zoll:** 1 Farben klar: Rose markiert Fokus/Aktion, Slate hält den Dialog ruhig. 2 Design-System eingehalten: Lucide, Rose/Slate, Systemschrift, passende Radien. 3 Look-and-Feel aufgeräumt und gleichmäßig. 4 UX klar: sechs vollständig beschriftete Klickziele. 5 Alle sechs Arten ohne Scrollen auffindbar. 6 Kein Überlauf, Abschneiden oder Überdecken.

**pc-uebergang:** 1 Zustände farblich klar. 2 Komponenten und Typografie konsistent. 3 Dialog proportional und ruhig. 4 Kachelwahl unmittelbar verständlich. 5 Alle Aktionen im sichtbaren Bereich. 6 Keine Überschneidung oder horizontale Scrollfläche.

**pc-monitor:** 1 Akzent sparsam und eindeutig. 2 Design-System vollständig konsistent. 3 Dialog bleibt kompakt und wirkt nicht verloren. 4 Primärer Einstieg eindeutig. 5 Alle Buchungsarten sofort sichtbar. 6 Keine abgeschnittenen oder überlappenden Elemente.

### Neue Buchung – Geld ausgegeben, kein Beleg

Angeschaut: `test-results/design/neue-buchung-ohne-beleg--pc-14zoll.png`, `--pc-uebergang.png`, `--pc-monitor.png`.

**pc-14zoll:** 1 Aktiver Ersatzbeleg-Zustand und amberfarbener Warnhinweis klar getrennt. 2 eigene Decimal-/Date-/Select-Komponenten, Rose/Slate/Amber und Lucide eingehalten. 3 dicht, aber sauber ausgerichtet. 4 0 % ist sichtbar gesperrt und der Grund wird direkt erklärt. 5 Buchen/Abbrechen und alle Pflichtfelder ohne problematischen Scroll erreichbar. 6 Kein Überlauf oder verdeckter Inhalt.

**pc-uebergang:** 1 Statusfarben verständlich. 2 Design-System konsistent. 3 gute Feldabstände und Gruppenbildung. 4 Beleg-Umschalter, Pflichtgrund und Vorsteuerfolge sind eindeutig. 5 Primäraktion sichtbar. 6 Keine Überschneidung oder abgeschnittener Text.

**pc-monitor:** 1 Rose und Amber unterscheiden Aktion/Warnung klar. 2 Komponenten, Radien und Schrift passen. 3 kompakter Dialog nutzt die Fläche angemessen. 4 genau eine primäre Buchungsaktion. 5 Ablauf vollständig sichtbar. 6 Keine Layoutfehler.

### Beleg prüfen – Zahlung, Vorschläge, Baustelle und Lieferantenkonflikt

Angeschaut: `test-results/design/beleg-pruefen--pc-14zoll.png`, `--pc-uebergang.png`, `--pc-monitor.png`.

**pc-14zoll:** 1 KI-Rose, Erfolg-Emerald und Konflikt-Amber sind klar unterscheidbar. 2 Rose/Slate, Lucide, feste Fußleiste und Komponenten passen; **Hinweis:** der E2E-Stub liefert keine echte Bilddatei, daher zeigt die Vorschau ein kaputtes Bildsymbol. 3 Formular bleibt trotz Informationsdichte geordnet. 4 Übernahmezustand, Zahlungsfrage und Lieferantenabweichung sind verständlich; Fußaktionen bleiben erreichbar. 5 die relevante Primäraktion ist ohne Seitenscroll sichtbar; der lange Dialog scrollt erwartungsgemäß vertikal. 6 keine Überlappung, kein horizontaler Überlauf.

**pc-uebergang:** 1 Zustände auf einen Blick trennbar. 2 Design-System eingehalten; derselbe reine Screenshot-Stub-Hinweis zur Vorschau. 3 2:1-Aufteilung wirkt ausgewogen. 4 Chips, Selects, Konfliktbox und feste Aktionen sind klar. 5 Prüfen/Übernehmen bleibt sichtbar. 6 kein Abschneiden oder Überdecken.

**pc-monitor:** 1 Farben ruhig und semantisch. 2 Komponenten, KI-Badges und Rose/Slate konsistent; derselbe Screenshot-Stub-Hinweis. 3 breite Formularspalte und schmale Vorschau nutzen den Monitor sinnvoll. 4 Reihenfolge führt nachvollziehbar von Betrag über Zahlung zu Zuordnung und Lieferant. 5 zentrale Aufgabe und Primäraktion sofort auffindbar. 6 kein Überlauf oder verdeckter Inhalt.

## Nicht blockierender Hinweis

- 💡 `react-pc-frontend/e2e/beleg-pruefen.spec.ts`: Die Bildroute sollte für einen sauberen Designnachweis mit einem kleinen gültigen Dummy-Bild beantwortet werden. Der aktuelle Screenshot zeigt deshalb ein Browser-Symbol für ein nicht ladbares Bild; Funktion und Layoutprüfung bleiben grün.

Gestartete Reviewer-Prozesse sind beendet; kein eigener Maven-, Vite- oder Playwright-Prozess bleibt offen. Quellcode, Kontextlog, Graph und Build-Artefakte wurden vom Reviewer nicht bereinigt oder verändert; das übernimmt wie vereinbart der Hauptagent.
