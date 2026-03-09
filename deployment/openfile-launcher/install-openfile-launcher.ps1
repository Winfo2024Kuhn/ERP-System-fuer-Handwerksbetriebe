# ====================================================================
# OpenFile Launcher - Automatisches Installations-Skript
# ====================================================================
# Dieses Skript installiert den OpenFile Launcher auf einem Client:
# 1. Kopiert launcher.ps1 nach C:\Program Files (x86)\OpenFileLauncher
# 2. Registriert das openfile:// Schema in der Windows Registry
#
# WICHTIG: Muss als Administrator ausgeführt werden!
# ====================================================================

# Prüfen, ob als Administrator ausgeführt
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host ""
    Write-Host "FEHLER: Dieses Skript muss als Administrator ausgefuehrt werden!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Bitte:" -ForegroundColor Yellow
    Write-Host "1. PowerShell als Administrator oeffnen" -ForegroundColor Yellow
    Write-Host "2. Zu diesem Verzeichnis navigieren" -ForegroundColor Yellow
    Write-Host "3. Dieses Skript erneut ausfuehren" -ForegroundColor Yellow
    Write-Host ""
    pause
    exit 1
}

Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host " OpenFile Launcher - Installation" -ForegroundColor Cyan
Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host ""

# Verzeichnisse
# Wenn als .ps1-Datei ausgeführt: Verwende Skript-Verzeichnis
# Wenn in Konsole eingefügt: Verwende aktuelles Verzeichnis
if ($MyInvocation.MyCommand.Path) {
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
} else {
    $scriptDir = Get-Location | Select-Object -ExpandProperty Path
    Write-Host "HINWEIS: Skript wurde direkt in Konsole eingefuegt. Verwende aktuelles Verzeichnis." -ForegroundColor Yellow
    Write-Host "Aktuelles Verzeichnis: $scriptDir" -ForegroundColor Yellow
    Write-Host ""
}

$targetDir = "C:\Program Files (x86)\OpenFileLauncher"
$launcherScript = Join-Path $scriptDir "launcher.ps1"

# Prüfen, ob launcher.ps1 vorhanden ist
if (-not (Test-Path $launcherScript)) {
    Write-Host "FEHLER: launcher.ps1 nicht gefunden!" -ForegroundColor Red
    Write-Host "Erwartet: $launcherScript" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Loesungen:" -ForegroundColor Cyan
    Write-Host "1. Navigieren Sie zum Ordner mit launcher.ps1:" -ForegroundColor White
    Write-Host "   cd 'C:\Pfad\zum\Ordner'" -ForegroundColor Gray
    Write-Host ""
    Write-Host "2. ODER fuehren Sie diese .ps1-Datei direkt aus:" -ForegroundColor White
    Write-Host "   Rechtsklick -> 'Als Administrator ausfuehren'" -ForegroundColor Gray
    Write-Host ""
    pause
    exit 1
}

try {
    # Schritt 1: Verzeichnis erstellen
    Write-Host "[1/4] Erstelle Zielverzeichnis: $targetDir" -ForegroundColor Cyan
    if (-not (Test-Path $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
        Write-Host "   Verzeichnis erstellt" -ForegroundColor Green
    } else {
        Write-Host "   Verzeichnis existiert bereits" -ForegroundColor Yellow
    }

    # Schritt 2: launcher.ps1 kopieren
    Write-Host "[2/4] Kopiere launcher.ps1..." -ForegroundColor Cyan
    $targetScript = Join-Path $targetDir "launcher.ps1"
    Copy-Item -Path $launcherScript -Destination $targetScript -Force
    Write-Host "   Kopiert nach: $targetScript" -ForegroundColor Green

    # Optional: launcher.cmd kopieren (falls vorhanden)
    $launcherCmd = Join-Path $scriptDir "launcher.cmd"
    if (Test-Path $launcherCmd) {
        $targetCmd = Join-Path $targetDir "launcher.cmd"
        Copy-Item -Path $launcherCmd -Destination $targetCmd -Force
        Write-Host "   launcher.cmd ebenfalls kopiert" -ForegroundColor Green
    }

    # Schritt 3: Registry - HKCR verfügbar machen
    Write-Host "[3/4] Registriere openfile:// Schema in der Registry..." -ForegroundColor Cyan
    if (-not (Test-Path "HKCR:\")) {
        New-PSDrive -Name HKCR -PSProvider Registry -Root HKEY_CLASSES_ROOT | Out-Null
    }

    # Registry-Schlüssel erstellen
    $schemaPath = "HKCR:\openfile"
    
    if (-not (Test-Path $schemaPath)) {
        New-Item -Path $schemaPath -Force | Out-Null
    }
    Set-ItemProperty -Path $schemaPath -Name "(Default)" -Value "URL:OpenFile Protocol" -Type String
    Set-ItemProperty -Path $schemaPath -Name "URL Protocol" -Value "" -Type String

    $iconPath = "$schemaPath\DefaultIcon"
    if (-not (Test-Path $iconPath)) {
        New-Item -Path $iconPath -Force | Out-Null
    }
    Set-ItemProperty -Path $iconPath -Name "(Default)" -Value "shell32.dll,0" -Type String

    $shellPath = "$schemaPath\shell"
    $openPath = "$shellPath\open"
    if (-not (Test-Path $openPath)) {
        New-Item -Path $openPath -Force | Out-Null
    }

    $commandPath = "$openPath\command"
    if (-not (Test-Path $commandPath)) {
        New-Item -Path $commandPath -Force | Out-Null
    }
    
    $command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$targetScript`" `"%1`""
    Set-ItemProperty -Path $commandPath -Name "(Default)" -Value $command -Type String
    Write-Host "   Registry-Eintraege erstellt" -ForegroundColor Green

    # Schritt 4: Verifizierung
    Write-Host "[4/4] Verifiziere Installation..." -ForegroundColor Cyan
    $verifyCommand = (Get-ItemProperty -Path $commandPath)."(Default)"
    if ($verifyCommand -eq $command) {
        Write-Host "   Verifikation erfolgreich" -ForegroundColor Green
    } else {
        Write-Host "   WARNUNG: Registry-Eintrag stimmt nicht ueberein!" -ForegroundColor Yellow
    }

    Write-Host ""
    Write-Host "=====================================================================" -ForegroundColor Green
    Write-Host " Installation erfolgreich abgeschlossen!" -ForegroundColor Green
    Write-Host "=====================================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Installierte Komponenten:" -ForegroundColor Cyan
    Write-Host "  - Launcher-Skript: $targetScript" -ForegroundColor White
    Write-Host "  - Registry Schema: openfile://" -ForegroundColor White
    Write-Host ""
    Write-Host "Sie koennen jetzt openfile://-Links verwenden!" -ForegroundColor Green
    Write-Host ""

} catch {
    Write-Host ""
    Write-Host "FEHLER bei der Installation:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ""
    pause
    exit 1
}

Write-Host "Druecken Sie eine Taste zum Beenden..."
pause
