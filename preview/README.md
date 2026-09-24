# Beschaffungs-Vorschau mit IDS

Die Anwendung auf Port 8099 verwendet das aktuelle Frontend. Nur die Beschaffungsseiten und -dialoge stammen aus `feature/en1090-echeck` (Referenz `255cd2862fdf01a3588d4afa6652f3abdd5431e0`, Dateiliste unter `react-pc-frontend/preview/original-procurement-files.json`). Warengruppe wurde auf Nutzerwunsch entfernt. Die gemeinsamen aktuellen Komponenten bleiben erhalten. IDS-Einstellungen und Warenkorbprüfung wurden ergänzt.

## Start

1. `./preview/pdf-start.sh`: unveränderter Original-PDF-Service mit Dummy-Datenquellen auf 127.0.0.1:8097, ohne Spring, Datenbank oder Mailjobs.
2. In `react-pc-frontend`: `EN1090_PREVIEW_PORT=8099 npm run preview:original`.
3. Bedarf: `/bestellungen/bedarf`. Projektloser Bedarf: `/bestellungen/bedarf/legacy`.
4. Zugang unter `/einstellungen#ids` speichern. Blankes Passwort erhält den vorhandenen Zugang.
5. Shop öffnen, Warenkorb zurückgeben. Rückkehr jetzt direkt zur Prüfung unter `/bestellungen/ids/:id`; alle Warenkörbe sind aus der Bedarfsübersicht erreichbar.
6. „Bei Würth bestellen“ überträgt die gespeicherten Positionen mit IDS-WKS und interner Bestellnummer an den Shop. Die verbindliche Bestellung erfolgt dort. Nur eine IDS-Rückgabe mit Bestellkennzeichen setzt den Status auf bestellt; Rückgaben aktualisieren denselben Warenkorb.

Die Vorschau bindet ausschließlich an Loopback. Die sonstigen Beschaffungsdaten sind lokale Mocks, ohne echte Datenbank oder Mailversand. Würth-Aufruf und Rückgabe sind dagegen echt. Zugangsdaten liegen verschlüsselt in der gitignored `src/main/resources/application-local.properties`. Echte Warenkörbe liegen mit Dateimodus 0600 außerhalb des Repositories unter `~/Library/Application Support/Codex/ids-preview/<Arbeitsverzeichnis-Hash>/`. Sie überstehen einen Neustart. Offene Shop-Aufrufe laufen nach einer Stunde ab und werden beim Serverneustart ungültig.

Übernommen werden Artikelnummer, Kurz-/Langtext, Menge, Originaleinheit, Nettopreis, Preiseinheit, EAN, MwSt., Positionsreferenzen, Angebotsnummer, Währung, Lieferdatum und Bestellbestätigung, soweit geliefert. Fehlende Werte bleiben offen. Beim alten Import waren zusätzliche Felder noch nicht gespeichert; für diese Angaben ist eine erneute Rückgabe aus dem Shop erforderlich.

## Prüfungen

`npm test`, `npm run test:preview`, `npm run lint`, `npm run build` in `react-pc-frontend`. E2E gegen einen getrennten Server: `IDS_PREVIEW_CONFIG_PATH=/tmp/ids-e2e-isolated/application-local.properties EN1090_PREVIEW_PORT=8098 npm run preview:original`. Dort ausschließlich Dummy-Zugangsdaten verwenden, Passwort `dummy-only`. Anschließend `EN1090_PREVIEW_PORT=8098 npm run test:e2e`. Eine Modusprüfung schützt die echte Konfiguration vor Teständerungen. Fremde Hosts sind während der automatischen Tests gesperrt.

Die Browserprüfung umfasst 1440×900, 1536×960 und 1920×1080, einschließlich WKE→Prüfung→WKS→Bestellrückgabe, Preiseinheit, Status, Replay-Sperre und Fehlermeldung bei fehlendem Warenkorb. Reale Käufe werden nicht getestet.

Quelle für WKS: [ITEK IDS-Connect](https://itek.de/wissen/verzeichnis-branchenstandards/ids-connect), Schema Warenkorb senden 2.5.

## Sichtprüfung 24.09.2026

Warenkorb-Screenshots in allen drei Desktopgrößen unter `/tmp/en1090-original-screenshots/ids-return-*.png` tatsächlich betrachtet. Rose/Slate, deutlicher Status, eine Primäraktion, vorhandenes PageLayout/Button/Toast, ruhige Tabelle mit lesbarer Preiseinheit. Bestellaktion überall ohne Scrollen sichtbar. Keine Überlagerung oder abgeschnittenen Felder. Fehlermeldungen sowohl im Inhalt als auch als Toast; offene Preise nicht als Null dargestellt.

## Letzter Prüfstand

1.530 Backend-Tests, 1.787 Frontend-Tests, 19 Vorschau-Vertragstests und 15 Browserprüfungen bestanden; Build und Lint fehlerfrei, Code-Review grün. Ein vorhandener Java-Test verwendete abgelaufene April-Testdaten für eine Methode mit aktuellem Tagesdatum; seine Fixtures wurden relativ zum Ausführungstag korrigiert, Produktionscode blieb gleich.

Der zusätzliche reale Versuch zeigt bei WKE wie WKS `Error.Login.failed`, obwohl die Shop-Seite eine bestehende Benutzerbegrüßung anzeigt. Übertragene Zugangsdaten stimmen bytegenau mit der gespeicherten Konfiguration überein; weder Passwortmaske noch Rand-Leerzeichen werden gesendet. Daher kein bestätigter Live-Nachweis für WKS und kein Kaufabschluss. Welches Passwort zum IDS-Benutzer gehört, ist mit dem Nutzer zu klären.

### Login-Diagnose anschließend geklärt

Der aktuell gespeicherte Passwortwert wich vom Original der bereitgestellten Würth-Mail ab. Ein direkter Vergleichstest mit exakt dem Mailwert führte ohne Loginfehler zu `ViewIDSCatalogService-ViewWKERequest`. Dieser Wert wurde über die vorhandene Einstellungs-API verschlüsselt gespeichert. Danach wurde der echte Browserablauf Projekt 101 → Im Lieferanten-Shop → Würth erfolgreich geprüft: IDS-Zielseite erreicht, Benutzerbegrüßung sichtbar, kein `Error.Login.failed`. Keine Bestellung ausgelöst. Die vorherige Einschätzung eines weiterhin ungeklärten IDS-Zugangs ist damit überholt; der WKS-Kaufabschluss bleibt ungetestet.
