# =========================================================
# Server Start Script
# Kalkulationsprogramm
# =========================================================
# Startet den Kalkulationsprogramm-Server automatisch
# Prüft ob Server bereits läuft und führt Health-Check durch
# =========================================================

param(
    [string]$JarPath = "C:\Kalkulationsprogramm\Kalkulationsprogramm.jar",
    [int]$ServerPort = 8080,
    [string]$LogDir = "C:\Kalkulationsprogramm\Logs",
    [int]$HealthCheckTimeout = 120  # Sekunden
)

# Zeitstempel für Log
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logFileName = "server_start_${timestamp}.log"
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
        try {
            Add-Content -Path $logFilePath -Value $logMessage -ErrorAction SilentlyContinue
        } catch {
            # Ignoriere Logging-Fehler
        }
    }
}

function Ensure-Directory {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        try {
            New-Item -ItemType Directory -Path $Path -Force | Out-Null
            return $true
        }
        catch {
            return $false
        }
    }
    return $true
}

function Test-ServerRunning {
    param([int]$Port)
    
    try {
        $connection = Test-NetConnection -ComputerName localhost -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue
        return $connection
    }
    catch {
        return $false
    }
}

function Wait-ForServerReady {
    param(
        [int]$Port,
        [int]$TimeoutSeconds = 120
    )
    
    Write-Log "Warte bis Server bereit ist (max. $TimeoutSeconds Sekunden)..."
    $elapsed = 0
    $interval = 5
    
    while ($elapsed -lt $TimeoutSeconds) {
        if (Test-ServerRunning -Port $Port) {
            Write-Log "Server ist bereit und antwortet auf Port $Port"
            return $true
        }
        
        Start-Sleep -Seconds $interval
        $elapsed += $interval
        
        if ($elapsed % 15 -eq 0) {
            Write-Log "Warte bereits $elapsed Sekunden..."
        }
    }
    
    Write-Log "Timeout: Server antwortet nicht innerhalb von $TimeoutSeconds Sekunden" "WARN"
    return $false
}

function Find-JavaExecutable {
    # Prüfe ob java im PATH ist
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) {
        return $javaCmd.Source
    }
    
    # Suche in üblichen Java Installationspfaden
    $javaPaths = @(
        "$env:JAVA_HOME\bin\java.exe",
        "C:\Program Files\Java\*\bin\java.exe",
        "C:\Program Files (x86)\Java\*\bin\java.exe"
    )
    
    foreach ($path in $javaPaths) {
        $resolved = Resolve-Path $path -ErrorAction SilentlyContinue
        if ($resolved) {
            return $resolved.Path | Select-Object -First 1
        }
    }
    
    return $null
}

# =========================================================
# Hauptprogramm
# =========================================================

Write-Log "========================================"
Write-Log "Kalkulationsprogramm Server Start"
Write-Log "========================================"

# Log-Verzeichnis erstellen
Ensure-Directory $LogDir | Out-Null

# Schritt 1: Prüfe ob Server bereits läuft
Write-Log "Prüfe ob Server bereits läuft (Port $ServerPort)..."
if (Test-ServerRunning -Port $ServerPort) {
    Write-Log "Server läuft bereits auf Port $ServerPort" "INFO"
    Write-Log "Kein Neustart erforderlich."
    exit 0
}

Write-Log "Server läuft nicht, starte Server..."

# Schritt 2: Prüfe ob JAR-Datei existiert
if (-not (Test-Path $JarPath)) {
    Write-Log "FEHLER: JAR-Datei nicht gefunden: $JarPath" "ERROR"
    exit 1
}
Write-Log "JAR-Datei gefunden: $JarPath"

# Schritt 3: Finde Java
Write-Log "Suche Java-Installation..."
$javaExe = Find-JavaExecutable
if (-not $javaExe) {
    Write-Log "FEHLER: Java nicht gefunden! Bitte installieren Sie Java 17 oder höher." "ERROR"
    exit 1
}
Write-Log "Java gefunden: $javaExe"

# Java Version prüfen
try {
    $javaVersion = & $javaExe -version 2>&1 | Select-Object -First 1
    Write-Log "Java Version: $javaVersion"
} catch {
    Write-Log "Warnung: Konnte Java-Version nicht ermitteln" "WARN"
}

# Schritt 4: Server starten
Write-Log "Starte Server..."
Write-Log "Befehl: java -jar $JarPath"

try {
    # Erstelle separaten PowerShell-Prozess für den Server
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $javaExe
    $startInfo.Arguments = "-jar `"$JarPath`""
    $startInfo.WorkingDirectory = Split-Path $JarPath -Parent
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $false  # Server-Fenster anzeigen
    $startInfo.RedirectStandardOutput = $false
    $startInfo.RedirectStandardError = $false
    
    $process = [System.Diagnostics.Process]::Start($startInfo)
    
    if ($process) {
        Write-Log "Server-Prozess gestartet (PID: $($process.Id))"
    } else {
        Write-Log "Fehler beim Starten des Server-Prozesses" "ERROR"
        exit 1
    }
}
catch {
    Write-Log "FEHLER beim Starten des Servers: $_" "ERROR"
    exit 1
}

# Schritt 5: Health Check
if (Wait-ForServerReady -Port $ServerPort -TimeoutSeconds $HealthCheckTimeout) {
    Write-Log "========================================"
    Write-Log "Server erfolgreich gestartet!"
    Write-Log "Server läuft auf Port $ServerPort"
    Write-Log "URL: http://localhost:$ServerPort"
    Write-Log "========================================"
    exit 0
}
else {
    Write-Log "========================================"
    Write-Log "Server wurde gestartet, aber Health-Check fehlgeschlagen" "WARN"
    Write-Log "Bitte prüfen Sie die Server-Logs manuell"
    Write-Log "========================================"
    exit 2
}
