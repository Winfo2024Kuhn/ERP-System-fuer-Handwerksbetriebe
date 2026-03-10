# =========================================================
# Automatisches Datenbank-Backup Script
# Kalkulationsprogramm
# =========================================================
# Dieses Script erstellt automatische Backups der MySQL-Datenbank
# auf der externen Festplatte E:\ und zusätzlich auf OneDrive
# =========================================================

param(
    [string]$BackupDir = "E:\Kalkulationsprogramm\Backups",
    [string]$OneDriveBackupDir = "C:\Users\bausc\OneDrive\backup_handwerkerprogramm",
    [string]$LogDir = "E:\Kalkulationsprogramm\Logs",
    [int]$RetentionDays = 30,
    [string]$UploadsDir = "C:\Kalkulationsprogramm\uploads"
)

# Konfiguration
$DB_HOST = "192.168.x.x"
$DB_PORT = "3307"
$DB_NAME = "kalkulationsprogramm_db"
$DB_USER = "YOUR_DB_USERNAME"
$DB_PASSWORD = "YOUR_DB_PASSWORD"
$MYSQLDUMP_PATH = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe"

# Alternative Pfade für mysqldump falls nicht gefunden
$MYSQLDUMP_ALTERNATIVES = @(
    "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe",
    "C:\Program Files\MySQL\MySQL Server 8.3\bin\mysqldump.exe",
    "C:\Program Files\MySQL\MySQL Server 9.0\bin\mysqldump.exe",
    "C:\Program Files (x86)\MySQL\MySQL Server 8.0\bin\mysqldump.exe",
    "mysqldump.exe"  # Falls im PATH
)

# Zeitstempel für Backup-Datei
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupFileName = "kalkulationsprogramm_${timestamp}.sql"
$backupFilePath = Join-Path $BackupDir $backupFileName
$compressedBackupFilePath = "${backupFilePath}.gz"
$uploadsBackupFileName = "uploads_${timestamp}.zip"
$uploadsBackupFilePath = Join-Path $BackupDir $uploadsBackupFileName
$logFileName = "backup_${timestamp}.log"
$logFilePath = Join-Path $LogDir $logFileName

# =========================================================
# Funktionen
# =========================================================

function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logMessage = "[$timestamp] [$Level] $Message"
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
        if ($path -eq "mysqldump.exe") {
            # Prüfe ob mysqldump im PATH ist
            $cmd = Get-Command mysqldump -ErrorAction SilentlyContinue
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
        Write-Log "Externe Festplatte ${DriveLetter}:\ ist nicht verfügbar!" "ERROR"
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
        # Verwende .NET Compression
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
        
        # Prüfe ob Verzeichnis leer ist
        $fileCount = (Get-ChildItem -Path $SourceDir -Recurse -File -ErrorAction SilentlyContinue | Measure-Object).Count
        if ($fileCount -eq 0) {
            Write-Log "WARNUNG: Uploads-Verzeichnis ist leer" "WARN"
            return $false
        }
        
        Write-Log "Sichere $fileCount Datei(en) aus uploads..."
        
        # Erstelle ZIP-Archive mit .NET Compression
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

function Remove-OldBackups {
    param(
        [string]$BackupDirectory,
        [int]$Days
    )
    
    try {
        $cutoffDate = (Get-Date).AddDays(-$Days)
        
        # Lösche alte Datenbank-Backups
        $oldDbBackups = Get-ChildItem -Path $BackupDirectory -Filter "kalkulationsprogramm_*.sql.gz" | 
                        Where-Object { $_.LastWriteTime -lt $cutoffDate }
        
        # Lösche alte Uploads-Backups
        $oldUploadsBackups = Get-ChildItem -Path $BackupDirectory -Filter "uploads_*.zip" | 
                             Where-Object { $_.LastWriteTime -lt $cutoffDate }
        
        $oldBackups = $oldDbBackups + $oldUploadsBackups
        
        if ($oldBackups.Count -gt 0) {
            Write-Log "Lösche $($oldBackups.Count) alte Backup(s) älter als $Days Tage..."
            foreach ($backup in $oldBackups) {
                Remove-Item $backup.FullName -Force
                Write-Log "Gelöscht: $($backup.Name)"
            }
        }
        else {
            Write-Log "Keine alten Backups zum Löschen gefunden."
        }
    }
    catch {
        Write-Log "Fehler beim Löschen alter Backups: $_" "ERROR"
    }
}

# =========================================================
# Hauptprogramm
# =========================================================

Write-Log "========================================" 
Write-Log "Backup-Prozess gestartet"
Write-Log "========================================"

# Schritt 1: Externe Festplatte prüfen
Write-Log "Prüfe externe Festplatte E:\..."
if (-not (Test-ExternalDrive "E")) {
    Write-Log "Backup abgebrochen: Externe Festplatte nicht verfügbar!" "ERROR"
    exit 1
}

# Schritt 2: Verzeichnisse erstellen
Write-Log "Erstelle Verzeichnisse falls notwendig..."
if (-not (Ensure-Directory $BackupDir)) {
    Write-Log "Backup abgebrochen: Konnte Backup-Verzeichnis nicht erstellen!" "ERROR"
    exit 1
}

if (-not (Ensure-Directory $LogDir)) {
    Write-Log "WARNUNG: Konnte Log-Verzeichnis nicht erstellen!" "WARN"
}

# Schritt 3: mysqldump finden
Write-Log "Suche mysqldump..."
$MYSQLDUMP_PATH = Find-MySQLDump
if (-not $MYSQLDUMP_PATH) {
    Write-Log "FEHLER: mysqldump.exe nicht gefunden!" "ERROR"
    Write-Log "Bitte installieren Sie MySQL Client oder passen Sie den Pfad an." "ERROR"
    exit 1
}
Write-Log "mysqldump gefunden: $MYSQLDUMP_PATH"

# Schritt 4: Datenbank-Backup erstellen
Write-Log "Erstelle Datenbank-Backup..."
Write-Log "Ziel: $backupFilePath"

try {
    # mysqldump Befehl ausführen
    $arguments = @(
        "--host=$DB_HOST",
        "--port=$DB_PORT",
        "--user=$DB_USER",
        "--password=$DB_PASSWORD",
        "--single-transaction",
        "--routines",
        "--triggers",
        "--events",
        "--result-file=$backupFilePath",
        $DB_NAME
    )
    
    $process = Start-Process -FilePath $MYSQLDUMP_PATH -ArgumentList $arguments -NoNewWindow -Wait -PassThru
    
    if ($process.ExitCode -ne 0) {
        Write-Log "mysqldump ist mit Fehlercode $($process.ExitCode) beendet!" "ERROR"
        exit 1
    }
    
    # Prüfe ob Backup-Datei erstellt wurde
    if (-not (Test-Path $backupFilePath)) {
        Write-Log "Backup-Datei wurde nicht erstellt!" "ERROR"
        exit 1
    }
    
    $backupSize = (Get-Item $backupFilePath).Length / 1MB
    Write-Log "Backup erstellt: $([math]::Round($backupSize, 2)) MB"
}
catch {
    Write-Log "Fehler beim Erstellen des Backups: $_" "ERROR"
    exit 1
}

# Schritt 5: Backup komprimieren
Write-Log "Komprimiere Backup..."
if (Compress-File $backupFilePath $compressedBackupFilePath) {
    $compressedSize = (Get-Item $compressedBackupFilePath).Length / 1MB
    Write-Log "Backup komprimiert: $([math]::Round($compressedSize, 2)) MB"
    
    # Lösche unkomprimierte Datei
    Remove-Item $backupFilePath -Force
    Write-Log "Unkomprimierte Datei entfernt"
}
else {
    Write-Log "Komprimierung fehlgeschlagen, behalte unkomprimierte Datei" "WARN"
}

# Schritt 6: Uploads-Verzeichnis sichern
Write-Log "Sichere Uploads-Verzeichnis..."
Write-Log "Quelle: $UploadsDir"
Write-Log "Ziel: $uploadsBackupFilePath"

$uploadsBackedUp = Backup-UploadsDirectory -SourceDir $UploadsDir -DestinationZip $uploadsBackupFilePath

# Schritt 7: Backup auf OneDrive kopieren
$oneDriveCopySuccess = $false
Write-Log "Kopiere Backups auf OneDrive: $OneDriveBackupDir"
if (Ensure-Directory $OneDriveBackupDir) {
    try {
        # Datenbank-Backup kopieren
        $dbSourceFile = if (Test-Path $compressedBackupFilePath) { $compressedBackupFilePath } else { $backupFilePath }
        if (Test-Path $dbSourceFile) {
            Copy-Item -Path $dbSourceFile -Destination $OneDriveBackupDir -Force
            Write-Log "Datenbank-Backup auf OneDrive kopiert: $(Split-Path $dbSourceFile -Leaf)"
        }

        # Uploads-Backup kopieren
        if ($uploadsBackedUp -and (Test-Path $uploadsBackupFilePath)) {
            Copy-Item -Path $uploadsBackupFilePath -Destination $OneDriveBackupDir -Force
            Write-Log "Uploads-Backup auf OneDrive kopiert: $(Split-Path $uploadsBackupFilePath -Leaf)"
        }

        $oneDriveCopySuccess = $true
        Write-Log "OneDrive-Backup erfolgreich abgeschlossen."
    }
    catch {
        Write-Log "Fehler beim Kopieren auf OneDrive: $_" "ERROR"
    }

    # Alte Backups auf OneDrive aufräumen
    Remove-OldBackups -BackupDirectory $OneDriveBackupDir -Days $RetentionDays
}
else {
    Write-Log "WARNUNG: OneDrive-Verzeichnis konnte nicht erstellt werden. Überspringe OneDrive-Backup." "WARN"
}

# Schritt 8: Alte Backups löschen
Write-Log "Prüfe alte Backups..."
Remove-OldBackups -BackupDirectory $BackupDir -Days $RetentionDays

# Schritt 9: Zusammenfassung
Write-Log "========================================"
Write-Log "Backup erfolgreich abgeschlossen!"
Write-Log "Datenbank-Backup: $(Split-Path $compressedBackupFilePath -Leaf)"
if ($uploadsBackedUp) {
    Write-Log "Uploads-Backup: $(Split-Path $uploadsBackupFilePath -Leaf)"
}
Write-Log "Speicherort 1 (Extern): $BackupDir"
if ($oneDriveCopySuccess) {
    Write-Log "Speicherort 2 (OneDrive): $OneDriveBackupDir"
} else {
    Write-Log "WARNUNG: OneDrive-Kopie fehlgeschlagen!" "WARN"
}
Write-Log "========================================"

exit 0
