# =====================================================================
#  ERP-System – Netzwerkfreigabe + OpenFileLauncher Update
#  Als Administrator ausfuehren!
# =====================================================================

$ErrorActionPreference = 'Stop'

# Pfade relativ zu diesem Skript ermitteln
$ScriptDir    = Split-Path -Parent $MyInvocation.MyCommand.Definition
$AppRoot      = Split-Path -Parent (Split-Path -Parent $ScriptDir)
$PropsFile    = Join-Path $AppRoot 'src\main\resources\application-local.properties'
$LauncherSrc  = Join-Path $ScriptDir 'launcher.ps1'
$LauncherDest = 'C:\Program Files\OpenFileLauncher\launcher.ps1'
$ConfigDest   = 'C:\Program Files\OpenFileLauncher\config.json'

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "  ERP Netzwerkfreigabe + Launcher Setup" -ForegroundColor Cyan
Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "  App-Root: $AppRoot"

# ---- hicad.network-url aus application-local.properties lesen ----
Write-Host "`n[1/4] Lese hicad.network-url aus application-local.properties..."
if (-not (Test-Path $PropsFile)) {
    Write-Host "  FEHLER: $PropsFile nicht gefunden!" -ForegroundColor Red
    exit 1
}
$rawLine = (Get-Content $PropsFile | Where-Object { $_ -match '^hicad\.network-url\s*=' }) | Select-Object -Last 1
if (-not $rawLine) {
    Write-Host "  FEHLER: hicad.network-url nicht in $PropsFile gefunden!" -ForegroundColor Red
    exit 1
}
# Java-Properties-Escaping aufloesen: \\ -> \
$networkUrl = ($rawLine -replace '^hicad\.network-url\s*=\s*', '').Replace('\\', '\')
if (-not $networkUrl.StartsWith('\\')) {
    Write-Host "  FEHLER: hicad.network-url ist kein UNC-Pfad: $networkUrl" -ForegroundColor Red
    exit 1
}
Write-Host "  Netzwerkpfad: $networkUrl" -ForegroundColor Green

# Share-Name und Pfad aus dem UNC-Root ableiten
# \\SERVER\ShareName\SubDir  ->  \\SERVER\ShareName
$parts     = $networkUrl.TrimStart('\').Split('\')   # SERVER, ShareName, SubDir...
$shareName = $parts[1]
$uploadsPath = Join-Path $AppRoot 'uploads'

# ---- Netzwerkfreigabe erstellen ----
Write-Host "`n[2/4] Netzwerkfreigabe '$shareName' einrichten..."
cmd /c "net share $shareName /delete /yes 2>nul" | Out-Null
$shareOut = cmd /c "net share ${shareName}=${uploadsPath} 2>&1"
Write-Host "  $shareOut"

# CHANGE-Recht via SID S-1-1-0 (Everyone, sprachunabhaengig)
try {
    $sid      = New-Object System.Security.Principal.SecurityIdentifier('S-1-1-0')
    $account  = $sid.Translate([System.Security.Principal.NTAccount]).Value
    Grant-SmbShareAccess -Name $shareName -AccountName $account -AccessRight Change -Force | Out-Null
    Write-Host "  CHANGE-Recht gesetzt fuer $account" -ForegroundColor Green
} catch {
    Write-Host "  WARNUNG: CHANGE-Recht konnte nicht gesetzt werden: $($_.Exception.Message)" -ForegroundColor Yellow
}

# ---- config.json generieren ----
Write-Host "`n[3/4] config.json generieren..."
$config = [PSCustomObject]@{
    allowedRoots = @($networkUrl)
    rootAliases  = [PSCustomObject]@{}
}
$configJson = $config | ConvertTo-Json -Depth 3
$configJson | Set-Content -Path $ConfigDest -Encoding UTF8
Write-Host "  OK: $ConfigDest" -ForegroundColor Green
Write-Host "  Inhalt: $configJson"

# ---- Launcher kopieren ----
Write-Host "`n[4/4] launcher.ps1 aktualisieren..."
Copy-Item -Path $LauncherSrc -Destination $LauncherDest -Force
Write-Host "  OK: $LauncherDest" -ForegroundColor Green

Write-Host "`n=====================================================" -ForegroundColor Cyan
Write-Host "  Setup abgeschlossen!" -ForegroundColor Green
Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Netzwerkpfad : $networkUrl"
Write-Host "Konfiguration: $ConfigDest"
Write-Host ""
Write-Host "Jetzt Backend neu starten, dann oeffnen .xlsm/.sza"
Write-Host "direkt in Excel/HiCAD (keine Kopien)."
Write-Host ""
Read-Host "Enter zum Beenden"
