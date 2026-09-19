# =========================================================
# Automatisches Datenbank- und Uploads-Backup Script
# Kalkulationsprogramm
# =========================================================
# Sichert Datenbank + Uploads-Verzeichnis an DREI Orten:
#   1. Lokal (immer verfuegbar, kritischstes Backup)
#   2. Externe Festplatte E:\ (falls angeschlossen)
#   3. OneDrive (Cloud-Sync)
#
# WICHTIG: Die externe Festplatte ist ABSICHTLICH kein hartes
# Abbruchkriterium mehr. Frueher ist das komplette Backup
# (inkl. OneDrive!) fehlgeschlagen, sobald E:\ nicht verfuegbar
# war. Jetzt wird IMMER zuerst lokal gesichert; externe Platte
# und OneDrive sind zusaetzliche, unabhaengige Kopien.
# =========================================================

param(
    [string]$LocalStagingDir = "C:\Kalkulationsprogramm\backups\db",
    [string]$ExternalBackupDir = "E:\Kalkulationsprogramm\Backups",
    [string]$OneDriveBackupDir = "C:\Users\bausc\OneDrive\backup_handwerkerprogramm",
    [string]$LogDir = "C:\Kalkulationsprogramm\logs\backups",
    [int]$RetentionDays = 30,
    [string]$UploadsDir = "C:\Kalkulationsprogramm\uploads"
)

# Konfiguration
# WICHTIG: Diese Platzhalter NIEMALS mit echten Zugangsdaten committen!
# Auf dem Produktivserver wird diese Datei NACH dem Deployment lokal
# (ausserhalb von Git) mit echten Werten befuellt - siehe DEPLOYMENT_README.md.
$DB_HOST = "localhost"
$DB_PORT = "3307"
$DB_NAME = "kalkulationsprogramm_db"
$DB_USER = "YOUR_DB_USERNAME"
$DB_PASSWORD = "YOUR_DB_PASSWORD"

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
$uploadsBackupFileName = "uploads_${timestamp}.zip"
$localUploadsBackupFilePath = Join-Path $LocalStagingDir $uploadsBackupFileName
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

function Test-ExternalDrive {
    param([string]$DriveLetter)

    $drive = Get-PSDrive -Name $DriveLetter -ErrorAction SilentlyContinue
    if (-not $drive) {
        return $false
    }

    if ($drive.Free -lt 1GB) {
        Write-Log "WARNUNG: Weniger als 1GB freier Speicherplatz auf ${DriveLetter}:\" "WARN"
    }

    return $true
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

function Backup-UploadsDirectory {
    param(
        [string]$SourceDir,
        [string]$DestinationZip
    )

    try {
        if (-not (Test-Path $SourceDir)) {
            Write-Log "WARNUNG: Uploads-Verzeichnis nicht gefunden: $SourceDir" "WARN"
            return $false
        }

        $fileCount = (Get-ChildItem -Path $SourceDir -Recurse -File -ErrorAction SilentlyContinue | Measure-Object).Count
        if ($fileCount -eq 0) {
            Write-Log "WARNUNG: Uploads-Verzeichnis ist leer" "WARN"
            return $false
        }

        Write-Log "Sichere $fileCount Datei(en) aus uploads..."

        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::CreateFromDirectory($SourceDir, $DestinationZip, [System.IO.Compression.CompressionLevel]::Optimal, $false)

        if (Test-Path $DestinationZip) {
            $zipSize = (Get-Item $DestinationZip).Length / 1MB
            Write-Log "Uploads-Backup erstellt: $([math]::Round($zipSize, 2)) MB"
            return $true
        }
        else {
            Write-Log "Fehler: ZIP-Datei wurde nicht erstellt" "ERROR"
            return $false
        }
    }
    catch {
        Write-Log "Fehler beim Sichern der Uploads: $_" "ERROR"
        return $false
    }
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

        $oldUploadsBackups = Get-ChildItem -Path $BackupDirectory -Filter "uploads_*.zip" -ErrorAction SilentlyContinue |
                             Where-Object { $_.LastWriteTime -lt $cutoffDate }

        $oldBackups = @($oldDbBackups) + @($oldUploadsBackups)

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

# Schritt 5: Uploads-Verzeichnis sichern (lokal)
Write-Log "Sichere Uploads-Verzeichnis..."
Write-Log "Quelle: $UploadsDir"
$uploadsBackedUp = Backup-UploadsDirectory -SourceDir $UploadsDir -DestinationZip $localUploadsBackupFilePath

$filesToCopy = @($localCompressedFilePath)
if ($uploadsBackedUp) {
    $filesToCopy += $localUploadsBackupFilePath
}

# Schritt 6: Auf externe Festplatte E:\ kopieren (best effort, KEIN Abbruch bei Fehlen)
$externalSuccess = $false
Write-Log "Prüfe externe Festplatte E:\..."
if (Test-ExternalDrive "E") {
    $externalSuccess = Copy-ToDestination -Label "Externe Festplatte" -DestinationDir $ExternalBackupDir -Files $filesToCopy
    if ($externalSuccess) {
        Remove-OldBackups -BackupDirectory $ExternalBackupDir -Days $RetentionDays
    }
}
else {
    Write-Log "Externe Festplatte E:\ nicht angeschlossen - übersprungen (lokales Backup bleibt gültig)." "WARN"
}

# Schritt 7: Auf OneDrive kopieren (best effort)
Write-Log "Kopiere Backups auf OneDrive: $OneDriveBackupDir"
$oneDriveSuccess = Copy-ToDestination -Label "OneDrive" -DestinationDir $OneDriveBackupDir -Files $filesToCopy
if ($oneDriveSuccess) {
    Remove-OldBackups -BackupDirectory $OneDriveBackupDir -Days $RetentionDays
}

# Schritt 8: Alte lokale Backups löschen
Write-Log "Prüfe alte lokale Backups..."
Remove-OldBackups -BackupDirectory $LocalStagingDir -Days $RetentionDays

# Schritt 9: Zusammenfassung
Write-Log "========================================"
Write-Log "Backup abgeschlossen!"
Write-Log "Datenbank-Backup: $(Split-Path $localCompressedFilePath -Leaf)"
if ($uploadsBackedUp) {
    Write-Log "Uploads-Backup: $(Split-Path $localUploadsBackupFilePath -Leaf)"
}
Write-Log "Speicherort 1 (Lokal):  $LocalStagingDir - OK"
Write-Log "Speicherort 2 (Extern): $ExternalBackupDir - $(if ($externalSuccess) { 'OK' } else { 'ÜBERSPRUNGEN' })" $(if ($externalSuccess) { "INFO" } else { "WARN" })
Write-Log "Speicherort 3 (OneDrive): $OneDriveBackupDir - $(if ($oneDriveSuccess) { 'OK' } else { 'FEHLGESCHLAGEN' })" $(if ($oneDriveSuccess) { "INFO" } else { "WARN" })
Write-Log "========================================"

# Exit-Code: Nur die eigentliche DB-Sicherung ist kritisch. Fehlende externe
# Platte/OneDrive sind Warnungen, kein Fehlschlag des Gesamt-Backups, weil
# das lokale (primaere) Backup in jedem Fall vorhanden ist.
exit 0
