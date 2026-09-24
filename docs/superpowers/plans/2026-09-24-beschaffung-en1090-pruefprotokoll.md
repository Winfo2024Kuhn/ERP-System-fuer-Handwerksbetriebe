# Beschaffung: Prüfprotokoll und lokale Testanwendung

## Umgesetzter Ablauf

Bedarfsübersicht, Projektliste, Materialdialog und Katalogauswahl basieren auf den Komponenten aus `feature/en1090-echeck`. Sie verwenden die aktuelle Einkaufs-API. Freie Bedarfe funktionieren mit und ohne Projekt. Werkstatt-Rückmeldungen speichern vorhandene Mengen mit Versionsprüfung; nur verbleibende Fehlmengen gehen in Bestellungen über. Preise dürfen fehlen, auch bei Freigabe und Versand. Interne Bestellnummern bleiben erhalten.

Die druckbare Werkstattliste verwendet eine POST-Auswahl, damit viele Positionen nicht an der URL-Länge scheitern. Die Rückmeldung ist keine Lagerverwaltung und erzeugt keine Bestandsführung.

## Nachweise

- PC-Unit: 190 Testdateien, 1.787 Tests erfolgreich; `/tmp/erp-beschaffung-unit-final.log`.
- PC-Lint: erfolgreich; `/tmp/erp-beschaffung-lint-final.log`.
- PC-Build: erfolgreich; `/tmp/erp-beschaffung-build-final.log`.
- Vollständiger neuer Browserablauf: alle drei Größen erfolgreich; `/tmp/beschaffung-workflow-e2e.log`.
- Weitere angepasste Browserabläufe für Zeichnungsteile, HiCAD und Werkstattprüfung: alle drei Größen erfolgreich.
- Vollständige PC-E2E-Suite vor dem Kategorien-Nachtrag: 768/768 erfolgreich; `/tmp/erp-beschaffung-full-e2e.log`. Abschließender Lauf einschließlich Kategorien: `/tmp/erp-beschaffung-final-e2e.log`.
- Vollständige Backend-Suite: 3.620 Tests, keine Fehler; 17 bedingt übersprungen (`/tmp/bedarf-backend-full.log`). Die opt-in MySQL-Prüfungen wurden danach gegen eine neu angelegte, anschließend entfernte Dummy-Datenbank ausgeführt: 17/17 erfolgreich, keine Skips (`/tmp/bedarf-backend-optin-final.log`). Vier bestehende Audit-Diagnosefälle mit fest verdrahteter lokaler Datenbank wurden nicht ausgeführt.
- Abschließende Backend-Kategorie- und Mengenprüfungen: 49/49 erfolgreich (`/tmp/bedarf-final-category-rounding.log`).

Die Browserprüfung deckt Drucken und PDF-Download, gespeicherte Werkstattmengen, offene Preise, interne Bestellnummer, Vorschau/Freigabe, verhindertes Doppelbestellen und frei erfassten Vorratsbedarf nach Neuladen ab. Browser-APIs sind mit Dummy-Daten simuliert; Backend-Integrationstests verwenden separate Testdatenbanken.

## Sichtprüfung

Geprüfte Größen: 1440 × 900, 1536 × 960 und 1920 × 1080. Screenshots liegen in `/tmp/beschaffung-workflow-e2e/design/` und `/tmp/bestellung-e2e-results/design/`.

| Frage | Ergebnis in den drei Größen |
| --- | --- |
| Farben und Zustände | Rose kennzeichnet die Hauptaktion und Fehlmengen, Slate die Struktur; gespeicherte und reservierte Zustände sind auch als Text erkennbar. |
| Design-System | Vorhandene Auswahl-, Zahlen- und Datumsfelder, Lucide-Symbole sowie Rose/Slate werden wiederverwendet. |
| Look-and-Feel | Tabellenspalten und Formularfelder sind ausgerichtet; keine sichtbare Überlagerung oder abgeschnittene Bedienelemente. |
| Bedienbarkeit | „Benötigt“, „Vorhanden“ und „Zu bestellen“ erklären die Mengen. „Preis offen“ bleibt sichtbar; Speichern und Reservierung werden bestätigt. |
| Auffindbarkeit | Material erfassen, Liste drucken und Fehlmengen übernehmen sind im Kopfbereich erreichbar; Vorrat ist auch ohne vorhandenen Datensatz erreichbar. |
| Überschneidungen | Die automatischen Überlauf-/Sichtbarkeitsprüfungen und die betrachteten Screenshots zeigen keine blockierenden Überschneidungen. |

## Lokale Bereitstellung

Die vorhandene Testanwendung auf `http://127.0.0.1:8099/` wurde am 24.09.2026 mit dem neuen Build gestartet. Die vorhandene Konfiguration, isolierte Testdatenbank und Uploads wurden beibehalten. Es gibt keine neuen oder veränderten SQL-Migrationen gegenüber ihrer vorherigen Laufzeit.

Die Laufzeit verwendet eine private Kopie der kompilierten Klassen und des Frontends unter dem bestehenden Laufzeitverzeichnis. Spätere Maven-Tests können diese Kopie nicht überschreiben. Der HTTP-Abruf von `/` wurde mit dem neuen Build bytegenau verglichen. Nach einem Neustart kann eine erneute Anmeldung über die Startseite erforderlich sein. Echter E-Mail-Versand bleibt entsprechend der bestehenden Testkonfiguration deaktiviert.

## Umgang mit Vorarbeiten

Die Arbeitskopie enthielt bereits umfangreiche vorgemerkte und nicht vorgemerkte Änderungen. Der Aufgabenvergleich erfolgt gegen `/tmp/erp-beschaffung-en1090-baseline`. Fremdänderungen und Index wurden nicht zurückgesetzt. Ein Commit wird nicht mit fremden vorgemerkten Änderungen vermischt.

## Rückmeldung aus dem Nutzertest

Die übernommene Kategorieauswahl verwendete zunächst die nicht vorhandene Route `/api/kategorien` und das falsche Namensfeld. Korrigiert auf `/api/artikel/kategorien/alle` mit `bezeichnung`; das DTO liefert zusätzlich `parentId`, damit die Hierarchie erhalten bleibt. Ladefehler zeigen jetzt eine Wiederholungsaktion. Eine neue Regression und Browserprüfung verwenden ausschließlich den echten API-Vertrag und lassen falsche Kategorienrouten ausdrücklich fehlschlagen. Das vom Nutzer nicht gewünschte Feld „Warengruppe“ samt unnötigem Kategorienabruf wurde aus der Bedarfserfassung entfernt. Die Auswahl zum Filtern des Artikelstamms bleibt vorhanden.

Der abschließende fokussierte Code-Review bewertet die Korrekturen grün; der zuvor gemeldete Rundungsfall für zwei sehr präzise Profilzuschnitte ist behoben.
