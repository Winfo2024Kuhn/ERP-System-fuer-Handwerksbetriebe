# Kalkulationsprogramm - Deployment Guide (Altweg: JAR unter Windows)

> **Altweg.** Diese Anleitung beschreibt den Betrieb als JAR auf einem Windows-Rechner mit
> Windows-Aufgaben für Start und Sicherung. Für eine Neuinstallation ist
> [`deployment/docker-server`](docker-server/README.md) der empfohlene Weg: Dort laufen
> Sicherung, Nachtupdate und Rollback im Updater-Container, und diese Skripte werden nicht
> gebraucht.

## Übersicht

Dieses Dokument beschreibt die Installation und Konfiguration des Kalkulationsprogramms auf dem
Produktions-Server, inklusive automatischer Backups und Scheduled Tasks. Die einzelnen Skripte
(vor allem das Update) beschreibt [scripts/README.md](scripts/README.md).

## Voraussetzungen

### Software-Anforderungen

- **Windows Server** oder Windows 10/11
- **Java 25** (JRE oder JDK, siehe `pom.xml`)
- **Docker Desktop** mit PostgreSQL. Die Sicherung läuft per `pg_dump` direkt im
  Datenbank-Container – zusätzliche Datenbank-Programme braucht der Rechner nicht.
  **Achtung:** Die Datenbank des docker-server (Container `erp-postgres`) ist von außen nicht
  erreichbar (kein freigegebener Port, nur das interne Docker-Netz). Das JAR braucht deshalb
  ein eigenes PostgreSQL, z. B. einen Container mit `-p 127.0.0.1:5432:5432` (siehe
  `docs/INSTALLATION.md`, Variante 3). Sicherung und Update dann auf diesen Container zeigen
  lassen (`update-production.ps1 -DbContainer <name> -DbUser erp_user`,
  `backup-database.ps1 -DockerPostgresContainer <name> -DockerPostgresUser erp_user`). Ohne
  gelungene Sicherung bricht das Update ab (bewusst ohne: `-SkipBackup`).
- **PowerShell 5.1** oder höher (bereits in Windows enthalten)

### Hardware-Anforderungen

- Optional eine **externe Festplatte** für Backups. Das Backup-Skript findet sie über die
  Datenträgerbezeichnung `INTENSO`, sonst nimmt es Laufwerk `E:`. Fehlt sie, wird trotzdem
  lokal gesichert.

## Installation

### Schritt 1: JAR-Datei deployen

Kopieren Sie die kompilierte JAR-Datei nach `C:\Kalkulationsprogramm\`. Das Startskript sucht
zuerst `C:\Kalkulationsprogramm\Kalkulationsprogramm.jar` und nimmt sonst die neueste `*.jar`
in diesem Ordner – umbenennen ist also nicht nötig.

```powershell
New-Item -ItemType Directory -Path "C:\Kalkulationsprogramm" -Force
Copy-Item "target\Kalkulationsprogramm-<Version>.jar" "C:\Kalkulationsprogramm\"
```

Spätere Updates erledigt `update-production.ps1` (siehe [scripts/README.md](scripts/README.md)).

### Schritt 2: Scripts installieren

```powershell
# Als Administrator PowerShell öffnen
cd "<Repository-Pfad>"

# Installation starten
.\deployment\scripts\install-scheduled-tasks.ps1

# Optional: Mit wöchentlichem Neustart
.\deployment\scripts\install-scheduled-tasks.ps1 -EnableWeeklyRestart
```

Das Installations-Script führt automatisch folgende Schritte aus:
- Kopiert `backup-database.ps1`, `start-kalkulationsprogramm.ps1` und
  `restart-kalkulationsprogramm.ps1` nach `C:\Kalkulationsprogramm\scripts\`
  (anderer Ort: `-DeploymentPath`)
- Erstellt die Windows Scheduled Tasks (siehe unten), alle als Konto `SYSTEM`

Die Verzeichnisse für Backups und Logs legen die Skripte beim ersten Lauf selbst an.

### Schritt 3: Datenbank-Container prüfen

Prüfen Sie, ob der Datenbank-Container läuft, den das JAR benutzt:

```powershell
docker ps --filter name=<container-name>
```

## Konfiguration

Alle Einstellungen sind Parameter am Anfang der Skripte. Sie lassen sich beim Aufruf
überschreiben (z. B. `.\backup-database.ps1 -RetentionDays 60`) oder in der Kopie unter
`C:\Kalkulationsprogramm\scripts\` ändern. Achtung: `install-scheduled-tasks.ps1`
überschreibt diese Kopien beim nächsten Lauf mit dem Stand aus dem Repository.

### Backup-Einstellungen (`backup-database.ps1`)

| Parameter | Standard | Bedeutung |
| --- | --- | --- |
| `LocalStagingDir` | `C:\Kalkulationsprogramm\backups\db` | Lokale Datenbank-Sicherungen |
| `ExternalVolumeLabel` | `INTENSO` | Bezeichnung der externen Festplatte |
| `ExternalFallbackDrive` | `E` | Laufwerk, falls keine Platte mit dieser Bezeichnung gefunden wird |
| `ExternalBackupSubDir` | `Kalkulationsprogramm-Backup` | Ordner auf der externen Platte (darin `db\` und `uploads\`) |
| `OneDriveBackupDir` | OneDrive-Ordner des Benutzers | Cloud-Kopie: Datenbank-Sicherungen direkt im Ordner, Uploads in `uploads\` |
| `LogDir` | `C:\Kalkulationsprogramm\logs\backups` | Backup-Logs |
| `RetentionDays` | `30` | Aufbewahrung der Datenbank-Sicherungen (lokal, extern, OneDrive) |
| `UploadsDir` | `C:\Kalkulationsprogramm\uploads` | Hochgeladene Dateien |
| `DockerPostgresContainer` | `erp-postgres` | Datenbank-Container |
| `DockerPostgresUser` | `postgres` | Datenbank-Benutzer für `pg_dump` |

Der Standard für `OneDriveBackupDir` ist auf den bisherigen Server zugeschnitten – auf einem
anderen Rechner unbedingt anpassen.

Die Uploads werden nicht gezippt, sondern per `robocopy` auf externe Platte und OneDrive
gespiegelt: nur Neues und Geändertes, im Ziel wird nie etwas gelöscht.

### Server-Port

| Skript | Parameter | Standard |
| --- | --- | --- |
| `start-kalkulationsprogramm.ps1` | `-ServerPort` | `8080` |
| `restart-kalkulationsprogramm.ps1` | `-ServerPort` | `8082` |

**Achtung:** Die beiden Standards passen nicht zusammen. Läuft der Server auf 8080, findet der
Neustart ihn auf 8082 nicht und das anschließende Startskript meldet nur „läuft bereits“. Den
Port im Neustart-Skript deshalb auf den echten Port setzen.

### JAR-Pfad

`start-kalkulationsprogramm.ps1 -JarPath "<Pfad>"`. Standard:
`C:\Kalkulationsprogramm\Kalkulationsprogramm.jar`, sonst die neueste `*.jar` im selben Ordner.

## Scheduled Tasks

### Übersicht der installierten Tasks

| Task Name | Zeitplan | Beschreibung |
|-----------|----------|--------------|
| **Kalkulationsprogramm - Database Backup** | Täglich 02:00 Uhr | Datenbank-Sicherung (lokal, externe Platte, OneDrive) und Spiegel der Uploads |
| **Kalkulationsprogramm - Auto Start** | Bei System-Start (2 Min. Verzögerung) | Startet Server automatisch nach Reboot |
| **Kalkulationsprogramm - Weekly Restart** | Sonntags 03:00 Uhr | Wöchentlicher Neustart (optional, `-EnableWeeklyRestart`) |

Nur den Autostart-Task (ohne Backup) richtet `SETUP-AUTOSTART.bat` ein.

### Tasks anzeigen

```powershell
# Alle Kalkulationsprogramm-Tasks
Get-ScheduledTask | Where-Object {$_.TaskName -like "*Kalkulationsprogramm*"} | Format-Table TaskName, State

# Task Scheduler GUI öffnen
taskschd.msc
```

### Task manuell ausführen

```powershell
# Backup manuell starten
Start-ScheduledTask -TaskName "Kalkulationsprogramm - Database Backup"

# Server neu starten (nur wenn mit -EnableWeeklyRestart eingerichtet)
Start-ScheduledTask -TaskName "Kalkulationsprogramm - Weekly Restart"
```

### Task modifizieren

```powershell
# Backup-Zeit ändern (z.B. auf 03:00 Uhr)
$task = Get-ScheduledTask -TaskName "Kalkulationsprogramm - Database Backup"
$trigger = New-ScheduledTaskTrigger -Daily -At "03:00"
Set-ScheduledTask -TaskName $task.TaskName -Trigger $trigger
```

## Manuelle Script-Ausführung

### Backup erstellen

```powershell
cd C:\Kalkulationsprogramm\scripts
.\backup-database.ps1
```

Mit benutzerdefinierten Parametern:
```powershell
.\backup-database.ps1 -ExternalFallbackDrive F -RetentionDays 60
```

### Server starten

```powershell
cd C:\Kalkulationsprogramm\scripts
.\start-kalkulationsprogramm.ps1
```

### Server neu starten

```powershell
cd C:\Kalkulationsprogramm\scripts
.\restart-kalkulationsprogramm.ps1 -ServerPort 8080
```

## Backup-Wiederherstellung

Die Sicherungen (`kalkulationsprogramm_db_<datum>.sql.gz`) sind PostgreSQL-Sicherungen
(`pg_dump --no-owner --no-privileges`).

**Datenbank des docker-server:** Am sichersten spielt sie der Updater aus
`deployment/docker-server` ein. Er sichert vorher den aktuellen Stand nach
`sicherungen\vor-import-…` und startet das ERP danach neu:

```powershell
# Sicherung in den ERP-Ordner legen (Beispiel: C:\ERP-Handwerk), dann dort:
Copy-Item "C:\Kalkulationsprogramm\backups\db\kalkulationsprogramm_db_<datum>.sql.gz" "C:\ERP-Handwerk\"
cd C:\ERP-Handwerk
docker compose exec updater bash /erp/nachtupdate.sh --importieren kalkulationsprogramm_db_<datum>.sql.gz
```

Die `.sql.gz`-Datei muss dafür nicht entpackt werden.

**WARNUNG:** Dies überschreibt die aktuelle Datenbank!

## Troubleshooting

### Problem: Backup schlägt fehl

**Symptom:** Task zeigt Fehler oder Backup-Datei wird nicht erstellt

**Lösungen:**
1. Prüfen Sie die Logs in `C:\Kalkulationsprogramm\logs\backups\`
2. Prüfen Sie, ob Docker Desktop läuft und der Datenbank-Container da ist:
   ```powershell
   docker ps --filter name=erp-postgres
   ```
3. Prüfen Sie, ob das Konto der geplanten Aufgabe (`SYSTEM`) Docker benutzen darf
   (Fehlermeldung von `pg_dump` im Backup-Log).

Fehlen externe Platte oder OneDrive, ist das **kein** Fehler: Die lokale Sicherung gilt, im
Log steht eine Warnung.

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
3. Prüfen Sie, ob eine JAR da ist:
   ```powershell
   Get-ChildItem "C:\Kalkulationsprogramm\*.jar"
   ```
4. Prüfen Sie die Start-Logs in `C:\Kalkulationsprogramm\Logs\`

### Problem: Server läuft, aber antwortet nicht

**Lösungen:**
1. Prüfen Sie, ob Port 8080 offen ist:
   ```powershell
   Test-NetConnection -ComputerName localhost -Port 8080
   ```
2. Prüfen Sie Firewall-Einstellungen
3. Prüfen Sie Server-Logs nach Fehlern
4. Versuchen Sie manuellen Neustart:
   ```powershell
   C:\Kalkulationsprogramm\scripts\restart-kalkulationsprogramm.ps1 -ServerPort 8080
   ```

### Problem: Alte Backups werden nicht gelöscht

**Lösungen:**
1. Prüfen Sie Berechtigungen auf den Backup-Ordnern
2. Führen Sie Backup-Script manuell aus und prüfen Sie Ausgabe
3. Prüfen Sie, ob Backup-Dateien das richtige Namensformat haben
   (`kalkulationsprogramm_db_*.sql.gz`)

### Problem: Externe Festplatte wird nicht gefunden

**Lösungen:**
1. Prüfen Sie, ob die Festplatte angeschlossen ist
2. Prüfen Sie die Datenträgerbezeichnung (`INTENSO`) bzw. den Laufwerksbuchstaben (`E:`)
3. Alternativ: `-ExternalVolumeLabel` bzw. `-ExternalFallbackDrive` anpassen

## Monitoring

### Backup-Status prüfen

```powershell
# Letzte Backups anzeigen
Get-ChildItem "C:\Kalkulationsprogramm\backups\db" | Sort-Object LastWriteTime -Descending | Select-Object -First 10

# Backup-Größen anzeigen
Get-ChildItem "C:\Kalkulationsprogramm\backups\db\*.gz" |
    Select-Object Name, @{Name="Size (MB)";Expression={[math]::Round($_.Length/1MB, 2)}} |
    Sort-Object Name -Descending
```

### Server-Status prüfen

```powershell
# Prüfen ob Server läuft
Test-NetConnection -ComputerName localhost -Port 8080

# Prozess, der auf dem Port lauscht
Get-NetTCPConnection -LocalPort 8080 -State Listen | ForEach-Object { Get-Process -Id $_.OwningProcess }
```

### Log-Dateien prüfen

```powershell
# Neueste Backup-Logs
Get-ChildItem "C:\Kalkulationsprogramm\logs\backups\backup_*.log" | Sort-Object LastWriteTime -Descending | Select-Object -First 1 | Get-Content

# Neueste Server-Start-Logs
Get-ChildItem "C:\Kalkulationsprogramm\Logs\server_start_*.log" | Sort-Object LastWriteTime -Descending | Select-Object -First 1 | Get-Content
```

`restart-kalkulationsprogramm.ps1` schreibt sein Log standardmäßig noch nach
`E:\Kalkulationsprogramm\Logs\` (Parameter `-LogDir`).

## Wartung

### Backup-Verzeichnis bereinigen

Alte Datenbank-Sicherungen werden automatisch nach 30 Tagen gelöscht (lokal, auf der externen
Platte und im OneDrive-Ordner). Für manuelle Bereinigung:

```powershell
# Alle lokalen Backups älter als 60 Tage löschen
$cutoffDate = (Get-Date).AddDays(-60)
Get-ChildItem "C:\Kalkulationsprogramm\backups\db\*.gz" |
    Where-Object {$_.LastWriteTime -lt $cutoffDate} |
    Remove-Item -Verbose
```

### Log-Dateien bereinigen

```powershell
# Logs älter als 90 Tage löschen
$cutoffDate = (Get-Date).AddDays(-90)
Get-ChildItem "C:\Kalkulationsprogramm\logs\backups\*.log", "C:\Kalkulationsprogramm\Logs\*.log" |
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
# ACHTUNG: Löscht alle Daten (JAR, Uploads, lokale Sicherungen, Logs)!
Remove-Item "C:\Kalkulationsprogramm" -Recurse -Force
```

Die Kopien auf der externen Platte (`Kalkulationsprogramm-Backup`) und im OneDrive-Ordner
bleiben liegen, bis sie von Hand gelöscht werden.

## Support

Bei Problemen:
1. Prüfen Sie die Logs in `C:\Kalkulationsprogramm\logs\backups\` und `C:\Kalkulationsprogramm\Logs\`
2. Prüfen Sie den Troubleshooting-Abschnitt in diesem Dokument
3. Kontaktieren Sie den System-Administrator
