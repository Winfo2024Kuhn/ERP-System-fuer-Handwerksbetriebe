# ERP-Handwerk – Lokale Installation (für Handwerker)

## Was ist das?

Dieses Script richtet **ERP-Handwerk** auf Ihrem Windows-PC ein.
Nach der Einrichtung können Sie das Programm wie jede andere Anwendung
per Doppelklick vom Desktop starten.

## Voraussetzungen

- **Windows 10 oder 11**
- **Java 23 oder neuer** – Download: https://adoptium.net/de/temurin/releases/
  - Bei der Installation: "PATH-Variable setzen" aktivieren

## Einrichtung (einmalig)

1. **Java installieren** (falls noch nicht vorhanden)
2. **Doppelklick** auf `Einrichtung-ERP-Handwerk.bat`
3. Sicherheitsabfragen mit **Ja** bestätigen
4. Fertig! Auf dem Desktop erscheint das Symbol **ERP-Handwerk**

## Tägliche Nutzung

### Programm starten
- **Doppelklick** auf das Desktop-Symbol **ERP-Handwerk**
- Der Server startet im Hintergrund (kein schwarzes Fenster)
- Der Browser öffnet sich automatisch

### Nach PC-Neustart
- Der Server startet automatisch nach der Anmeldung
- Klicken Sie einfach auf das Desktop-Symbol

### Programm stoppen
- Doppelklick auf `ERP-Handwerk-Stoppen.bat` im Ordner `C:\ERP-Handwerk\`

## Was wird installiert?

| Was | Wo |
|---|---|
| Anwendung (JAR) | `C:\ERP-Handwerk\` |
| Datenbank (H2) | `%USERPROFILE%\ERP-Handwerk\datenbank` |
| Logs | `C:\ERP-Handwerk\logs\` |
| Desktop-Verknüpfung | Desktop |
| Autostart | Windows Aufgabenplanung |

## Deinstallation

1. Desktop-Symbol löschen
2. Ordner `C:\ERP-Handwerk\` löschen
3. Ordner `%USERPROFILE%\ERP-Handwerk\` löschen (Datenbank)
4. Windows-Aufgabenplanung öffnen (taskschd.msc) → Task "ERP-Handwerk - Autostart" löschen

## Hilfe

- **Server startet nicht?** → Prüfen Sie ob Java installiert ist: `java -version` in der Eingabeaufforderung
- **Browser zeigt Fehler?** → Warten Sie 10-15 Sekunden und laden Sie die Seite neu (F5)
- **Log-Datei prüfen:** `C:\ERP-Handwerk\logs\server.log`
