# Kalkulationsprogramm - Deployment Guide

## Übersicht

Dieses Dokument beschreibt die Installation und Konfiguration des Kalkulationsprogramms auf dem Produktions-Server, inklusive automatischer Backups und Scheduled Tasks.

## Voraussetzungen

### Software-Anforderungen

- **Windows Server** oder Windows 10/11
- **Java 17 oder höher** ([Download](https://www.oracle.com/java/technologies/downloads/))
- **MySQL Client Tools** (für `mysqldump.exe`)
  - MySQL Server installieren oder nur Client Tools
  - [MySQL Download](https://dev.mysql.com/downloads/mysql/)
- **PowerShell 5.1** oder höher (bereits in Windows enthalten)

### Hardware-Anforderungen

- **Externe Festplatte E:\** für Backups (empfohlen: mindestens 50 GB frei)
- **Netzwerkverbindung** zum MySQL-Server (192.168.x.x:3307)

## Installation

### Schritt 1: Verzeichnisstruktur erstellen

```powershell
# Deployment-Verzeichnis
New-Item -ItemType Directory -Path "C:\Kalkulationsprogramm" -Force

# Backup-Verzeichnis auf externer Festplatte
New-Item -ItemType Directory -Path "E:\Kalkulationsprogramm\Backups" -Force
New-Item -ItemType Directory -Path "E:\Kalkulationsprogramm\Logs" -Force
```

### Schritt 2: JAR-Datei deployen

1. Kopieren Sie die kompilierte JAR-Datei nach `C:\Kalkulationsprogramm\`
2. Benennen Sie die Datei um zu: `Kalkulationsprogramm.jar`

```powershell
# Beispiel
Copy-Item "\\Pfad\zur\Kalkulationsprogramm-1.0.0.jar" "C:\Kalkulationsprogramm\Kalkulationsprogramm.jar"
```

### Schritt 3: Scripts installieren

**Im Entwicklungs-Repository:**

```powershell
# Als Administrator PowerShell öffnen
cd "<Repository-Pfad>\Handwerkerprogramm"

# Installation starten
.\deployment\scripts\install-scheduled-tasks.ps1

# Optional: Mit wöchentlichem Neustart
.\deployment\scripts\install-scheduled-tasks.ps1 -EnableWeeklyRestart
```

Das Installations-Script führt automatisch folgende Schritte aus:
- Kopiert alle Scripts nach `C:\Kalkulationsprogramm\scripts\`
- Erstellt Windows Scheduled Tasks
- Konfiguriert Autostart und Backups

### Schritt 4: MySQL Client verifizieren

Prüfen Sie, ob `mysqldump` verfügbar ist:

```powershell
# Testen
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe" --version
```

Falls nicht gefunden, installieren Sie MySQL Server oder die Client Tools.

### Schritt 5: Externe Festplatte prüfen

Stellen Sie sicher, dass Laufwerk E:\ verfügbar ist:

```powershell
Get-PSDrive E
```

## Konfiguration

### Backup-Einstellungen anpassen

Bearbeiten Sie `C:\Kalkulationsprogramm\scripts\backup-database.ps1`:

```powershell
# Parameter am Anfang der Datei
param(
    [string]$BackupDir = "E:\Kalkulationsprogramm\Backups",     # Backup-Verzeichnis
    [string]$LogDir = "E:\Kalkulationsprogramm\Logs",           # Log-Verzeichnis
    [int]$RetentionDays = 30                                     # Aufbewahrungszeit
)
```

### Server-Port anpassen

Falls Ihr Server einen anderen Port verwendet, bearbeiten Sie:

**In `start-kalkulationsprogramm.ps1`:**
```powershell
param(
    [int]$ServerPort = 8082  # Ändern Sie diesen Wert
)
```

**In `restart-kalkulationsprogramm.ps1`:**
```powershell
param(
    [int]$ServerPort = 8082  # Ändern Sie diesen Wert
)
```

### JAR-Pfad anpassen

Falls die JAR an einem anderen Ort liegt:

**In `start-kalkulationsprogramm.ps1`:**
```powershell
param(
    [string]$JarPath = "C:\Kalkulationsprogramm\Kalkulationsprogramm.jar"
)
```

## Scheduled Tasks

### Übersicht der installierten Tasks

| Task Name | Zeitplan | Beschreibung |
|-----------|----------|--------------|
| **Kalkulationsprogramm - Database Backup** | Täglich 02:00 Uhr | Erstellt komprimiertes Datenbank-Backup auf E:\ |
| **Kalkulationsprogramm - Auto Start** | Bei System-Start | Startet Server automatisch nach Reboot |
| **Kalkulationsprogramm - Weekly Restart** | Sonntags 03:00 Uhr | Wöchentlicher Neustart (optional) |

### Tasks anzeigen

```powershell
# Alle Kalkulationsprogramm-Tasks
Get-ScheduledTask | Where-Object {$_.TaskName -like "*Kalkulationsprogramm*"} | Format-Table TaskName, State, LastRunTime, NextRunTime

# Task Scheduler GUI öffnen
taskschd.msc
```

### Task manuell ausführen

```powershell
# Backup manuell starten
Start-ScheduledTask -TaskName "Kalkulationsprogramm - Database Backup"

# Server neu starten
Start-ScheduledTask -TaskName "Kalkulationsprogramm - Weekly Restart"
```

### Task modifizieren

```powershell
# Backup-Zeit ändern (z.B. auf 03:00 Uhr)
$task = Get-ScheduledTask -TaskName "Kalkulationsprogramm - Database Backup"
$trigger = New-ScheduledTaskTrigger -Daily -At "03:00"
Set-ScheduledTask -InputObject $task -Trigger $trigger
```

## Manuelle Script-Ausführung

### Backup erstellen

```powershell
cd C:\Kalkulationsprogramm\scripts
.\backup-database.ps1
```

Mit benutzerdefinierten Parametern:
```powershell
.\backup-database.ps1 -BackupDir "F:\Backups" -RetentionDays 60
```

### Server starten

```powershell
cd C:\Kalkulationsprogramm\scripts
.\start-kalkulationsprogramm.ps1
```

### Server neu starten

```powershell
cd C:\Kalkulationsprogramm\scripts
.\restart-kalkulationsprogramm.ps1
```

## Backup-Wiederherstellung

### Backup-Datei dekomprimieren

```powershell
# GZip-Datei dekomprimieren
$backupFile = "E:\Kalkulationsprogramm\Backups\kalkulationsprogramm_20251128_020000.sql.gz"
$outputFile = "C:\Temp\restore.sql"

# PowerShell-Dekomprimierung
$sourceStream = [System.IO.File]::OpenRead($backupFile)
$gzipStream = New-Object System.IO.Compression.GZipStream($sourceStream, [System.IO.Compression.CompressionMode]::Decompress)
$outputStream = [System.IO.File]::Create($outputFile)
$gzipStream.CopyTo($outputStream)
$outputStream.Close()
$gzipStream.Close()
$sourceStream.Close()
```

### Datenbank wiederherstellen

```powershell
# MySQL Restore
$mysqlPath = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
$sqlFile = "C:\Temp\restore.sql"

& $mysqlPath --host=192.168.x.x --port=3307 --user=Marvin --password=YOUR_DB_PASSWORD kalkulationsprogramm_db < $sqlFile
```

**WARNUNG:** Dies überschreibt die aktuelle Datenbank!

## Troubleshooting

### Problem: Backup schlägt fehl

**Symptom:** Task zeigt Fehler oder Backup-Datei wird nicht erstellt

**Lösungen:**
1. Prüfen Sie die Logs in `E:\Kalkulationsprogramm\Logs\`
2. Prüfen Sie, ob externe Festplatte E:\ verfügbar ist
3. Prüfen Sie MySQL-Verbindung:
   ```powershell
   Test-NetConnection -ComputerName 192.168.x.x -Port 3307
   ```
4. Prüfen Sie mysqldump-Pfad:
   ```powershell
   Test-Path "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe"
   ```

### Problem: Server startet nicht automatisch

**Lösungen:**
1. Prüfen Sie Task-Status:
   ```powershell
   Get-ScheduledTask -TaskName "Kalkulationsprogramm - Auto Start"
   ```
2. Prüfen Sie Java-Installation:
   ```powershell
   java -version
   ```
3. Prüfen Sie JAR-Pfad:
   ```powershell
   Test-Path "C:\Kalkulationsprogramm\Kalkulationsprogramm.jar"
   ```
4. Prüfen Sie Server-Logs in `E:\Kalkulationsprogramm\Logs\`

### Problem: Server läuft, aber antwortet nicht

**Lösungen:**
1. Prüfen Sie, ob Port 8082 offen ist:
   ```powershell
   Test-NetConnection -ComputerName localhost -Port 8082
   ```
2. Prüfen Sie Firewall-Einstellungen
3. Prüfen Sie Server-Logs nach Fehlern
4. Versuchen Sie manuellen Neustart:
   ```powershell
   C:\Kalkulationsprogramm\scripts\restart-kalkulationsprogramm.ps1
   ```

### Problem: Alte Backups werden nicht gelöscht

**Lösungen:**
1. Prüfen Sie Berechtigungen auf E:\
2. Führen Sie Backup-Script manuell aus und prüfen Sie Ausgabe
3. Prüfen Sie, ob Backup-Dateien das richtige Namensformat haben

### Problem: Externe Festplatte E:\ nicht verfügbar

**Lösungen:**
1. Prüfen Sie, ob Festplatte angeschlossen ist
2. Ändern Sie Laufwerksbuchstaben in der Datenträgerverwaltung
3. Alternativ: Ändern Sie Backup-Pfad in den Scripts auf anderes Laufwerk

## Monitoring

### Backup-Status prüfen

```powershell
# Letzte Backups anzeigen
Get-ChildItem "E:\Kalkulationsprogramm\Backups" | Sort-Object LastWriteTime -Descending | Select-Object -First 10

# Backup-Größen anzeigen
Get-ChildItem "E:\Kalkulationsprogramm\Backups\*.gz" | 
    Select-Object Name, @{Name="Size (MB)";Expression={[math]::Round($_.Length/1MB, 2)}} |
    Sort-Object Name -Descending
```

### Server-Status prüfen

```powershell
# Prüfen ob Server läuft
Test-NetConnection -ComputerName localhost -Port 8082

# Server-Prozess finden
Get-Process java | Where-Object {$_.MainWindowTitle -match "Kalkulation"}
```

### Log-Dateien prüfen

```powershell
# Neueste Backup-Logs
Get-ChildItem "E:\Kalkulationsprogramm\Logs\backup_*.log" | Sort-Object LastWriteTime -Descending | Select-Object -First 1 | Get-Content

# Neueste Server-Start-Logs
Get-ChildItem "E:\Kalkulationsprogramm\Logs\server_start_*.log" | Sort-Object LastWriteTime -Descending | Select-Object -First 1 | Get-Content
```

## Wartung

### Backup-Verzeichnis bereinigen

Alte Backups werden automatisch nach 30 Tagen gelöscht. Für manuelle Bereinigung:

```powershell
# Alle Backups älter als 60 Tage löschen
$cutoffDate = (Get-Date).AddDays(-60)
Get-ChildItem "E:\Kalkulationsprogramm\Backups\*.gz" | 
    Where-Object {$_.LastWriteTime -lt $cutoffDate} | 
    Remove-Item -Verbose
```

### Log-Dateien bereinigen

```powershell
# Logs älter als 90 Tage löschen
$cutoffDate = (Get-Date).AddDays(-90)
Get-ChildItem "E:\Kalkulationsprogramm\Logs\*.log" | 
    Where-Object {$_.LastWriteTime -lt $cutoffDate} | 
    Remove-Item -Verbose
```

## Deinstallation

### Tasks entfernen

```powershell
# Alle Kalkulationsprogramm-Tasks löschen
Get-ScheduledTask | Where-Object {$_.TaskName -like "*Kalkulationsprogramm*"} | Unregister-ScheduledTask -Confirm:$false
```

### Scripts und Daten entfernen

```powershell
# ACHTUNG: Löscht alle Daten!
Remove-Item "C:\Kalkulationsprogramm" -Recurse -Force
Remove-Item "E:\Kalkulationsprogramm" -Recurse -Force
```

## Support

Bei Problemen:
1. Prüfen Sie die Logs in `E:\Kalkulationsprogramm\Logs\`
2. Prüfen Sie den Troubleshooting-Abschnitt in diesem Dokument
3. Kontaktieren Sie den System-Administrator
