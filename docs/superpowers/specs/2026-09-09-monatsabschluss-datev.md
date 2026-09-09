Issue: #102

# Monatsabschluss mit Mehrfachauswahl, Auswertungen und DATEV-Dateiexport

Datum: 2026-09-09

## Ziel und Freigabe

Büromitarbeiter sollen Monatsstände aller Mitarbeiter an einer zentralen Stelle prüfen, filtern und gemeinsam abschließen können. Ein eigener Menüpunkt „Monatsabschluss“ verbindet die Mitarbeiterübersicht mit Monatsauswertungen, Abschlussverlauf und einem DATEV-Dateiexport der abgeschlossenen Werte für das Steuerbüro.

Der Nutzer hat den vorgeschlagenen Umfang mit eigenem Menüpunkt, Mehrfachauswahl, Auswertungen und Sammelabschluss freigegeben. Anschließend hat er den Dateiweg für DATEV ausdrücklich als Bestandteil dieser Seite bestätigt. Eine nachgereichte Probeabrechnung verwendet das Formular LOGN17, das auch die offizielle LODAS-Musterauswertung verwendet. Das ist ein Hinweis auf LODAS, keine ausdrückliche Programmbestätigung. LODAS ist das verbindlich zu unterstützende erste Zielprofil; das Profil wird ausdrücklich konfiguriert. Personenbezogene Angaben aus der Probeabrechnung werden weder hier noch in Testdaten oder im Repository übernommen.

## Nicht-Ziele

- Keine direkte DATEV-Cloud-Anbindung, automatische Übertragung oder Zugangsdatenverwaltung.
- Keine eigene Lohnabrechnung, Steuerberechnung oder Berechnung von Auszahlungsbeträgen.
- Keine beliebige CSV, die ohne nachgewiesenes Zielformat als DATEV-kompatibel bezeichnet wird.
- Keine Änderung der bestehenden Berechnung von Sollstunden, Abwesenheiten, Feiertagen oder Zeitkorrekturen.
- Keine Aufhebung bestehender Berechtigungen und Abschlusssperren; kein Abschluss aktueller oder zukünftiger Monate.
- Kein zusätzlicher Sammelprozess zum Wiederöffnen. Der bestehende einzelne Vorgang bleibt erreichbar.
- Keine fachliche Neugestaltung der mobilen Zeiterfassung. Die nachträglich ausdrücklich beauftragte Vereinheitlichung vorhandener Eingaben und Meldungen gilt auch dort.

## Nachträglich freigegebene Ergänzung: systemeigene Eingaben und Meldungen

Der Nutzer hat nach der Designfreigabe ausdrücklich verlangt, bestehende Browser-Standardfelder und Meldungen in den Oberflächen auf die eigenen Design-Komponenten umzustellen. Das gezeigte Beispiel ist der blaue native Uhrzeit-Picker im Dialog „Arbeitszeit einrichten“. Geöffnete Auswahlelemente müssen ebenso zum Design-System passen wie ihre geschlossenen Felder. Vorhandene gestaltete Eingaben, Selects, DatePicker und Toasts sind wiederzuverwenden; korrekt gestaltete HTML-Inputs müssen nicht allein wegen ihrer HTML-Grundlage ersetzt werden.

Für Mengen-/Dezimalfelder gilt: Eine anfänglich angezeigte 0 (auch `0,00`) wird bei Klick-/Tab-Fokus geleert, Nichtnullwerte bleiben erhalten. Zwischenstände werden als Text bearbeitet; Pflichtzahlen werden bei der Übernahme validiert, leer wird nicht still zu 0. Eingabe und Anzeige verwenden deutsches Dezimalkomma. Kennnummern bleiben Ziffernstrings mit führenden Nullen und sind vom automatischen Leeren ausgenommen. Diese Regeln werden dauerhaft in AGENTS.md und dem Design-Skill festgehalten.

Eine Bestandsaufnahme grenzt zusätzliche dateidisjunkte Tasks für beide Frontends ab. Prüfung: keine sichtbaren nativen Uhrzeit-/Datumspicker, Number-Spinner, Browser-alert/confirm oder fremden Toast-Systeme in den umgestellten Abläufen; eigene Bestätigungsdialoge und Toasts, Tastaturbedienung, Null-Fokusverhalten, Kommawerte, Pflichtfeldfehler sowie bestehende Speicherabläufe im Browser testen.

## Bestand und betroffene Bereiche

Betroffen sind Spring-Boot-Backend und PC-Frontend.

- `src/main/java/org/example/kalkulationsprogramm/domain/MonatsSaldo.java` speichert Soll-, Arbeits-, Abwesenheits-, Feiertags- und Korrekturstunden sowie Abschlussstatus, Version, Zeitpunkt und Akteur. `getGesamtIst()` addiert die vier Ist-Komponenten; `getDifferenz()` zieht davon die Sollstunden ab.
- `src/main/java/org/example/kalkulationsprogramm/service/MonatsSaldoService.java` implementiert Status, Einzelabschluss, Wiederöffnung, gesperrte Zugriffe und Audit. Abgeschlossene Monate bewahren den geprüften Stand, offene Monate werden nach bestehenden Regeln berechnet.
- Die bestehende API liegt unter `/api/zeitverwaltung/monatsabschluesse`; die neue Übersicht und Sammeloperationen ergänzen diesen Bereich.
- `MonatsabschlussBerechtigungService`, `MonatsabschlussDto`, `MonatsabschlussAuditRepository` und Mitarbeiter-/Abteilungsdaten sind vorhandene Integrationspunkte.
- `react-pc-frontend/src/pages/ZeiterfassungKalender.tsx` enthält bisher den Einzelabschluss, den Verlauf und die Rückmeldungen. Neue Seite, Navigation und Routen ergänzen diesen Ablauf.
- Für DATEV werden Zielkonfiguration, Personalnummernzuordnung, Lohnartenmapping, Exportvalidierung und Dateierzeugung ergänzt. Erforderliche persistente Felder erhalten neue Flyway-Migrationen.

## Architektur und Ablauf

### Übersicht und Auswahl

Die Seite „Monatsabschluss“ bietet Monat/Jahr, Mitarbeiter, Abteilung und Status „Alle“, „Offen“ oder „Abgeschlossen“ als Filter. Jede Zeile bezeichnet eindeutig einen Mitarbeiter im gewählten Monat. System-Mitarbeiter, die keinen Monatsabschluss besitzen, sind keine Abschlusskandidaten.

Checkboxen erlauben einzelne Mitarbeiter und „Alle gefilterten Mitarbeiter“ auszuwählen. Diese Gesamtauswahl umfasst die gesamte gefilterte Ergebnismenge, auch bei einer später gewählten Seiteneinteilung. Auswahlanzahl und Umfang werden vor einer Aktion sichtbar; Filter- und Monatswechsel dürfen keine unbemerkte Bearbeitung unsichtbarer alter Auswahlen verursachen.

Die Übersicht zeigt Arbeitsstunden, Sollstunden, Abwesenheiten, Feiertage, Korrekturen, Gesamtstunden und Plus-/Minusstunden sowie Abschlussstatus und Abschlussdatum. Summen beziehen sich erkennbar auf die gefilterte Menge; Aktionsdialoge auf die tatsächliche Auswahl. Die Kategorien werden in Stunden dargestellt und entsprechend der vorhandenen Berechnung addiert, ohne Abwesenheiten oder Feiertage doppelt zu zählen.

### Sammelabschluss

Die primäre Aktion „Ausgewählte Monate abschließen“ zeigt vor Ausführung Zeitraum und Anzahl der betroffenen Mitarbeiter. Das Backend überprüft Rechte, gültige Mitarbeiter und vergangene Monate selbst. Bereits abgeschlossene Einträge werden als solche gemeldet und weder neu berechnet noch erneut festgeschrieben.

Die vorhandene Abschlusslogik bleibt die fachliche Quelle. Der Sammelprozess liefert je Mitarbeiter ein verständliches Ergebnis: abgeschlossen, bereits abgeschlossen oder fehlgeschlagen mit Grund. Ein einzelner fachlicher Fehler darf erfolgreich abschließbare andere Mitarbeiter nicht zurückrollen. Doppelklicks und wiederholte Anfragen dürfen keine doppelten Abschlussereignisse erzeugen. Konkurrierende Änderungen werden durch die bestehenden Sperren und Versionsregeln geschützt und als Konflikt verständlich zurückgemeldet.

Nach Abschluss aktualisieren sich Zeilen, Summen und Status sichtbar. Fehlgeschlagene Einträge bleiben identifizierbar, sodass die zuständige Person gezielt nacharbeiten kann.

### Auswertungen und Verlauf

Ein Monatsvergleich zeigt die gleichen Kennzahlen für mehrere Monate unter nachvollziehbaren Mitarbeiter-/Abteilungsfiltern. Offene Monate sind als vorläufig erkennbar. Für abgeschlossene Monate stammen Anzeige, Vergleich und Export aus dem festgehaltenen Stand; Änderungen heutiger Zeitkontoeinstellungen dürfen historische Werte nicht verändern.

Der vorhandene Abschluss-/Wiederöffnungsverlauf wird pro Mitarbeiter und Monat zugänglich. Ein Kalenderlink öffnet genau diesen Mitarbeiter und Monat für die Detailprüfung und den bestehenden Einzelprozess.

### DATEV-Dateiexport

„Für DATEV exportieren“ ist eine Aktion direkt in „Monatsabschluss“. Der Export umfasst explizit ausgewählte abgeschlossene Mitarbeiter-Monatsstände. Der Abschluss allein löst keinen Export aus. Offene Monate können keine Lohnexportdaten liefern.

Vor Erzeugung müssen ein unterstütztes DATEV-Zielprofil, die dafür notwendigen Betriebs-/Mandantenangaben, Personalnummern und gültige Zuordnungen der zu exportierenden Stundenkategorien zu Lohnarten vorliegen. Das Produkt darf LODAS und Lohn und Gehalt nicht als austauschbare Dateiformate behandeln. Eine Zielauswahl bietet nur nach offizieller Formatspezifikation tatsächlich implementierte Profile an. Die erste Version implementiert ausschließlich LODAS und benennt dieses Ziel in den Einstellungen klar. Lohn und Gehalt bleibt eine spätere Erweiterung. Alle Betriebs-, Personal- und Lohnartennummern werden vom Anwender konfiguriert.

Eine Vorprüfung benennt fehlende oder doppelte Personalnummern, unvollständige Pflichtangaben, ungültige Lohnarten und nicht exportierbare Datensätze. Es entsteht keine unbemerkte Teil-Datei. Die Auswahl muss bei Fehlern korrigiert werden; erst eine vollständig gültige Auswahl wird heruntergeladen. Fachliche Zuordnungen werden nicht geraten: Zeitkontokorrekturen sind beispielsweise nicht automatisch auszuzahlende Mehrarbeit, und aggregierte Abwesenheiten ersetzen keine unbelegte Aufteilung nach Urlaub und Krankheit.

Für neue Abschlüsse wird eine für den Lohnexport geeignete Aufschlüsselung der Abwesenheitskategorien zusammen mit dem Abschluss historisch festgehalten. Arbeitsstunden, Feiertage und die einzelnen Abwesenheitsarten erhalten getrennte Zuordnungen. Nicht für den Lohnexport bestimmte Kategorien können ausdrücklich ausgeschlossen werden; die Vorprüfung nennt diese Ausschlüsse und die betroffenen Stunden, bevor der Nutzer den Export ausführt. Bei älteren Abschlüssen ohne historische Aufschlüsselung wird ein angeforderter Abwesenheitsexport blockiert. Er darf nicht mit heutigen Einzelbuchungen ergänzt werden. Ein explizit auf vorhandene Arbeitsstunden beschränkter Export bleibt mit sichtbarer Nennung der ausgelassenen Kategorien möglich.

Die Datei verwendet die für das gewählte Zielprofil vorgeschriebenen Satzarten, Pflichtfelder, Feldreihenfolge, Zahlen-/Datumsformate, Zeichencodierung und Zeilenenden. Stundenwerte müssen anhand von Dummy-Daten mit der offiziellen Beschreibung beziehungsweise einer offiziellen Importvorlage nachvollziehbar geprüft werden. Eine Datei enthält genau einen Monat und einen Mandanten; eine Auswahl über mehrere Monate ergibt entsprechend getrennte Dateien. Dateiname und Ergebnisanzeige machen Ziel und Zeitraum erkennbar. Personalnummern sind im LODAS-Profil numerisch mit höchstens fünf Stellen, Lohnarten numerisch mit höchstens vier Stellen.

Der Export verändert keinen Abschluss und keinen Monatswert. Beim Download prüft das Backend erneut Berechtigung, Abschlussstatus und den verwendeten Stand, damit ein zwischenzeitlich wieder geöffneter Monat nicht als unverändert abgeschlossen exportiert wird. Ein erneuter Download darf nicht fälschlich als bestätigter Import beim Steuerbüro dargestellt werden.

### API-Verhalten und technische Grenzen

Die Backend-Erweiterung benötigt folgende klar getrennte Fähigkeiten; genaue Routen und DTO-Namen werden im Grobplan festgelegt:

1. Gefilterte Monatsübersicht mit Abschlussmetadaten, Kennzahlen und Summen.
2. Monatsvergleich auf derselben fachlichen Berechnungsgrundlage.
3. Sammelabschluss mit expliziten Mitarbeiter-/Monatsreferenzen und Ergebnis pro Eintrag.
4. Lesen und autorisiertes Speichern der DATEV-Konfiguration und Zuordnungen.
5. Exportvorprüfung und Download für eine explizite Auswahl abgeschlossener Stände.

Controller verwenden DTOs statt Entities. Das Backend begrenzt und validiert Eingaben und prüft den Zugriff auch bei direkt aufgerufenen Endpunkten. Neue Massenabfragen dürfen nicht vollständige Kalender und komplette Auditverläufe für jeden Mitarbeiter laden; Detailverläufe werden bei Bedarf abgefragt. Personenbezogene Daten bleiben auf die für die Aufgabe erforderlichen Informationen beschränkt und erscheinen nicht in Diagnose-Logs.

Die PC-Seite folgt dem vorhandenen Rose-/Slate-Design, den bestehenden Select-, Dialog- und Toast-Komponenten sowie klarer Handwerker-Sprache. Laden, leere Ergebnisse, Fehler, Bearbeitung und Abschluss haben unterscheidbare Zustände. Checkboxen und Aktionen sind per Tastatur erreichbar und verständlich beschriftet.

## Akzeptanzkriterien

1. Ein berechtigter Büromitarbeiter erreicht „Monatsabschluss“ über die Navigation und filtert einen vergangenen Monat nach Mitarbeiter, Abteilung und Status.
2. Einzel- und Gesamtauswahl stimmen mit dem sichtbaren Auswahlumfang überein; ein Filterwechsel löst keine Aktion auf unbemerkt mitgeführten Mitarbeitern aus.
3. Ein Sammelabschluss mit erfolgreichen, bereits abgeschlossenen und fehlerhaften Einträgen liefert eindeutige Einzelergebnisse. Erfolgreiche Einträge besitzen genau einen neuen Abschluss im Verlauf.
4. Aktuelle/zukünftige Monate und unberechtigte direkte API-Aufrufe werden zurückgewiesen. Bestehende Schutzregeln bleiben auch unter konkurrierenden Einzel- und Sammelaktionen wirksam.
5. Einzelwerte, gefilterte Summen und Monatsvergleich verwenden dieselbe Differenzformel. Historische Abschlüsse bleiben bei späteren Konfigurationsänderungen unverändert.
6. Verlauf und Kalenderlink führen zum richtigen Mitarbeiter und Monat.
7. Ein DATEV-Export mit vollständig konfigurierten Dummy-Daten erzeugt eine nach der offiziellen Beschreibung des angebotenen Zielprofils geprüfte Datei mit genau den ausgewählten abgeschlossenen Werten.
8. Fehlende Personalnummer, fehlerhafte Lohnart, nicht unterstütztes Profil oder zwischenzeitliche Wiederöffnung verhindern den Download mit konkreter Korrekturmeldung.
9. Backendtests decken Sammel-Teilergebnisse, Berechtigungen, Snapshot-Treue, relevante Konkurrenzfälle und Formatvalidierung ab. Frontend-/Browserprüfungen decken Filter, Gesamtauswahl, Abschlussrückmeldung, Verlauf und Exportfehler sowie einen erfolgreichen Download ab.

## DATEV-Quellen und belegte Abgrenzung

- [DATEV LODAS ASCII-Import, 94. Auflage, Juni 2026](https://help-center.apps.datev.de/api/amr/knowledge-common/v1/entities/st81064830359671307_de.pdf): offizielle Grundlage für das LODAS-Profil. Belegt sind Abschnitte `[Allgemein]`, `[Satzbeschreibung]` und `[Bewegungsdaten]`, Ziel `LODAS`, `Version_SST=1.0`, `Version_DB=15.70`, Berater-/Mandantennummer und Datumsformat `TT/MM/JJJJ`. Für Standardbewegungsdaten nennt die Beschreibung `u_lod_bwd_buchung_standard` mit Abrechnungszeitraum, Wert, Bearbeitungsschlüssel, eigener Lohnart und Personalnummer. Die Versionsangaben sind laut Fach 2, Seiten 2–5 optional; insbesondere wird keine Datenbankversion fest verdrahtet. Die Implementierung muss die für Stunden gültigen Feldregeln und Grenzen gegen die offizielle Dokumentation prüfen; ein Header allein weist keine Importfähigkeit nach.
- [Offizielle LODAS-Musterauswertung](https://www.datev.de/content/dam/markenassets/themen-und-produktgruppen/zielgruppen/zielgruppenuebergreifend/shop-assets/personalwirtschaft/lodas-musterauswertung-2025-deutsch.pdf): enthält das Formular LOGN17. Das Nutzerbeispiel zeigt eine Zeitlohn-Lohnart, aber keine belastbaren Zuordnungen für Abwesenheitsarten. Beispiel-Lohnarten werden deshalb nicht als universeller Betriebsstandard übernommen.

## Im Grobplan zu entscheidende Punkte

- Die vollständigen Feldregeln des LODAS-Profils, seine Stunden-Bearbeitungsschlüssel und die Zuordnung vorhandener Abwesenheitskategorien zum neuen Abschlusssnapshot werden anhand der offiziellen Beschreibung festgelegt. Lohn und Gehalt wird in dieser Umsetzung nicht implementiert.
- Speicherort und Pflegeoberfläche für Personalnummern, Betriebsangaben und Lohnartenmapping unter Wiederverwendung vorhandener Stammdaten, soweit diese passen.
- Konkretes Snapshot-Datenmodell für die historische Abwesenheitsaufschlüsselung und Kennzeichnung älterer Abschlüsse ohne Details; die beschriebenen Exportblockaden und ausdrücklich angezeigten Ausschlüsse sind verbindlich.
- Konkrete API-Routen, Seiteneinteilung/Anfragegrenzen und technische Transaktionsgrenze pro Sammelergebnis; die beschriebenen fachlichen Ergebnisse sind verbindlich.
- Vergleichszeitraum und Darstellung des Monatsvergleichs sowie die konkrete Auswahlführung für Exporte über mehrere Monate.
