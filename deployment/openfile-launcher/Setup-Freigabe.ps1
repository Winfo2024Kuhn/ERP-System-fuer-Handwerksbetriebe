# =====================================================================
#  ERP-System – Netzwerkfreigabe + OpenFileLauncher Update
#  Als Administrator ausfuehren!
#
#  -Zugriff: Konten oder Gruppen, die die Freigabe lesen und aendern
#  duerfen, z. B. -Zugriff 'ERP-Benutzer'. Ohne Angabe: alle angemeldeten
#  Benutzer ("Authentifizierte Benutzer") - Gast und anonyme Zugriffe nicht.
# =====================================================================

param(
    [string[]]$Zugriff = @()
)

$ErrorActionPreference = 'Stop'

# Bei Fehlern das Fenster offen halten - per Rechtsklick gestartet waere die
# Meldung sonst sofort weg
function Abbruch([string]$Meldung) {
    Write-Host "  FEHLER: $Meldung" -ForegroundColor Red
    Read-Host "Enter zum Beenden" | Out-Null
    exit 1
}
trap { Abbruch $_.Exception.Message }

# Pfade relativ zu diesem Skript ermitteln
$ScriptDir    = Split-Path -Parent $MyInvocation.MyCommand.Definition
$AppRoot      = Split-Path -Parent (Split-Path -Parent $ScriptDir)
$PropsFile    = Join-Path $AppRoot 'src\main\resources\application-local.properties'
$LauncherSrc  = Join-Path $ScriptDir 'launcher.ps1'
$LauncherDir  = 'C:\Program Files\OpenFileLauncher'
$LauncherDest = Join-Path $LauncherDir 'launcher.ps1'
$ConfigDest   = Join-Path $LauncherDir 'config.json'
# Kopie fuer die Arbeitsplaetze: install-openfile-launcher.ps1 nimmt sie aus diesem Ordner mit
$ConfigForClients = Join-Path $ScriptDir 'config.json'

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "  ERP Netzwerkfreigabe + Launcher Setup" -ForegroundColor Cyan
Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "  App-Root: $AppRoot"

# ---- hicad.network-url aus application-local.properties lesen ----
Write-Host "`n[1/4] Lese hicad.network-url aus application-local.properties..."
if (-not (Test-Path $PropsFile)) {
    Abbruch "$PropsFile nicht gefunden!"
}
$rawLine = (Get-Content $PropsFile | Where-Object { $_ -match '^hicad\.network-url\s*=' }) | Select-Object -Last 1
if (-not $rawLine) {
    Abbruch "hicad.network-url nicht in $PropsFile gefunden!"
}
# Java-Properties-Escaping aufloesen: \\ -> \
$networkUrl = ($rawLine -replace '^hicad\.network-url\s*=\s*', '').Replace('\\', '\')
if (-not $networkUrl.StartsWith('\\')) {
    Abbruch "hicad.network-url ist kein UNC-Pfad: $networkUrl"
}
Write-Host "  Netzwerkpfad: $networkUrl" -ForegroundColor Green

# Share-Name und Pfad aus dem UNC-Root ableiten
# \\SERVER\ShareName\SubDir  ->  \\SERVER\ShareName
$parts     = $networkUrl.TrimStart('\').Split('\')   # SERVER, ShareName, SubDir...
$shareName = $parts[1]
$uploadsPath = Join-Path $AppRoot 'uploads'

# ---- Netzwerkfreigabe erstellen ----
# Aendern-Recht ist noetig, weil Excel und HiCAD direkt auf der Freigabe speichern.
# Es geht nur an angemeldete Benutzer (bzw. -Zugriff), nie an "Jeder": Das schliesst
# Gast- und anonyme Zugriffe ein.
Write-Host "`n[2/4] Netzwerkfreigabe '$shareName' einrichten..."
if (-not (Test-Path $uploadsPath)) {
    Abbruch "Upload-Ordner nicht gefunden: $uploadsPath"
}
if ($Zugriff.Count -eq 0) {
    # SID S-1-5-11 = Authentifizierte Benutzer (sprachunabhaengig)
    $sid     = New-Object System.Security.Principal.SecurityIdentifier('S-1-5-11')
    $Zugriff = @($sid.Translate([System.Security.Principal.NTAccount]).Value)
    # Freigegeben wird der ganze uploads-Ordner (alle Kundendokumente und Mail-Anhaenge).
    # In einem Netz mit vielen Konten (Domaene) besser eine eigene Gruppe angeben.
    Write-Host "  HINWEIS: Aendern-Recht fuer ALLE angemeldeten Benutzer auf den ganzen uploads-Ordner." -ForegroundColor Yellow
    Write-Host "  Enger fassen mit eigener Gruppe, z. B.: .\Setup-Freigabe.ps1 -Zugriff 'ERP-Benutzer'" -ForegroundColor Yellow
    Write-Host "  Die Ordnerrechte (NTFS) auf $uploadsPath setzt dieses Skript nicht." -ForegroundColor Yellow
}
if (Get-SmbShare -Name $shareName -ErrorAction SilentlyContinue) {
    Remove-SmbShare -Name $shareName -Force
}
New-SmbShare -Name $shareName -Path $uploadsPath -ChangeAccess $Zugriff | Out-Null
# Einen Standard-Eintrag "Jeder" (S-1-1-0) entfernen, falls Windows ihn anlegt
$jeder = (New-Object System.Security.Principal.SecurityIdentifier('S-1-1-0')).Translate([System.Security.Principal.NTAccount]).Value
Revoke-SmbShareAccess -Name $shareName -AccountName $jeder -Force -ErrorAction SilentlyContinue | Out-Null
Write-Host "  Freigabe: \\$env:COMPUTERNAME\$shareName -> $uploadsPath" -ForegroundColor Green
Get-SmbShareAccess -Name $shareName | ForEach-Object {
    Write-Host "  Recht: $($_.AccountName) - $($_.AccessControlType) $($_.AccessRight)" -ForegroundColor Green
}

# ---- config.json generieren ----
Write-Host "`n[3/4] config.json generieren..."
$config = [PSCustomObject]@{
    allowedRoots = @($networkUrl)
    rootAliases  = [PSCustomObject]@{}
}
$configJson = $config | ConvertTo-Json -Depth 3
New-Item -ItemType Directory -Path $LauncherDir -Force | Out-Null
$configJson | Set-Content -Path $ConfigDest -Encoding UTF8
$configJson | Set-Content -Path $ConfigForClients -Encoding UTF8
Write-Host "  OK: $ConfigDest" -ForegroundColor Green
Write-Host "  OK: $ConfigForClients (fuer die Arbeitsplaetze)" -ForegroundColor Green
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
Write-Host "Arbeitsplaetze: diesen Ordner (mit config.json) auf den PC kopieren"
Write-Host "und INSTALL.bat als Administrator ausfuehren."
Write-Host ""
Read-Host "Enter zum Beenden"
