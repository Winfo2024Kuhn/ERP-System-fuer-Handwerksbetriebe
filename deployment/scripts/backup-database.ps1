# =========================================================
# Automatisches Datenbank- und Uploads-Backup Script
# Kalkulationsprogramm
# =========================================================
# Datenbank: taeglicher Dump (.sql.gz), 30 Tage aufbewahrt, an
# DREI Orten:
#   1. Lokal (immer verfuegbar)
#   2. Externe Festplatte (falls angeschlossen, siehe unten)
#   3. OneDrive (Cloud-Sync)
#
# Uploads: KEIN taegliches Voll-ZIP mehr. Frueher wurde jede Nacht
# der komplette uploads-Ordner (~8 GB) gezippt und 30 Tage lang
# lokal UND in OneDrive aufbewahrt -> ~490 GB fuer ~10 GB Daten,
# C: lief voll. Jetzt wird uploads per robocopy inkrementell nach
# OneDrive und auf die externe Platte gespiegelt: nur neue/geaenderte
# Dateien werden kopiert, jede Datei liegt genau einmal im Backup.
# Bewusst OHNE /PURGE bzw. /MIR: Im ERP geloeschte Dateien bleiben
# im Backup erhalten. Gegen Ueberschreiben (z. B. Ransomware) hilft
# zusaetzlich der Versionsverlauf von OneDrive.
# Lokal wird uploads nicht mehr gesichert - eine zweite Kopie auf
# derselben Platte schuetzt nicht vor Plattenausfall.
# Alte uploads_*.zip werden von der Aufbewahrung weiter nach 30
# Tagen entfernt, damit Altbestaende von selbst verschwinden.
#
# Externe Platte: wird ueber das Volume-Label gefunden (USB-Platten
# bekommen nicht immer denselben Laufwerksbuchstaben); falls nicht
# gefunden, wird $ExternalFallbackDrive versucht. Die Intenso-Platte
# ist FAT32 (max. 4 GB pro Datei) - deshalb ebenfalls Spiegel statt
# ZIP, und robocopy /FFT wegen der 2-Sekunden-Zeitstempel von FAT.
#
# WICHTIG: Externe Platte und OneDrive sind ABSICHTLICH kein hartes
# Abbruchkriterium. Es wird IMMER zuerst lokal gesichert; externe
# Platte und OneDrive sind zusaetzliche, unabhaengige Kopien.
# =========================================================

param(
    [string]$LocalStagingDir = "C:\Kalkulationsprogramm\backups\db",
    [string]$ExternalVolumeLabel = "INTENSO",
    [string]$ExternalFallbackDrive = "E",
    [string]$ExternalBackupSubDir = "Kalkulationsprogramm-Backup",
    [string]$OneDriveBackupDir = "C:\Users\bausc\OneDrive\backup_handwerkerprogramm",
    [string]$LogDir = "C:\Kalkulationsprogramm\logs\backups",
    [int]$RetentionDays = 30,
    [string]$UploadsDir = "C:\Kalkulationsprogramm\uploads",
    [string]$DbPropertiesFile = "C:\Kalkulationsprogramm\config\application-local.properties"
)

# Konfiguration
# Die DB-Zugangsdaten stehen NICHT in diesem Script (das Repo ist oeffentlich),
# sondern werden aus der application-local.properties der Anwendung gelesen.
# update-production.ps1 kopiert diese Datei bei jedem Update aus dem (per
# .gitignore ausgeschlossenen) src/main/resources des Repos nach config\.
function Read-DbConfig {
    param([string]$PropertiesFile)

    if (-not (Test-Path $PropertiesFile)) {
        return $null
    }
    $props = @{}
    foreach ($line in Get-Content $PropertiesFile) {
        if ($line -match '^\s*([^#!\s][^=]*?)\s*=\s*(.*)$') {
            $props[$matches[1]] = $matches[2].Trim()
        }
    }
    $url = $props['spring.datasource.url']
    if (-not $url -or $url -notmatch '^jdbc:(?:mysql|mariadb)://([^:/?]+)(?::(\d+))?/([^?;]+)') {
        return $null
    }
    return @{
        Host     = $matches[1]
        Port     = $(if ($matches[2]) { $matches[2] } else { "3306" })
        Name     = $matches[3]
        User     = $props['spring.datasource.username']
        Password = $props['spring.datasource.password']
    }
}

$dbConfig = Read-DbConfig $DbPropertiesFile
if (-not $dbConfig -or -not $dbConfig.User) {
    Write-Host "FEHLER: DB-Zugangsdaten nicht lesbar aus $DbPropertiesFile (spring.datasource.url/username/password)"
    if (Test-Path $LogDir) {
        Add-Content -Path (Join-Path $LogDir "backup_$(Get-Date -Format 'yyyyMMdd_HHmmss').log") -Value "[ERROR] DB-Zugangsdaten nicht lesbar aus $DbPropertiesFile"
    }
    exit 1
}
$DB_HOST = $dbConfig.Host
$DB_PORT = $dbConfig.Port
$DB_NAME = $dbConfig.Name
$DB_USER = $dbConfig.User
$DB_PASSWORD = $dbConfig.Password

# Alternative Pfade für Dump-Tools falls nicht gefunden
$MYSQLDUMP_ALTERNATIVES = @(
    "C:\Program Files\MariaDB 11.4\bin\mariadb-dump.exe",
    "C:\Program Files\MariaDB 11.3\bin\mariadb-dump.exe",
    "C:\Program Files\MariaDB 11.2\bin\mariadb-dump.exe",
    "C:\Program Files\MariaDB 10.11\bin\mariadb-dump.exe",
    "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe",
    "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe",
    "C:\Program Files\MySQL\MySQL Server 8.3\bin\mysqldump.exe",
    "C:\Program Files\MySQL\MySQL Server 9.0\bin\mysqldump.exe",
    "C:\Program Files (x86)\MySQL\MySQL Server 8.4\bin\mysqldump.exe",
    "C:\Program Files (x86)\MySQL\MySQL Server 8.0\bin\mysqldump.exe",
    "mariadb-dump.exe", # Falls im PATH
    "mysqldump.exe"  # Falls im PATH
)

# Zeitstempel für Backup-Datei
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupFileName = "kalkulationsprogramm_db_${timestamp}.sql"
$localBackupFilePath = Join-Path $LocalStagingDir $backupFileName
$localCompressedFilePath = "${localBackupFilePath}.gz"
$logFileName = "backup_${timestamp}.log"
$logFilePath = Join-Path $LogDir $logFileName

# =========================================================
# Funktionen
# =========================================================

function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logMessage = "[$ts] [$Level] $Message"
    Write-Host $logMessage

    if (Test-Path $LogDir) {
        Add-Content -Path $logFilePath -Value $logMessage
    }
}

function Ensure-Directory {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        try {
            New-Item -ItemType Directory -Path $Path -Force | Out-Null
            Write-Log "Verzeichnis erstellt: $Path"
        }
        catch {
            Write-Log "Fehler beim Erstellen des Verzeichnisses $Path : $_" "ERROR"
            return $false
        }
    }
    return $true
}

function Find-MySQLDump {
    foreach ($path in $MYSQLDUMP_ALTERNATIVES) {
        if ($path -eq "mysqldump.exe" -or $path -eq "mariadb-dump.exe") {
            $cmdName = [System.IO.Path]::GetFileNameWithoutExtension($path)
            $cmd = Get-Command $cmdName -ErrorAction SilentlyContinue
            if ($cmd) {
                return $cmd.Source
            }
        }
        elseif (Test-Path $path) {
            return $path
        }
    }
    return $null
}

function Resolve-ExternalBackupRoot {
    # Liefert "<Laufwerk>:\<Unterordner>" der externen Platte oder $null.
    $letter = $null
    $volume = Get-Volume -FileSystemLabel $ExternalVolumeLabel -ErrorAction SilentlyContinue |
              Where-Object { $_.DriveLetter } | Select-Object -First 1
    if ($volume) {
        $letter = "$($volume.DriveLetter)"
    }
    elseif (Get-PSDrive -Name $ExternalFallbackDrive -ErrorAction SilentlyContinue) {
        $letter = $ExternalFallbackDrive
    }
    if (-not $letter) {
        return $null
    }

    $drive = Get-PSDrive -Name $letter -ErrorAction SilentlyContinue
    if ($drive -and $drive.Free -lt 1GB) {
        Write-Log "WARNUNG: Weniger als 1GB freier Speicherplatz auf ${letter}:\" "WARN"
    }

    return "${letter}:\$ExternalBackupSubDir"
}

function Compress-File {
    param(
        [string]$SourceFile,
        [string]$DestinationFile
    )

    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem

        $sourceStream = [System.IO.File]::OpenRead($SourceFile)
        $destinationStream = [System.IO.File]::Create($DestinationFile)
        $gzipStream = New-Object System.IO.Compression.GZipStream($destinationStream, [System.IO.Compression.CompressionMode]::Compress)

        $sourceStream.CopyTo($gzipStream)

        $gzipStream.Close()
        $destinationStream.Close()
        $sourceStream.Close()

        return $true
    }
    catch {
        Write-Log "Fehler beim Komprimieren der Datei: $_" "ERROR"
        return $false
    }
}

function Sync-UploadsDirectory {
    param(
        [string]$Label,
        [string]$SourceDir,
        [string]$DestinationDir
    )

    if (-not (Test-Path $SourceDir)) {
        Write-Log "WARNUNG: Uploads-Verzeichnis nicht gefunden: $SourceDir" "WARN"
        return $false
    }

    if (-not (Ensure-Directory $DestinationDir)) {
        Write-Log "$Label (uploads) uebersprungen: Zielverzeichnis nicht erreichbar" "WARN"
        return $false
    }

    # /E kopiert Unterordner, ohne /PURGE: im Ziel wird nie etwas geloescht.
    # /FFT: 2-Sekunden-Toleranz bei Zeitstempeln (FAT32), sonst wuerde jede
    # Nacht alles neu kopiert. /XJ: keine Junctions verfolgen.
    $robocopyArgs = @(
        $SourceDir, $DestinationDir,
        "/E", "/FFT", "/XJ", "/R:2", "/W:5",
        "/NP", "/NDL", "/NFL", "/NJH",
        "/LOG+:$logFilePath"
    )
    & robocopy.exe @robocopyArgs | Out-Null
    $code = $LASTEXITCODE

    # robocopy: 0-7 = Erfolg (1 = Dateien kopiert, 2/3 = Extras im Ziel), ab 8 = Fehler
    if ($code -ge 8) {
        Write-Log "$Label (uploads): robocopy mit Fehlercode $code beendet - Details oben im Log" "ERROR"
        return $false
    }
    Write-Log "$Label (uploads): synchronisiert nach $DestinationDir (robocopy-Code $code)"
    return $true
}

function Copy-ToDestination {
    param(
        [string]$Label,
        [string]$DestinationDir,
        [string[]]$Files
    )

    if (-not (Ensure-Directory $DestinationDir)) {
        Write-Log "$Label übersprungen: Zielverzeichnis nicht erreichbar" "WARN"
        return $false
    }

    try {
        foreach ($file in $Files) {
            if (Test-Path $file) {
                Copy-Item -Path $file -Destination $DestinationDir -Force
                Write-Log "$Label`: $(Split-Path $file -Leaf) kopiert"
            }
        }
        return $true
    }
    catch {
        Write-Log "Fehler beim Kopieren nach $Label ($DestinationDir): $_" "ERROR"
        return $false
    }
}

function Remove-OldBackups {
    param(
        [string]$BackupDirectory,
        [int]$Days
    )

    if (-not (Test-Path $BackupDirectory)) {
        return
    }

    try {
        $cutoffDate = (Get-Date).AddDays(-$Days)

        $oldDbBackups = Get-ChildItem -Path $BackupDirectory -Filter "kalkulationsprogramm_db_*.sql.gz" -ErrorAction SilentlyContinue |
                        Where-Object { $_.LastWriteTime -lt $cutoffDate }

        # Unkomprimierte .sql-Dumps (z. B. manuell vor Updates erstellt) lagen
        # bisher ewig herum; sie bekommen dieselbe Aufbewahrungsfrist.
        $oldPlainDbBackups = Get-ChildItem -Path $BackupDirectory -Filter "kalkulationsprogramm_db_*.sql" -ErrorAction SilentlyContinue |
                             Where-Object { $_.Extension -eq ".sql" -and $_.LastWriteTime -lt $cutoffDate }

        # Altbestand aus der Zeit der taeglichen Voll-ZIPs - es kommen keine neuen hinzu.
        $oldUploadsBackups = Get-ChildItem -Path $BackupDirectory -Filter "uploads_*.zip" -ErrorAction SilentlyContinue |
                             Where-Object { $_.LastWriteTime -lt $cutoffDate }

        $oldBackups = @(@($oldDbBackups) + @($oldPlainDbBackups) + @($oldUploadsBackups) | Where-Object { $_ })

        if ($oldBackups.Count -gt 0) {
            Write-Log "Lösche $($oldBackups.Count) alte Backup(s) älter als $Days Tage in $BackupDirectory..."
            foreach ($backup in $oldBackups) {
                Remove-Item $backup.FullName -Force
            }
        }
    }
    catch {
        Write-Log "Fehler beim Löschen alter Backups in $BackupDirectory : $_" "WARN"
    }
}

# =========================================================
# Hauptprogramm
# =========================================================

Ensure-Directory $LogDir | Out-Null

Write-Log "========================================"
Write-Log "Backup-Prozess gestartet"
Write-Log "========================================"

# Schritt 1: Lokales Staging-Verzeichnis sicherstellen (kritisch - immer verfuegbar)
if (-not (Ensure-Directory $LocalStagingDir)) {
    Write-Log "Backup abgebrochen: Konnte lokales Backup-Verzeichnis nicht erstellen!" "ERROR"
    exit 1
}

# Schritt 2: Dump-Tool finden
Write-Log "Suche Dump-Tool (mariadb-dump/mysqldump)..."
$MYSQLDUMP_PATH = Find-MySQLDump
if (-not $MYSQLDUMP_PATH) {
    Write-Log "FEHLER: Kein Dump-Tool gefunden (mariadb-dump oder mysqldump)." "ERROR"
    Write-Log "Bitte installieren Sie MariaDB/MySQL Client-Tools oder passen Sie den Pfad an." "ERROR"
    exit 1
}
Write-Log "Dump-Tool gefunden: $MYSQLDUMP_PATH"

# Schritt 3: Datenbank-Backup lokal erstellen
Write-Log "Erstelle Datenbank-Backup..."
Write-Log "Ziel: $localBackupFilePath"

try {
    $arguments = @(
        "--host=$DB_HOST",
        "--port=$DB_PORT",
        "--user=$DB_USER",
        "--password=$DB_PASSWORD",
        "--single-transaction",
        "--routines",
        "--triggers",
        "--events",
        "--result-file=$localBackupFilePath",
        $DB_NAME
    )

    $process = Start-Process -FilePath $MYSQLDUMP_PATH -ArgumentList $arguments -NoNewWindow -Wait -PassThru

    if ($process.ExitCode -ne 0) {
        Write-Log "Dump-Tool ist mit Fehlercode $($process.ExitCode) beendet!" "ERROR"
        exit 1
    }

    if (-not (Test-Path $localBackupFilePath)) {
        Write-Log "Backup-Datei wurde nicht erstellt!" "ERROR"
        exit 1
    }

    $backupSize = (Get-Item $localBackupFilePath).Length / 1MB
    Write-Log "Backup erstellt: $([math]::Round($backupSize, 2)) MB"
}
catch {
    Write-Log "Fehler beim Erstellen des Backups: $_" "ERROR"
    exit 1
}

# Schritt 4: Backup komprimieren
Write-Log "Komprimiere Backup..."
if (Compress-File $localBackupFilePath $localCompressedFilePath) {
    $compressedSize = (Get-Item $localCompressedFilePath).Length / 1MB
    Write-Log "Backup komprimiert: $([math]::Round($compressedSize, 2)) MB"
    Remove-Item $localBackupFilePath -Force
}
else {
    Write-Log "Komprimierung fehlgeschlagen, behalte unkomprimierte Datei" "WARN"
    $localCompressedFilePath = $localBackupFilePath
}

$filesToCopy = @($localCompressedFilePath)

# Schritt 5: Externe Festplatte (best effort, KEIN Abbruch bei Fehlen)
#   <Platte>:\Kalkulationsprogramm-Backup\db       -> DB-Dumps (30 Tage)
#   <Platte>:\Kalkulationsprogramm-Backup\uploads  -> inkrementeller Spiegel
$externalDbSuccess = $false
$externalUploadsSuccess = $false
Write-Log "Suche externe Festplatte (Label '$ExternalVolumeLabel', sonst ${ExternalFallbackDrive}:)..."
$externalRoot = Resolve-ExternalBackupRoot
if ($externalRoot) {
    $externalDbDir = Join-Path $externalRoot "db"
    $externalDbSuccess = Copy-ToDestination -Label "Externe Festplatte" -DestinationDir $externalDbDir -Files $filesToCopy
    if ($externalDbSuccess) {
        Remove-OldBackups -BackupDirectory $externalDbDir -Days $RetentionDays
    }
    $externalUploadsSuccess = Sync-UploadsDirectory -Label "Externe Festplatte" -SourceDir $UploadsDir -DestinationDir (Join-Path $externalRoot "uploads")
}
else {
    Write-Log "Externe Festplatte nicht angeschlossen - uebersprungen (lokales Backup bleibt gueltig)." "WARN"
}

# Schritt 6: OneDrive (best effort)
#   DB-Dumps direkt im OneDrive-Ordner (wie bisher), uploads im Unterordner "uploads"
Write-Log "Kopiere Backups auf OneDrive: $OneDriveBackupDir"
$oneDriveDbSuccess = Copy-ToDestination -Label "OneDrive" -DestinationDir $OneDriveBackupDir -Files $filesToCopy
if ($oneDriveDbSuccess) {
    Remove-OldBackups -BackupDirectory $OneDriveBackupDir -Days $RetentionDays
}
$oneDriveUploadsSuccess = Sync-UploadsDirectory -Label "OneDrive" -SourceDir $UploadsDir -DestinationDir (Join-Path $OneDriveBackupDir "uploads")

# Schritt 7: Alte lokale Backups loeschen
Write-Log "Pruefe alte lokale Backups..."
Remove-OldBackups -BackupDirectory $LocalStagingDir -Days $RetentionDays

# Schritt 8: Zusammenfassung
function Format-Status([bool]$ok, [string]$failText) { if ($ok) { 'OK' } else { $failText } }
$externalOk = $externalDbSuccess -and $externalUploadsSuccess
$oneDriveOk = $oneDriveDbSuccess -and $oneDriveUploadsSuccess
Write-Log "========================================"
Write-Log "Backup abgeschlossen!"
Write-Log "Datenbank-Backup: $(Split-Path $localCompressedFilePath -Leaf)"
Write-Log "Speicherort 1 (Lokal, nur DB):  $LocalStagingDir - OK"
Write-Log "Speicherort 2 (Extern): $(if ($externalRoot) { $externalRoot } else { '-' }) - DB $(Format-Status $externalDbSuccess 'UEBERSPRUNGEN'), uploads $(Format-Status $externalUploadsSuccess 'UEBERSPRUNGEN')" $(if ($externalOk) { "INFO" } else { "WARN" })
Write-Log "Speicherort 3 (OneDrive): $OneDriveBackupDir - DB $(Format-Status $oneDriveDbSuccess 'FEHLGESCHLAGEN'), uploads $(Format-Status $oneDriveUploadsSuccess 'FEHLGESCHLAGEN')" $(if ($oneDriveOk) { "INFO" } else { "WARN" })
Write-Log "========================================"

# Exit-Code: Nur die eigentliche DB-Sicherung ist kritisch. Fehlende externe
# Platte/OneDrive sind Warnungen, kein Fehlschlag des Gesamt-Backups, weil
# das lokale (primaere) Backup in jedem Fall vorhanden ist.
exit 0
