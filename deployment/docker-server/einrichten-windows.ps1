# =============================================================================
# ERP Handwerk - Einrichtung auf einem Windows-PC (Docker Desktop)
# =============================================================================
# Start per Doppelklick auf Einrichten.cmd. Kann gefahrlos wiederholt werden.
#
#   1. prueft Docker Desktop (bietet die Installation an, falls es fehlt)
#   2. Docker Desktop startet ab jetzt automatisch bei der Anmeldung
#   3. PC geht am Strom nicht mehr in Standby/Ruhezustand (sonst ist das ERP
#      nachts und in der Mittagspause weg)
#   4. gibt den Ordner nur Administratoren und dem aktuellen Benutzer frei
#   5. legt .env mit zufaelligen Passwoertern an (nur beim ersten Mal)
#   6. startet alles mit "docker compose up -d" und wartet, bis das ERP laeuft
#   7. legt eine Verknuepfung "ERP Handwerk" auf den Desktop
#
# Danach laeuft das ERP immer: Die Container haben "restart: always" und
# starten mit Docker Desktop von selbst. Updates spielt der Updater-Container
# nachts um 3 Uhr ein.
#
# Hinweis: Diese Datei bewusst nur mit ASCII-Zeichen (Windows PowerShell 5.1
# liest Dateien ohne BOM sonst mit falscher Kodierung).
# =============================================================================

$ErrorActionPreference = 'Stop'
$Ordner = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Ordner

function Schritt($text) { Write-Host ""; Write-Host "==> $text" -ForegroundColor Cyan }
function Ok($text) { Write-Host "    [OK] $text" -ForegroundColor Green }
function Hinweis($text) { Write-Host "    [!] $text" -ForegroundColor Yellow }

function Zufallspasswort {
    $zeichen = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'.ToCharArray()
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    -join ($bytes | ForEach-Object { $zeichen[$_ % $zeichen.Length] })
}

function Docker-Laeuft {
    try { docker info *> $null; return ($LASTEXITCODE -eq 0) } catch { return $false }
}

$DockerExe = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'

# --- 1. Docker Desktop ---------------------------------------------------------
Schritt 'Docker Desktop pruefen'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Hinweis 'Docker Desktop ist nicht installiert.'
    $antwort = Read-Host '    Jetzt installieren (winget)? [j/n]'
    if ($antwort -match '^[jJyY]') {
        winget install -e --id Docker.DockerDesktop --accept-package-agreements --accept-source-agreements
        Hinweis 'Bitte den PC neu starten, Docker Desktop einmal oeffnen (Lizenz bestaetigen) und Einrichten.cmd erneut starten.'
    }
    exit 1
}
if (-not (Docker-Laeuft)) {
    if (Test-Path $DockerExe) { Start-Process $DockerExe }
    Write-Host '    Warte auf Docker Desktop ...'
    $bis = (Get-Date).AddMinutes(4)
    while (-not (Docker-Laeuft) -and (Get-Date) -lt $bis) { Start-Sleep -Seconds 5 }
    if (-not (Docker-Laeuft)) { Hinweis 'Docker Desktop startet nicht. Bitte einmal von Hand oeffnen und erneut versuchen.'; exit 1 }
}
Ok 'Docker laeuft'

# --- 2. Autostart --------------------------------------------------------------
Schritt 'Docker Desktop beim Anmelden automatisch starten'
if (Test-Path $DockerExe) {
    Set-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run' -Name 'Docker Desktop' -Value "`"$DockerExe`""
}
# Neuere Versionen merken sich die Einstellung zusaetzlich in settings-store.json
foreach ($datei in @("$env:APPDATA\Docker\settings-store.json", "$env:APPDATA\Docker\settings.json")) {
    if (Test-Path $datei) {
        try {
            $einstellungen = Get-Content $datei -Raw | ConvertFrom-Json
            $schluessel = if ($datei -like '*settings-store.json') { 'AutoStart' } else { 'autoStart' }
            $einstellungen | Add-Member -NotePropertyName $schluessel -NotePropertyValue $true -Force
            [IO.File]::WriteAllText($datei, ($einstellungen | ConvertTo-Json -Depth 20), (New-Object Text.UTF8Encoding($false)))
        } catch { Hinweis "Konnte $datei nicht anpassen: $_" }
    }
}
Ok 'Autostart eingerichtet'

# --- 3. Energiesparen ----------------------------------------------------------
Schritt 'Standby und Ruhezustand am Netzstrom abschalten'
try {
    powercfg /change standby-timeout-ac 0 | Out-Null
    powercfg /change hibernate-timeout-ac 0 | Out-Null
    Ok 'PC bleibt am Strom wach'
} catch { Hinweis 'Energieeinstellungen nicht geaendert (Administratorrechte?). Bitte von Hand: Einstellungen > System > Energie > Standby: Nie.' }

# --- 4. Ordnerrechte -----------------------------------------------------------
# Ein Ordner wie C:\ERP-Handwerk erbt sonst "Authentifizierte Benutzer: Aendern":
# jedes Windows-Konto koennte .env (Passwoerter), sicherungen\ (alle Kundendaten)
# lesen und nachtupdate.sh aendern - das im Updater mit vollem Docker-Zugriff laeuft.
Schritt 'Ordner nur fuer Administratoren und diesen Benutzer freigeben'
try {
    # Aktueller Benutzer per SID - der Name allein ist bei Domaenen-/Microsoft-Konten mehrdeutig
    $ich = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    icacls $Ordner /inheritance:r /grant:r '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-18:(OI)(CI)F' "*$($ich):(OI)(CI)F" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "icacls Fehlercode $LASTEXITCODE" }
    Ok 'Andere Windows-Benutzer haben keinen Zugriff mehr'
} catch { Hinweis "Rechte nicht gesetzt ($_). Bitte von Hand: Ordner > Eigenschaften > Sicherheit." }

# --- 5. Einstellungen (.env) ---------------------------------------------------
Schritt 'Einstellungen'
$EnvDatei = Join-Path $Ordner '.env'
if (-not (Test-Path $EnvDatei)) {
    $kunde = Read-Host '    Name des Betriebs (fuer Handy-Nachrichten)'
    if ([string]::IsNullOrWhiteSpace($kunde)) { $kunde = $env:COMPUTERNAME }
    $inhalt = Get-Content (Join-Path $Ordner '.env.example') -Raw
    $inhalt = $inhalt.Replace('CHANGE_ME_ROOT_PW', (Zufallspasswort)).Replace('CHANGE_ME_DB_PW', (Zufallspasswort))
    # Zeilenweise ersetzen statt -replace: ein $ im Firmennamen waere sonst ein Rueckverweis
    $inhalt = ($inhalt -split "`n" | ForEach-Object { if ($_ -like 'KUNDE_NAME=*') { "KUNDE_NAME=$($kunde.Trim())" } else { $_ } }) -join "`n"
    [IO.File]::WriteAllText($EnvDatei, $inhalt, (New-Object Text.UTF8Encoding($false)))
    Ok '.env mit zufaelligen Passwoertern angelegt (gut aufbewahren, nicht weitergeben)'
} else {
    Ok '.env ist schon da - bleibt unveraendert'
}
$port = (Select-String -Path $EnvDatei -Pattern '^APP_PORT=(\d+)' | Select-Object -First 1)
$port = if ($port) { $port.Matches[0].Groups[1].Value } else { '8080' }

# --- 6. Starten ----------------------------------------------------------------
Schritt 'ERP starten (beim ersten Mal werden ca. 1 GB geladen)'
docker compose up -d
if ($LASTEXITCODE -ne 0) { Hinweis 'docker compose up ist fehlgeschlagen - Meldung oben ansehen.'; exit 1 }
Write-Host '    Warte, bis das ERP antwortet (erster Start richtet die Datenbank ein) ...'
$bis = (Get-Date).AddMinutes(15)
$laeuft = $false
while ((Get-Date) -lt $bis) {
    try {
        $antwort = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 "http://localhost:$port/actuator/health"
        if ($antwort.StatusCode -eq 200) { $laeuft = $true; break }
    } catch { }
    Start-Sleep -Seconds 3
}
if (-not $laeuft) { Hinweis 'Das ERP antwortet noch nicht. Fehler ansehen mit:  docker compose logs app'; exit 1 }
Ok "ERP laeuft: http://localhost:$port"

# --- 7. Verknuepfung -----------------------------------------------------------
$desktop = [Environment]::GetFolderPath('Desktop')
[IO.File]::WriteAllText((Join-Path $desktop 'ERP Handwerk.url'), "[InternetShortcut]`r`nURL=http://localhost:$port/`r`n")
Ok 'Verknuepfung "ERP Handwerk" auf dem Desktop'

Write-Host ""
Write-Host "Fertig. Das ERP startet ab jetzt nach jedem Neustart von selbst." -ForegroundColor Green
Hinweis 'Docker Desktop startet erst, wenn sich jemand an Windows anmeldet. Fuer einen PC, der'
Hinweis 'nach Stromausfall/Windows-Update ohne Anmeldung weiterlaufen soll: automatische'
Hinweis 'Anmeldung einrichten, am sichersten mit "Autologon" von Microsoft Sysinternals.'
Start-Process "http://localhost:$port/"
