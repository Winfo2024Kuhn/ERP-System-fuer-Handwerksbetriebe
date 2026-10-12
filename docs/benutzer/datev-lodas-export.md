# Monatsstunden für DATEV LODAS exportieren

Der Export erstellt eine TXT-Datei mit Stundenbuchungen für **DATEV LODAS**. Er überträgt nichts automatisch und berechnet weder Löhne noch Steuern. DATEV Lohn und Gehalt verwendet ein anderes Importformat.

## Einmal mit dem Steuerbüro einrichten

Unter **Einstellungen → Zeiterfassung** in der Karte „Stunden ans Steuerbüro & DATEV“ (nur für Admins; der Knopf „DATEV einrichten“ im Monatsabschluss führt dorthin) die Beraternummer (4–7 Ziffern), Mandantennummer (1–5 Ziffern) und im Abschnitt „Mitarbeiter“ die vorhandenen DATEV-Personalnummern (1–5 Ziffern) eintragen. Nummern müssen größer als 0 sein; führende Nullen sind bei der Eingabe erlaubt. Personalnummern müssen auch ohne führende Nullen eindeutig sein. Wer das Recht zum Monatsabschluss hat, kann exportieren, die Einrichtung aber nicht ändern.

**Soll- oder Ist-Stunden:** Unter „Welche Stunden gehen als „Arbeit“ ans Steuerbüro?“ für den ganzen Betrieb festlegen, welche Stunden als „Arbeit“ übergeben werden. Für einzelne Mitarbeiter mit Personalnummer lässt sich im Abschnitt „Mitarbeiter“ davon abweichen („Wie Betrieb“, „Soll-Stunden“ oder „Ist-Stunden“; z. B. Festgehalt = Soll, Stundenlohn = Ist).
- **Soll:** Monats-Soll laut Arbeitszeitkonto minus Feiertage und Abwesenheiten (Urlaub, Krankheit, Fortbildung usw., jeweils mit dem Tages-Soll angerechnet) ergibt die Stunden für „Arbeit“. Feiertage und Abwesenheiten gehen mit ihrer eigenen Lohnart raus; zusammen ergibt das genau das Soll, nichts wird doppelt gezählt. Ist eine Abwesenheitsart auf „Nicht exportieren“ gestellt, fehlen deren Stunden in der Datei (die Vorprüfung zeigt sie als ausgelassen).
- **Ist:** die tatsächlich gestempelten Stunden ohne Pausen.
- Hat ein Soll-Mitarbeiter kein Soll hinterlegt, zeigt die Vorprüfung seine gearbeiteten Stunden als ausgelassen – solche Mitarbeiter auf Ist stellen. Sind Feiertage und Abwesenheiten höher als das Soll, blockiert die Vorprüfung den Export.

Neue Installationen beginnen mit Soll; war DATEV vor dieser Einstellung schon eingerichtet, bleibt es bei Ist, bis jemand umstellt.

Im Abschnitt „Lohnarten“ für **jede** Stundenkategorie „Lohnart zuordnen“ wählen und eine mit dem Steuerbüro abgestimmte, in LODAS eingerichtete Lohnart (1–4 Ziffern) eintragen – oder ausdrücklich „Nicht exportieren“ einstellen. Das betrifft Arbeit, Feiertag, Urlaub, Krankheit, Fortbildung, Zeitausgleich, Krankengeld und Wiedereingliederung. Die Schnittstelle bucht ausschließlich **Stunden mit Bearbeitungsschlüssel 01**. Kategorien, die das Steuerbüro als Tage, Beträge oder Fehlzeiten benötigt, deshalb ausschließen und gesondert bearbeiten. Keine Lohnart wird automatisch als betrieblicher Standard angenommen.

## Pro Monat

1. Unter **Zeiterfassung → Monatsabschluss** die gewünschten Mitarbeiter und den vergangenen Monat prüfen und abschließen (siehe [Monatsabschluss](monatsabschluss.md)).
2. Nur die tatsächlich benötigten Mitarbeiter auswählen und im Bereich „DATEV für das Steuerbüro“ auf **Für DATEV exportieren** klicken. Der Knopf ist gesperrt, solange nichts ausgewählt ist, mehr als 500 Mitarbeiter ausgewählt sind oder ein ausgewählter Monat noch offen ist.
3. Im Fenster „DATEV-Export prüfen“ auf **Vorprüfung starten** klicken und das Ergebnis lesen. Fehlende Nummern oder Zuordnungen korrigieren; ausgeschlossene Stunden („Diese Stunden werden nicht exportiert“) mit dem Häkchen bewusst bestätigen. Zeitkontokorrekturen werden niemals als Auszahlung exportiert.
4. Mit **Datei herunterladen** die TXT speichern und unverändert an das Steuerbüro übergeben. In der Oberfläche wird immer genau ein Monat exportiert (der oben gewählte). Nur wer die Schnittstelle direkt mit mehreren Monaten aufruft, erhält eine ZIP-Datei; vor dem Import entpacken. Jede TXT enthält genau einen Monat.
5. Im passenden LODAS-Mandanten **Mandant → Daten übernehmen → ASCII-Schnittstelle** aufrufen und die TXT-Datei importieren. Den Abrechnungszeitraum, Personalnummern, Lohnarten, Stundensummen und das Importprotokoll prüfen. Der Mandant und die Mitarbeiter müssen dort bereits bestehen. [DATEV-Schnittstellenhandbuch, Fach 1](https://help-center.apps.datev.de/api/amr/knowledge-common/v1/entities/st36028834085966347_de.pdf)

Ein Download bestätigt keinen Import. Ein erneuter Download löscht oder ersetzt keine bereits importierten Buchungen: Wiederholungsimporte und nachträgliche Korrekturen mit dem Steuerbüro abstimmen, um doppelte Buchungen zu vermeiden. Die Datei enthält Personalnummern und Arbeitszeitdaten; über den vereinbarten geschützten Übertragungsweg weitergeben.

## Grenzen und Prüfstand

- Exportiert werden die festgehaltenen Monatswerte. Bei einer Wiederöffnung oder Konfigurationsänderung während des Exports wird der Download abgebrochen; Übersicht/Einstellungen neu laden und erneut prüfen.
- Alte Abschlüsse ohne historische Abwesenheitsaufteilung können nur mit ausdrücklich ausgeschlossenen Abwesenheitskategorien exportiert werden. Heutige Abwesenheitsdaten ergänzen alte Abschlüsse nicht. Die ausgelassenen Gesamtstunden erscheinen in der Vorprüfung.
- Keine Teildateien bei Fehlern. Nullstunden erzeugen keine Buchung; ein Monat ohne exportierbare Stunden wird zurückgewiesen. Höchstens 500 Mitarbeiter-Monatsstände und 12 Monate pro Aufruf.
- Gleiche Personalnummer und Lohnart werden innerhalb eines Monats addiert. Nummern erscheinen in der Datei numerisch, Stunden mit zwei Dezimalstellen und Komma. Keine stillen Rundungen, negativen Stunden oder Feldüberläufe. Die technische Prüfung verwendet die dokumentierte Struktur `NUM 11.2` (höchstens `999999999,99`).
- **Offene DATEV-Verifikation:** Laut Fach 4 können für `bs_wert_butab` je Bearbeitungsschlüssel engere Grenzen gelten. Ein gesondertes verbindliches Höchstmaß für BS 01 konnte aus den verfügbaren offiziellen Auszügen nicht bestätigt werden; `NUM 11.2` ist deshalb keine Zusicherung dieses fachlichen Höchstwerts. Die konkrete LODAS-Einrichtung und ein Probeimport mit dem Steuerbüro bleiben zu prüfen.
- Format-Golden-Test prüft tatsächliche Bytes: Satzbeschreibung/Standardbuchung, Monatsanfang, Reihenfolge, Windows-1252 ohne BOM und CRLF. Alle übertragenen Zeichen liegen im ASCII-Bereich. Tests gegen ein echtes PostgreSQL prüfen konkurrierende Änderungen. **Ein tatsächlicher DATEV-Import und eine DATEV-Zertifizierung wurden nicht durchgeführt.**

Formatbelege: [94. Auflage, Juni 2026, Fach 3 Musterdatei und BS 01 Stunden](https://help-center.apps.datev.de/api/amr/knowledge-common/v1/entities/st81064830359671307_de.pdf), [93. Auflage, Fach 4 Tabelle 3.9: Personalnummer 5, Lohnart 4, Wert 11.2](https://help-center.apps.datev.de/api/amr/knowledge-common/v1/entities/st72057631104930315_de.pdf), [Fach 4 Erläuterung der Längen und BS-abhängigen Grenzen](https://help-center.apps.datev.de/api/amr/knowledge-common/v1/entities/st54043232595448331_de.pdf). Bei der Umsetzung am 09.09.2026 waren diese Dokumente als offizielle Suchindexauszüge zugänglich; die direkten PDF-Downloads wurden auf die DATEV-Wissensplattform umgeleitet.
