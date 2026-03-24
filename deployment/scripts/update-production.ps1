# ============================================================================
# Software Update Script fuer Kalkulationsprogramm
# ============================================================================
# Dieses Skript aktualisiert das Kalkulationsprogramm in C:\Kalkulationsprogramm
# mit dem neuesten Stand vom main branch
# ============================================================================

param(
    [switch]$SkipBuild,
    [switch]$SkipBackup,
    [switch]$Force
)

# Pruefe auf Admin-Rechte und starte neu als Admin falls noetig
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "Dieses Skript benoetigt Administrator-Rechte zum Beenden/Starten von Prozessen." -ForegroundColor Yellow
    Write-Host "Starte neu als Administrator..." -ForegroundColor Cyan
    
    $arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`""
    if ($SkipBuild) { $arguments += " -SkipBuild" }
    if ($SkipBackup) { $arguments += " -SkipBackup" }
    if ($Force) { $arguments += " -Force" }
    
    Start-Process powershell.exe -Verb RunAs -ArgumentList $arguments
    exit
}

# Konfiguration
$REPO_PATH = "C:\Github\ERP-System-fuer-Handwerksbetriebe"
$PRODUCTION_PATH = "C:\Kalkulationsprogramm"
$BACKUP_DIR = "$PRODUCTION_PATH\backups"

# Farben fuer Ausgabe
function Write-Success { param($msg) Write-Host "[OK] $msg" -ForegroundColor Green }
function Write-Info { param($msg) Write-Host "--> $msg" -ForegroundColor Cyan }
function Write-Error-Custom { param($msg) Write-Host "[ERROR] $msg" -ForegroundColor Red }
function Write-Warning-Custom { param($msg) Write-Host "[WARNUNG] $msg" -ForegroundColor Yellow }

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "    SOFTWARE UPDATE - Kalkulationsprogramm" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# Pruefe ob Pfade existieren
if (-not (Test-Path $REPO_PATH)) {
    Write-Error-Custom "Repository-Pfad nicht gefunden: $REPO_PATH"
    exit 1
}

if (-not (Test-Path $PRODUCTION_PATH)) {
    Write-Error-Custom "Produktions-Pfad nicht gefunden: $PRODUCTION_PATH"
    exit 1
}

# Wechsle zum Repository
Write-Info "Wechsle zum Repository: $REPO_PATH"
Set-Location $REPO_PATH

# Pruefe Git Status
Write-Info "Pruefe Git Status..."
$gitStatus = git status --porcelain
if ($gitStatus -and -not $Force) {
    Write-Warning-Custom "Es gibt uncommitted changes im Repository!"
    Write-Host $gitStatus
    $continue = Read-Host "Trotzdem fortfahren? (j/n)"
    if ($continue -ne "j" -and $continue -ne "J") {
        Write-Info "Update abgebrochen."
        exit 0
    }
}

# Pruefe aktuellen Branch
$currentBranch = git rev-parse --abbrev-ref HEAD
if ($currentBranch -ne "main") {
    Write-Warning-Custom "Du bist nicht auf dem main branch (aktuell: $currentBranch)"
    if (-not $Force) {
        $continue = Read-Host "Zum main branch wechseln? (j/n)"
        if ($continue -eq "j" -or $continue -eq "J") {
            Write-Info "Wechsle zu main branch..."
            git checkout main
            if ($LASTEXITCODE -ne 0) {
                Write-Error-Custom "Fehler beim Wechseln zum main branch"
                exit 1
            }
            Write-Success "Branch gewechselt zu main"
        } else {
            Write-Info "Update abgebrochen."
            exit 0
        }
    }
}

# Hole neueste Aenderungen
Write-Info "Hole neueste Aenderungen vom main branch..."
$oldCommit = git rev-parse HEAD
git pull origin main
if ($LASTEXITCODE -ne 0) {
    Write-Error-Custom "Fehler beim Pullen vom main branch"
    exit 1
}
$newCommit = git rev-parse HEAD

if ($oldCommit -eq $newCommit) {
    Write-Success "Repository ist bereits auf dem neuesten Stand (Commit: $($newCommit.Substring(0,7)))"
} else {
    Write-Success "Repository aktualisiert von $($oldCommit.Substring(0,7)) zu $($newCommit.Substring(0,7))"
    Write-Info "Aenderungen:"
    git log --oneline $oldCommit..$newCommit
}

# Maven Build
if (-not $SkipBuild) {
    Write-Host ""
    Write-Info "Starte Maven Build (kann einige Minuten dauern)..."
    
    # Pruefe ob Maven Wrapper verfuegbar ist (bevorzugt)
    $mvnwPath = Join-Path $REPO_PATH "mvnw.cmd"
    $mavenCmd = $null
    
    if (Test-Path $mvnwPath) {
        Write-Info "Verwende Maven Wrapper (mvnw.cmd)"
        $mavenCmd = $mvnwPath
    } else {
        # Fallback auf system Maven
        $mavenSystemCmd = Get-Command mvn -ErrorAction SilentlyContinue
        if ($mavenSystemCmd) {
            Write-Info "Verwende System Maven (mvn)"
            $mavenCmd = "mvn"
        } else {
            Write-Error-Custom "Weder Maven Wrapper (mvnw.cmd) noch System Maven (mvn) gefunden."
            Write-Info "Bitte Maven installieren oder sicherstellen, dass mvnw.cmd im Repository vorhanden ist."
            exit 1
        }
    }
    
    # Fuehre Maven Build aus
    & $mavenCmd clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Error-Custom "Maven Build fehlgeschlagen"
        exit 1
    }
    
    Write-Success "Maven Build erfolgreich abgeschlossen"
} else {
    Write-Warning-Custom "Build wird uebersprungen (-SkipBuild)"
}

# Finde die gebaute JAR Datei dynamisch im target-Verzeichnis
$targetDir = Join-Path $REPO_PATH "target"
$jarFile = Get-ChildItem $targetDir -Filter "*.jar" -ErrorAction SilentlyContinue | 
           Where-Object { $_.Name -notmatch '(-sources|-javadoc|\.original)' } | 
           Sort-Object LastWriteTime -Descending | 
           Select-Object -First 1

if (-not $jarFile) {
    Write-Error-Custom "Keine JAR-Datei im target-Verzeichnis gefunden: $targetDir"
    Write-Info "Bitte pruefe ob der Build erfolgreich war und die JAR erstellt wurde."
    exit 1
}

$jarPath = $jarFile.FullName
$JAR_NAME = $jarFile.Name

# Zeige JAR Info
$jarInfo = Get-Item $jarPath
Write-Success "JAR-Datei gefunden: $JAR_NAME"
Write-Info "  Groesse: $([math]::Round($jarInfo.Length / 1MB, 2)) MB"
Write-Info "  Erstellt: $($jarInfo.LastWriteTime)"

# Stoppe laufende Anwendung BEVOR die JAR kopiert wird
Write-Host ""
Write-Info "Pruefe ob Anwendung auf Port 8080 laeuft..."
$tcpConnection = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Where-Object { $_.State -eq 'Listen' }
$wasRunning = $false

if ($tcpConnection) {
    $processId = $tcpConnection.OwningProcess
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    
    if ($process) {
        $wasRunning = $true
        Write-Info "Laufender Prozess gefunden: $($process.Name) (PID: $processId)"
        Write-Info "Beende Anwendung..."
        
        Stop-Process -Id $processId -Force
        Start-Sleep -Seconds 3
        
        Write-Success "Anwendung wurde beendet"
    }
} else {
    Write-Info "Keine laufende Anwendung auf Port 8080 gefunden"
}

# Backup der alten JAR (falls vorhanden)
# Finde existierende JAR-Dateien im Produktionsverzeichnis
$existingJars = Get-ChildItem $PRODUCTION_PATH -Filter "*.jar" -ErrorAction SilentlyContinue
if ($existingJars -and -not $SkipBackup) {
    Write-Host ""
    Write-Info "Erstelle Backup der alten JAR-Datei..."
    
    # Erstelle Backup-Verzeichnis falls nicht vorhanden
    if (-not (Test-Path $BACKUP_DIR)) {
        New-Item -ItemType Directory -Path $BACKUP_DIR | Out-Null
    }
    
    # Backup mit Timestamp
    $timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
    foreach ($existingJar in $existingJars) {
        $backupPath = Join-Path $BACKUP_DIR "$($existingJar.Name).$timestamp.bak"
        Copy-Item $existingJar.FullName $backupPath
        Write-Success "Backup erstellt: $backupPath"
    }
    
    # Loesche alte Backups (behalte nur die letzten 5)
    $oldBackups = Get-ChildItem $BACKUP_DIR -Filter "*.bak" | 
                  Sort-Object LastWriteTime -Descending | 
                  Select-Object -Skip 5
    if ($oldBackups) {
        Write-Info "Loesche alte Backups..."
        $oldBackups | Remove-Item
    }
} elseif ($SkipBackup) {
    Write-Warning-Custom "Backup wird uebersprungen (-SkipBackup)"
}

# Entferne alte JAR-Dateien und kopiere neue JAR
Write-Host ""
Write-Info "Kopiere neue JAR nach $PRODUCTION_PATH..."

# Loesche alte JAR-Dateien (falls Name sich geaendert hat durch Versionsupdate)
if ($existingJars) {
    foreach ($oldJar in $existingJars) {
        if ($oldJar.Name -ne $JAR_NAME) {
            Remove-Item $oldJar.FullName -Force
            Write-Info "Alte JAR entfernt: $($oldJar.Name)"
        }
    }
}

Copy-Item $jarPath $PRODUCTION_PATH -Force
if ($LASTEXITCODE -eq 0 -or $?) {
    Write-Success "JAR-Datei erfolgreich kopiert"
} else {
    Write-Error-Custom "Fehler beim Kopieren der JAR-Datei"
    exit 1
}

# Kopiere Konfigurations-Dateien
Write-Host ""
Write-Info "Kopiere Konfigurations-Dateien..."
$configSource = Join-Path $REPO_PATH "config"
$configDest = Join-Path $PRODUCTION_PATH "config"

if (Test-Path $configSource) {
    # Erstelle config-Verzeichnis falls nicht vorhanden
    if (-not (Test-Path $configDest)) {
        New-Item -ItemType Directory -Path $configDest -Force | Out-Null
    }
    
    # Kopiere logback-spring.xml (wird immer aktualisiert)
    $logbackSource = Join-Path $configSource "logback-spring.xml"
    if (Test-Path $logbackSource) {
        Copy-Item $logbackSource $configDest -Force
        Write-Success "logback-spring.xml kopiert"
    }
    
    # application.properties: Nur kopieren wenn noch keine vorhanden ist
    $appPropsSource = Join-Path $REPO_PATH "deployment\config\application.properties"
    $appPropsDest = Join-Path $configDest "application.properties"
    if (-not (Test-Path $appPropsDest)) {
        if (Test-Path $appPropsSource) {
            Copy-Item $appPropsSource $appPropsDest -Force
            Write-Warning-Custom "application.properties wurde neu erstellt - bitte Werte pruefen!"
        } else {
            Write-Warning-Custom "Keine application.properties Vorlage gefunden in: $appPropsSource"
        }
    } else {
        Write-Success "application.properties existiert bereits (wird nicht ueberschrieben)"
    }
} else {
    Write-Warning-Custom "Config Quellverzeichnis nicht gefunden: $configSource"
}

# Kopiere OpenFile Launcher Setup-Dateien
Write-Host ""
Write-Info "Kopiere OpenFile Launcher Setup-Dateien..."
$openFileLauncherSource = Join-Path $REPO_PATH "deployment\openfile-launcher"
$openFileLauncherDest = Join-Path $PRODUCTION_PATH "downloads"

if (Test-Path $openFileLauncherSource) {
    # Erstelle downloads-Verzeichnis falls nicht vorhanden
    if (-not (Test-Path $openFileLauncherDest)) {
        New-Item -ItemType Directory -Path $openFileLauncherDest -Force | Out-Null
    }
    
    # Kopiere Setup-Dateien
    Copy-Item "$openFileLauncherSource\OpenFileLauncher-Setup.ps1" $openFileLauncherDest -Force
    Copy-Item "$openFileLauncherSource\OpenFileLauncher-Install.bat" $openFileLauncherDest -Force
    
    Write-Success "OpenFile Launcher Setup-Dateien kopiert nach: $openFileLauncherDest"
} else {
    Write-Warning-Custom "OpenFile Launcher Quellverzeichnis nicht gefunden: $openFileLauncherSource"
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "    [OK] UPDATE ERFOLGREICH ABGESCHLOSSEN" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Info "Die neue Version wurde erfolgreich installiert."
Write-Info "Produktions-Pfad: $PRODUCTION_PATH"
Write-Host ""

# Automatischer Neustart wenn die App vorher lief
if ($wasRunning) {
    Write-Info "Starte Anwendung automatisch neu..."
    
    $jarPath = Join-Path $PRODUCTION_PATH $JAR_NAME
    
    # Finde Java executable
    $javaExe = $null
    
    # 1. Versuche JAVA_HOME
    if ($env:JAVA_HOME) {
        $javaExe = Join-Path $env:JAVA_HOME "bin\javaw.exe"
        if (-not (Test-Path $javaExe)) {
            $javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
        }
    }
    
    # 2. Versuche PATH
    if (-not $javaExe -or -not (Test-Path $javaExe)) {
        $javaCmd = Get-Command javaw -ErrorAction SilentlyContinue
        if ($javaCmd) {
            $javaExe = $javaCmd.Source
        } else {
            $javaCmd = Get-Command java -ErrorAction SilentlyContinue
            if ($javaCmd) {
                $javaExe = $javaCmd.Source
            }
        }
    }
    
    # 3. Suche in haeufigen Installationsorten
    if (-not $javaExe -or -not (Test-Path $javaExe)) {
        $commonPaths = @(
            "C:\Program Files\Java",
            "C:\Program Files (x86)\Java",
            "C:\Program Files\Eclipse Adoptium",
            "C:\Program Files\Microsoft"
        )
        
        foreach ($basePath in $commonPaths) {
            if (Test-Path $basePath) {
                $jdkDirs = Get-ChildItem $basePath -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match "jdk|jre" }
                foreach ($jdk in $jdkDirs) {
                    $testPath = Join-Path $jdk.FullName "bin\javaw.exe"
                    if (Test-Path $testPath) {
                        $javaExe = $testPath
                        break
                    }
                    $testPath = Join-Path $jdk.FullName "bin\java.exe"
                    if (Test-Path $testPath) {
                        $javaExe = $testPath
                        break
                    }
                }
                if ($javaExe -and (Test-Path $javaExe)) { break }
            }
        }
    }
    
    # 4. Erstelle Startup-Script als Fallback
    if (-not $javaExe -or -not (Test-Path $javaExe)) {
        Write-Warning-Custom "Java executable nicht gefunden. Erstelle Startup-Script..."
        
        $startScript = Join-Path $PRODUCTION_PATH "start-app.bat"
        $scriptContent = @"
@echo off
cd /d "$PRODUCTION_PATH"
start javaw -jar "$JAR_NAME"
"@
        Set-Content -Path $startScript -Value $scriptContent -Force
        
        Start-Process cmd.exe -ArgumentList "/c `"$startScript`"" -WindowStyle Hidden
        
        Write-Success "Startup-Script erstellt und ausgefuehrt: $startScript"
    } else {
        Write-Info "Verwende Java: $javaExe"
        
        # Starte im Hintergrund
        $processArgs = @{
            FilePath = $javaExe
            ArgumentList = "-jar `"$jarPath`""
            WindowStyle = "Hidden"
            WorkingDirectory = $PRODUCTION_PATH
        }
        
        Start-Process @processArgs
        Write-Success "Anwendung wurde neu gestartet!"
    }
    
    Start-Sleep -Seconds 2
    Write-Info "Die Anwendung sollte in wenigen Sekunden auf Port 8080 verfuegbar sein."
    Write-Host ""
} else {
    Write-Warning-Custom "Die Anwendung wurde nicht automatisch gestartet."
    Write-Info "Moechtest du die Anwendung jetzt starten? (j/n)"
    $startNow = Read-Host
    
    if ($startNow -eq "j" -or $startNow -eq "J") {
        $jarPath = Join-Path $PRODUCTION_PATH $JAR_NAME
        
        # Erstelle einfaches Startup-Script
        $startScript = Join-Path $PRODUCTION_PATH "start-app.bat"
        $scriptContent = @"
@echo off
cd /d "$PRODUCTION_PATH"
start javaw -jar "$JAR_NAME"
"@
        Set-Content -Path $startScript -Value $scriptContent -Force
        Start-Process cmd.exe -ArgumentList "/c `"$startScript`"" -WindowStyle Hidden
        
        Write-Success "Anwendung wurde gestartet!"
        Write-Info "Die Anwendung sollte in wenigen Sekunden auf Port 8080 verfuegbar sein."
    }
    Write-Host ""
}
