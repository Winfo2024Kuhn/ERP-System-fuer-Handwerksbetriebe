# =========================================================
# ERP-Handwerk - Lokale Installation (Ein-Klick-Einrichtung)
# =========================================================
# Dieses Script installiert das ERP-Handwerk auf dem lokalen PC:
#   - Kopiert die Anwendung nach C:\ERP-Handwerk\
#   - Erstellt einen Desktop-Shortcut (Server starten + Browser öffnen)
#   - Registriert Autostart nach Windows-Neustart
#   - Verwendet eingebettete H2-Datenbank (kein MySQL nötig)
#
# Voraussetzungen: Java 23+ muss installiert sein
# =========================================================

param(
    [string]$InstallDir = "C:\ERP-Handwerk",
    [int]$ServerPort = 8080
)

# =========================================================
# Konfiguration
# =========================================================
$AppName          = "ERP-Handwerk"
$JarName          = "Kalkulationsprogramm-1.0.0.jar"
$TaskName         = "ERP-Handwerk - Autostart"
$DesktopShortcut  = Join-Path ([Environment]::GetFolderPath("Desktop")) "$AppName.lnk"
$StartMenuFolder  = Join-Path ([Environment]::GetFolderPath("StartMenu")) "Programs\$AppName"

# Finde die JAR-Datei relativ zum Script-Verzeichnis (Repository)
$RepoRoot = (Get-Item $PSScriptRoot).Parent.Parent.FullName
$JarSource = Join-Path $RepoRoot "target\$JarName"

# =========================================================
# Hilfsfunktionen
# =========================================================

function Write-Step {
    param([string]$Step, [string]$Message)
    Write-Host ""
    Write-Host "  [$Step] $Message" -ForegroundColor Yellow
}

function Write-OK {
    param([string]$Message)
    Write-Host "        OK $Message" -ForegroundColor Green
}

function Write-Err {
    param([string]$Message)
    Write-Host "        FEHLER: $Message" -ForegroundColor Red
}

function Write-Info {
    param([string]$Message)
    Write-Host "        $Message" -ForegroundColor Gray
}

# =========================================================
# Banner
# =========================================================
Write-Host ""
Write-Host "  ============================================" -ForegroundColor Cyan
Write-Host "    ERP-Handwerk - Lokale Einrichtung" -ForegroundColor Cyan
Write-Host "  ============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Dieses Script richtet ERP-Handwerk auf Ihrem" -ForegroundColor White
Write-Host "  Computer ein. Nach der Einrichtung:" -ForegroundColor White
Write-Host "    - Desktop-Symbol zum Starten" -ForegroundColor White
Write-Host "    - Automatischer Start nach PC-Neustart" -ForegroundColor White
Write-Host ""

# =========================================================
# Schritt 1: Java prüfen
# =========================================================
Write-Step "1/6" "Pruefe Java-Installation..."

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    # Suche in üblichen Installationspfaden
    $searchPaths = @(
        "$env:JAVA_HOME\bin\java.exe",
        "C:\Program Files\Java\*\bin\java.exe",
        "C:\Program Files\Eclipse Adoptium\*\bin\java.exe",
        "C:\Program Files\Microsoft\*\bin\java.exe",
        "C:\Program Files\Amazon Corretto\*\bin\java.exe"
    )
    foreach ($path in $searchPaths) {
        $resolved = Resolve-Path $path -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($resolved) {
            $javaExe = $resolved.Path
            break
        }
    }

    if (-not $javaExe) {
        Write-Err "Java wurde nicht gefunden!"
        Write-Host ""
        Write-Host "  Bitte installieren Sie Java 23 oder neuer:" -ForegroundColor Yellow
        Write-Host "  https://adoptium.net/de/temurin/releases/" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "  Starten Sie danach dieses Script erneut." -ForegroundColor Yellow
        Write-Host ""
        Read-Host "  Druecken Sie Enter zum Beenden"
        exit 1
    }
} else {
    $javaExe = $javaCmd.Source
}

try {
    $javaVersionOutput = & $javaExe -version 2>&1 | Select-Object -First 1
    Write-OK "Java gefunden: $javaVersionOutput"
} catch {
    Write-OK "Java gefunden: $javaExe"
}

# =========================================================
# Schritt 2: JAR-Datei finden
# =========================================================
Write-Step "2/6" "Suche Anwendungs-Datei..."

if (-not (Test-Path $JarSource)) {
    # Versuche Build auszuführen
    Write-Info "JAR nicht gefunden, versuche zu bauen..."
    $mvnw = Join-Path $RepoRoot "mvnw.cmd"
    if (Test-Path $mvnw) {
        Write-Info "Baue Anwendung (dies kann einige Minuten dauern)..."
        Push-Location $RepoRoot
        & cmd /c "$mvnw clean package -DskipTests -q" 2>&1 | Out-Null
        Pop-Location
    }
}

if (-not (Test-Path $JarSource)) {
    Write-Err "Anwendungs-Datei nicht gefunden: $JarSource"
    Write-Host ""
    Write-Host "  Bitte fuehren Sie zuerst aus:" -ForegroundColor Yellow
    Write-Host "    mvnw.cmd clean package -DskipTests" -ForegroundColor Cyan
    Write-Host ""
    Read-Host "  Druecken Sie Enter zum Beenden"
    exit 1
}

Write-OK "Anwendungs-Datei gefunden"

# =========================================================
# Schritt 3: Installation
# =========================================================
Write-Step "3/6" "Installiere nach $InstallDir..."

# Erstelle Installationsverzeichnis
if (-not (Test-Path $InstallDir)) {
    New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
}

# Erstelle Unterverzeichnisse
$logsDir = Join-Path $InstallDir "logs"
if (-not (Test-Path $logsDir)) {
    New-Item -ItemType Directory -Path $logsDir -Force | Out-Null
}

# Kopiere JAR
$JarDest = Join-Path $InstallDir $JarName
Copy-Item -Path $JarSource -Destination $JarDest -Force
Write-OK "Anwendung kopiert"

# =========================================================
# Schritt 4: Starter-Script erstellen
# =========================================================
Write-Step "4/6" "Erstelle Starter-Scripts..."

# --- VBScript: Startet Server unsichtbar + öffnet Browser ---
$vbsPath = Join-Path $InstallDir "ERP-Handwerk-Starten.vbs"
$vbsContent = @"
' =========================================================
' ERP-Handwerk - Starter
' Startet den Server im Hintergrund und oeffnet den Browser
' =========================================================
Dim WshShell, fso, installDir, jarPath, javaExe, logFile
Dim serverRunning, port

Set WshShell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

installDir = "$($InstallDir -replace '\\', '\\')"
jarPath = installDir & "\\$JarName"
port = $ServerPort
logFile = installDir & "\\logs\\server.log"

' Pruefe ob Server bereits laeuft
serverRunning = False
On Error Resume Next
Dim http
Set http = CreateObject("MSXML2.XMLHTTP.6.0")
http.Open "GET", "http://localhost:" & port & "/", False
http.setRequestHeader "Connection", "close"
http.Send
If http.Status >= 100 And http.Status < 600 Then
    serverRunning = True
End If
Set http = Nothing
On Error GoTo 0

If Not serverRunning Then
    ' Finde Java
    javaExe = "$($javaExe -replace '\\', '\\')"
    If javaExe = "" Or Not fso.FileExists(javaExe) Then
        javaExe = "java"
    End If

    ' Starte Server unsichtbar (kein Konsolen-Fenster)
    Dim cmd
    cmd = """" & javaExe & """ -jar """ & jarPath & """ --spring.profiles.active=h2 > """ & logFile & """ 2>&1"
    WshShell.Run "cmd /c " & cmd, 0, False

    ' Warte bis Server bereit ist (max. 60 Sekunden)
    Dim i, ready
    ready = False
    For i = 1 To 30
        WScript.Sleep 2000
        On Error Resume Next
        Set http = CreateObject("MSXML2.XMLHTTP.6.0")
        http.Open "GET", "http://localhost:" & port & "/", False
        http.setRequestHeader "Connection", "close"
        http.Send
        If http.Status >= 100 And http.Status < 600 Then
            ready = True
        End If
        Set http = Nothing
        On Error GoTo 0
        If ready Then Exit For
    Next

    If Not ready Then
        MsgBox "Der Server konnte nicht gestartet werden." & vbCrLf & _
               "Bitte pruefen Sie die Log-Datei:" & vbCrLf & logFile, _
               vbExclamation, "ERP-Handwerk"
        WScript.Quit 1
    End If
End If

' Browser oeffnen
WshShell.Run "http://localhost:" & port, 1, False

Set WshShell = Nothing
Set fso = Nothing
"@

Set-Content -Path $vbsPath -Value $vbsContent -Encoding UTF8
Write-OK "Starter-Script erstellt"

# --- Batch zum Stoppen ---
$stopBatPath = Join-Path $InstallDir "ERP-Handwerk-Stoppen.bat"
$stopBatContent = @"
@echo off
chcp 65001 >nul
echo ERP-Handwerk Server wird gestoppt...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":$ServerPort " ^| findstr "LISTENING"') do (
    echo Stoppe Prozess %%a...
    taskkill /PID %%a /F >nul 2>&1
)
echo Server gestoppt.
timeout /t 2 >nul
"@
Set-Content -Path $stopBatPath -Value $stopBatContent -Encoding UTF8
Write-OK "Stopp-Script erstellt"

# --- PowerShell Autostart-Script (für Scheduled Task) ---
$autostartPs1Path = Join-Path $InstallDir "autostart.ps1"
$autostartPs1Content = @"
# ERP-Handwerk Autostart (wird von Windows Aufgabenplanung aufgerufen)
`$port = $ServerPort
`$jarPath = "$JarDest"
`$logFile = "$logsDir\server.log"

# Prüfe ob Server bereits läuft
try {
    `$response = Invoke-WebRequest -Uri "http://localhost:`$port/" -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
    exit 0  # Server läuft bereits
} catch { }

# Starte Server
`$javaExe = "$javaExe"
if (-not (Test-Path `$javaExe)) { `$javaExe = "java" }

Start-Process -FilePath `$javaExe ``
    -ArgumentList "-jar", "`$jarPath", "--spring.profiles.active=h2" ``
    -WorkingDirectory "$InstallDir" ``
    -WindowStyle Hidden ``
    -RedirectStandardOutput `$logFile ``
    -RedirectStandardError "$logsDir\server-error.log"
"@
Set-Content -Path $autostartPs1Path -Value $autostartPs1Content -Encoding UTF8
Write-OK "Autostart-Script erstellt"

# =========================================================
# Schritt 5: Desktop-Shortcut erstellen
# =========================================================
Write-Step "5/6" "Erstelle Desktop-Verknuepfung..."

# Desktop-Shortcut
$WScriptShell = New-Object -ComObject WScript.Shell
$shortcut = $WScriptShell.CreateShortcut($DesktopShortcut)
$shortcut.TargetPath = "wscript.exe"
$shortcut.Arguments = "`"$vbsPath`""
$shortcut.WorkingDirectory = $InstallDir
$shortcut.Description = "ERP-Handwerk starten (Server + Browser)"
$shortcut.IconLocation = "shell32.dll,12"

# Versuche ein besseres Icon zu setzen
$iconPath = Join-Path $RepoRoot "assets\firmenlogo.ico"
if (Test-Path $iconPath) {
    $shortcut.IconLocation = $iconPath
}

$shortcut.Save()
Write-OK "Desktop-Verknuepfung erstellt: $DesktopShortcut"

# Startmenü-Shortcut
if (-not (Test-Path $StartMenuFolder)) {
    New-Item -ItemType Directory -Path $StartMenuFolder -Force | Out-Null
}
$startMenuShortcut = $WScriptShell.CreateShortcut((Join-Path $StartMenuFolder "$AppName.lnk"))
$startMenuShortcut.TargetPath = "wscript.exe"
$startMenuShortcut.Arguments = "`"$vbsPath`""
$startMenuShortcut.WorkingDirectory = $InstallDir
$startMenuShortcut.Description = "ERP-Handwerk starten (Server + Browser)"
$startMenuShortcut.IconLocation = "shell32.dll,12"
if (Test-Path $iconPath) {
    $startMenuShortcut.IconLocation = $iconPath
}
$startMenuShortcut.Save()
Write-OK "Startmenue-Verknuepfung erstellt"

# =========================================================
# Schritt 6: Autostart einrichten (Scheduled Task)
# =========================================================
Write-Step "6/6" "Richte Autostart nach PC-Neustart ein..."

# Entferne alten Task falls vorhanden
$existingTask = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($existingTask) {
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
}

# Erstelle neuen Task
$action = New-ScheduledTaskAction -Execute "PowerShell.exe" `
    -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$autostartPs1Path`""

# Trigger: Bei Benutzeranmeldung (nicht SYSTEM - damit der Server im Benutzerkontext läuft)
$trigger = New-ScheduledTaskTrigger -AtLogOn
$trigger.Delay = "PT30S"  # 30 Sekunden Verzögerung nach Login

$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 10)

try {
    Register-ScheduledTask -TaskName $TaskName `
        -Action $action `
        -Trigger $trigger `
        -Settings $settings `
        -Description "Startet den ERP-Handwerk Server automatisch nach der Anmeldung" `
        -RunLevel Limited | Out-Null
    Write-OK "Autostart eingerichtet (bei jeder Anmeldung)"
} catch {
    Write-Info "Autostart konnte nicht als Task registriert werden."
    Write-Info "Alternative: Verknuepfung in Autostart-Ordner..."

    # Fallback: Autostart-Ordner
    $autostartFolder = [Environment]::GetFolderPath("Startup")
    $autostartLink = Join-Path $autostartFolder "$AppName.lnk"
    $shortcutAutostart = $WScriptShell.CreateShortcut($autostartLink)
    $shortcutAutostart.TargetPath = "wscript.exe"
    $shortcutAutostart.Arguments = "`"$vbsPath`""
    $shortcutAutostart.WorkingDirectory = $InstallDir
    $shortcutAutostart.Description = "ERP-Handwerk Autostart"
    $shortcutAutostart.IconLocation = "shell32.dll,12"
    $shortcutAutostart.Save()
    Write-OK "Autostart ueber Autostart-Ordner eingerichtet"
}

# =========================================================
# Zusammenfassung
# =========================================================
Write-Host ""
Write-Host "  ============================================" -ForegroundColor Green
Write-Host "    Einrichtung erfolgreich!" -ForegroundColor Green
Write-Host "  ============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Installation: $InstallDir" -ForegroundColor White
Write-Host "  Datenbank:    $env:USERPROFILE\ERP-Handwerk\datenbank" -ForegroundColor White
Write-Host ""
Write-Host "  So starten Sie ERP-Handwerk:" -ForegroundColor Cyan
Write-Host "    - Doppelklick auf das Desktop-Symbol '$AppName'" -ForegroundColor White
Write-Host "    - Der Server startet automatisch im Hintergrund" -ForegroundColor White
Write-Host "    - Der Browser oeffnet sich mit der Anwendung" -ForegroundColor White
Write-Host ""
Write-Host "  Nach einem PC-Neustart:" -ForegroundColor Cyan
Write-Host "    - Der Server startet automatisch" -ForegroundColor White
Write-Host "    - Klicken Sie einfach auf das Desktop-Symbol" -ForegroundColor White
Write-Host ""
Write-Host "  Server stoppen:" -ForegroundColor Cyan
Write-Host "    - Doppelklick auf 'ERP-Handwerk-Stoppen.bat'" -ForegroundColor White
Write-Host "    - in $InstallDir" -ForegroundColor White
Write-Host ""
Read-Host "  Druecken Sie Enter zum Beenden"
