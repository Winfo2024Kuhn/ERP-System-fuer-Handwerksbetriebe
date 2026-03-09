# ====================================================================
# Erstellt eine einfache selbstextrahierende Setup.exe
# ====================================================================

$ErrorActionPreference = 'Stop'

Write-Host "Erstelle OpenFileLauncher-Setup.exe..." -ForegroundColor Cyan
Write-Host ""

# Lese launcher.ps1 und encode zu Base64
$launcherContent = Get-Content "launcher.ps1" -Raw
$launcherBytes = [System.Text.Encoding]::UTF8.GetBytes($launcherContent)
$launcherBase64 = [Convert]::ToBase64String($launcherBytes)

# Erstelle das Setup-Skript
$setupScript = @"
# OpenFile Launcher - Self-Extracting Setup
`$ErrorActionPreference = 'Stop'

# Prüfe Admin-Rechte
`$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not `$isAdmin) {
    [System.Windows.Forms.MessageBox]::Show("Bitte führen Sie das Setup als Administrator aus!`n`nRechtsklick -> 'Als Administrator ausführen'", "OpenFile Launcher Setup", [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Error)
    exit 1
}

# Zeige Installation-Start
Add-Type -AssemblyName System.Windows.Forms
[System.Windows.Forms.MessageBox]::Show("OpenFile Launcher wird installiert...`n`nDies dauert nur wenige Sekunden.", "OpenFile Launcher Setup", [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Information) | Out-Null

try {
    # Erstelle Zielverzeichnis
    `$targetDir = "C:\Program Files (x86)\OpenFileLauncher"
    if (-not (Test-Path `$targetDir)) {
        New-Item -ItemType Directory -Path `$targetDir -Force | Out-Null
    }

    # Dekodiere und speichere launcher.ps1
    `$launcherBase64 = "$launcherBase64"
    `$launcherBytes = [Convert]::FromBase64String(`$launcherBase64)
    `$launcherContent = [System.Text.Encoding]::UTF8.GetString(`$launcherBytes)
    `$launcherPath = Join-Path `$targetDir "launcher.ps1"
    Set-Content -Path `$launcherPath -Value `$launcherContent -Encoding UTF8

    # Registriere openfile:// Schema
    if (-not (Test-Path "HKCR:\")) {
        New-PSDrive -Name HKCR -PSProvider Registry -Root HKEY_CLASSES_ROOT | Out-Null
    }

    `$schemaPath = "HKCR:\openfile"
    
    if (-not (Test-Path `$schemaPath)) {
        New-Item -Path `$schemaPath -Force | Out-Null
    }
    Set-ItemProperty -Path `$schemaPath -Name "(Default)" -Value "URL:OpenFile Protocol" -Type String
    Set-ItemProperty -Path `$schemaPath -Name "URL Protocol" -Value "" -Type String

    `$iconPath = "`$schemaPath\DefaultIcon"
    if (-not (Test-Path `$iconPath)) {
        New-Item -Path `$iconPath -Force | Out-Null
    }
    Set-ItemProperty -Path `$iconPath -Name "(Default)" -Value "shell32.dll,0" -Type String

    `$shellPath = "`$schemaPath\shell"
    `$openPath = "`$shellPath\open"
    if (-not (Test-Path `$openPath)) {
        New-Item -Path `$openPath -Force | Out-Null
    }

    `$commandPath = "`$openPath\command"
    if (-not (Test-Path `$commandPath)) {
        New-Item -Path `$commandPath -Force | Out-Null
    }
    
    `$command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ```"`$launcherPath```" ```"%1```""
    Set-ItemProperty -Path `$commandPath -Name "(Default)" -Value `$command -Type String

    # Erfolgsmeldung
    [System.Windows.Forms.MessageBox]::Show("OpenFile Launcher wurde erfolgreich installiert!`n`nSie können jetzt openfile://-Links verwenden.", "Installation erfolgreich", [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Information) | Out-Null
    exit 0

} catch {
    [System.Windows.Forms.MessageBox]::Show("Fehler bei der Installation:`n`n`$(`$_.Exception.Message)", "Fehler", [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Error)
    exit 1
}
"@

# Speichere das Setup-Skript temporär
$tempPs1 = "$env:TEMP\openfile-setup-script.ps1"
Set-Content -Path $tempPs1 -Value $setupScript -Encoding UTF8

# Konvertiere zu .exe mit ps2exe (falls installiert) oder erstelle ein Batch-Wrapper
Write-Host "Erstelle finale Setup.exe..." -ForegroundColor Green
Write-Host ""

# Erstelle ein einfaches Batch-Setup, das das PowerShell-Skript ausführt
$batchContent = @"
@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command "& { $setupScript }"
"@

Set-Content -Path "OpenFileLauncher-Setup-Simple.bat" -Value $batchContent -Encoding ASCII

Write-Host "Setup erstellt:" -ForegroundColor Green
Write-Host "  OpenFileLauncher-Setup-Simple.bat" -ForegroundColor Yellow
Write-Host ""
Write-Host "HINWEIS: Für eine echte .exe benötigen Sie Inno Setup." -ForegroundColor Cyan
Write-Host "Die .bat-Datei funktioniert genauso, sieht aber weniger professionell aus." -ForegroundColor Cyan
Write-Host ""

# Cleanup
Remove-Item $tempPs1 -Force -ErrorAction SilentlyContinue
