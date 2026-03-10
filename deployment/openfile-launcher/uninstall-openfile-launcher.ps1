# ====================================================================
# OpenFile Launcher - Deinstallations-Skript
# ====================================================================
# Entfernt den OpenFile Launcher vollständig vom System
#
# WICHTIG: Muss als Administrator ausgeführt werden!
# ====================================================================

# Prüfen, ob als Administrator ausgeführt
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host ""
    Write-Host "FEHLER: Dieses Skript muss als Administrator ausgefuehrt werden!" -ForegroundColor Red
    Write-Host ""
    pause
    exit 1
}

Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host " OpenFile Launcher - Deinstallation" -ForegroundColor Cyan
Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host ""

$targetDir = "C:\Program Files (x86)\OpenFileLauncher"

try {
    # Schritt 1: Registry entfernen
    Write-Host "[1/2] Entferne Registry-Eintraege..." -ForegroundColor Cyan
    if (-not (Test-Path "HKCR:\")) {
        New-PSDrive -Name HKCR -PSProvider Registry -Root HKEY_CLASSES_ROOT | Out-Null
    }

    $schemaPath = "HKCR:\openfile"
    if (Test-Path $schemaPath) {
        Remove-Item -Path $schemaPath -Recurse -Force
        Write-Host "   Registry-Eintraege entfernt" -ForegroundColor Green
    } else {
        Write-Host "   Keine Registry-Eintraege gefunden" -ForegroundColor Yellow
    }

    # Schritt 2: Dateien entfernen
    Write-Host "[2/2] Entferne Dateien..." -ForegroundColor Cyan
    if (Test-Path $targetDir) {
        Remove-Item -Path $targetDir -Recurse -Force
        Write-Host "   Verzeichnis entfernt: $targetDir" -ForegroundColor Green
    } else {
        Write-Host "   Verzeichnis nicht vorhanden" -ForegroundColor Yellow
    }

    Write-Host ""
    Write-Host "=====================================================================" -ForegroundColor Green
    Write-Host " Deinstallation erfolgreich abgeschlossen!" -ForegroundColor Green
    Write-Host "=====================================================================" -ForegroundColor Green
    Write-Host ""

} catch {
    Write-Host ""
    Write-Host "FEHLER bei der Deinstallation:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ""
    pause
    exit 1
}

Write-Host "Druecken Sie eine Taste zum Beenden..."
pause
