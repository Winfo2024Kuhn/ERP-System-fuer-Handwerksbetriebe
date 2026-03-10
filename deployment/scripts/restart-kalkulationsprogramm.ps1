# =========================================================
# Server Restart Script
# Kalkulationsprogramm
# =========================================================
# Stoppt den laufenden Server sauber und startet ihn neu
# =========================================================

param(
    [int]$ServerPort = 8082,
    [int]$ShutdownTimeout = 30,
    [string]$LogDir = "E:\Kalkulationsprogramm\Logs"
)

# Zeitstempel für Log
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logFileName = "server_restart_${timestamp}.log"
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
        } catch {}
    }
}

function Ensure-Directory {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        try {
            New-Item -ItemType Directory -Path $Path -Force | Out-Null
        } catch {}
    }
}

function Stop-KalkulationsprogrammServer {
    param([int]$Port, [int]$TimeoutSeconds)
    
    Write-Log "Suche Server-Prozess auf Port $Port..."
    
    # Finde Prozess, der auf dem Port lauscht
    try {
        $connection = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($connection) {
            $processId = $connection.OwningProcess
            $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
            
            if ($process) {
                Write-Log "Server-Prozess gefunden: $($process.Name) (PID: $processId)"
                Write-Log "Stoppe Server..."
                
                # Versuche graceful shutdown
                $process.CloseMainWindow() | Out-Null
                
                # Warte auf Beendigung
                $waited = 0
                while (-not $process.HasExited -and $waited -lt $TimeoutSeconds) {
                    Start-Sleep -Seconds 1
                    $waited++
                    
                    if ($waited % 5 -eq 0) {
                        Write-Log "Warte auf Server-Shutdown... ($waited Sekunden)"
                    }
                }
                
                if ($process.HasExited) {
                    Write-Log "Server erfolgreich gestoppt"
                    return $true
                }
                else {
                    Write-Log "Server antwortet nicht, erzwinge Beendigung..." "WARN"
                    Stop-Process -Id $processId -Force
                    Start-Sleep -Seconds 2
                    Write-Log "Server-Prozess beendet"
                    return $true
                }
            }
        }
        else {
            Write-Log "Kein laufender Server auf Port $Port gefunden"
            return $false
        }
    }
    catch {
        Write-Log "Fehler beim Stoppen des Servers: $_" "ERROR"
        return $false
    }
}

# =========================================================
# Hauptprogramm
# =========================================================

Write-Log "========================================"
Write-Log "Kalkulationsprogramm Server Neustart"
Write-Log "========================================"

# Log-Verzeichnis erstellen
Ensure-Directory $LogDir

# Schritt 1: Server stoppen
if (Stop-KalkulationsprogrammServer -Port $ServerPort -TimeoutSeconds $ShutdownTimeout) {
    Write-Log "Warte 5 Sekunden vor Neustart..."
    Start-Sleep -Seconds 5
}

# Schritt 2: Server neu starten
Write-Log "Starte Server neu..."

$startScriptPath = Join-Path (Split-Path $PSScriptRoot -Parent) "scripts\start-kalkulationsprogramm.ps1"
if (-not (Test-Path $startScriptPath)) {
    # Fallback: Script im gleichen Verzeichnis
    $startScriptPath = Join-Path $PSScriptRoot "start-kalkulationsprogramm.ps1"
}

if (Test-Path $startScriptPath) {
    Write-Log "Rufe Start-Script auf: $startScriptPath"
    & $startScriptPath
    exit $LASTEXITCODE
}
else {
    Write-Log "FEHLER: Start-Script nicht gefunden: $startScriptPath" "ERROR"
    Write-Log "Bitte starten Sie den Server manuell" "ERROR"
    exit 1
}
