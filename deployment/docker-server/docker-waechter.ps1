# =============================================================================
# ERP Handwerk - Docker-Waechter (Windows-PC mit Docker Desktop)
# =============================================================================
# Laeuft alle 5 Minuten als geplante Aufgabe "ERP Handwerk Docker-Waechter".
# Prueft, ob das ERP von Windows aus antwortet, und startet Docker Desktop neu,
# wenn es haengt.
#
# Warum: Windows Update tauscht ab und zu WSL (den Linux-Unterbau von Docker
# Desktop) im laufenden Betrieb aus. Danach starten die Container zwar wieder,
# aber Docker Desktop leitet den Port nicht mehr weiter - oder Docker Desktop
# ist ganz weg. Das ERP ist dann nicht erreichbar, bis jemand Docker Desktop
# neu startet. Das erledigt dieser Waechter.
#
# Er greift NUR ein, wenn
#   a) Docker gar nicht antwortet, oder
#   b) der ERP-Container sich selbst als gesund meldet, von Windows aus aber
#      nicht erreichbar ist (Port-Weiterleitung haengt).
# Startet das ERP gerade (Nachtupdate, Neustart) oder wurde es bewusst
# gestoppt, laesst er es in Ruhe. Hoechstens ein Neustart pro 30 Minuten, damit
# er nie in eine Neustart-Schleife geraet.
#
# Aufgabe einrichten (macht Einrichten.cmd mit, kann gefahrlos wiederholt werden):
#   powershell -ExecutionPolicy Bypass -File docker-waechter.ps1 -Einrichten
# Abschalten: Aufgabenplanung > "ERP Handwerk Docker-Waechter" > Deaktivieren.
# Log: logs\docker-waechter.log
#
# Hinweis: Diese Datei bewusst nur mit ASCII-Zeichen (Windows PowerShell 5.1
# liest Dateien ohne BOM sonst mit falscher Kodierung).
# =============================================================================

param([switch]$Einrichten)

$ErrorActionPreference = 'Stop'
$Ordner = Split-Path -Parent $MyInvocation.MyCommand.Path
$AufgabenName = 'ERP Handwerk Docker-Waechter'
$DockerExe = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'

# --- Aufgabe in der Aufgabenplanung anlegen ----------------------------------
if ($Einrichten) {
    $skript = $MyInvocation.MyCommand.Path
    $psArgumente = "-NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$skript`""
    # conhost --headless: sonst blitzt alle 5 Minuten ein Konsolenfenster auf.
    # Gibt es erst ab Windows 10 1809 (Build 17763).
    if ([Environment]::OSVersion.Version.Build -ge 17763) {
        $aktion = New-ScheduledTaskAction -Execute (Join-Path $env:WINDIR 'System32\conhost.exe') `
            -Argument "--headless powershell.exe $psArgumente"
    } else {
        $aktion = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $psArgumente
    }
    # Ohne RepetitionDuration wiederholt Windows unbegrenzt
    $ausloeser = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 5)
    # Nur im angemeldeten Benutzer: Docker Desktop laeuft auch nur dort
    $benutzer = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    $konto = New-ScheduledTaskPrincipal -UserId $benutzer -LogonType Interactive -RunLevel Limited
    $einstellungen = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
        -StartWhenAvailable -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 20)
    Register-ScheduledTask -TaskName $AufgabenName -Action $aktion -Trigger $ausloeser -Principal $konto `
        -Settings $einstellungen -Description 'Startet Docker Desktop neu, wenn das ERP haengt (siehe docker-waechter.ps1).' `
        -Force | Out-Null
    Write-Host "    [OK] Aufgabe `"$AufgabenName`" eingerichtet (alle 5 Minuten)" -ForegroundColor Green
    exit 0
}

# --- Hilfsfunktionen ----------------------------------------------------------
$LogOrdner = Join-Path $Ordner 'logs'
$LogDatei = Join-Path $LogOrdner 'docker-waechter.log'
$NeustartMerker = Join-Path $LogOrdner 'docker-waechter-letzter-neustart.txt'

function Log($text) {
    if (-not (Test-Path $LogOrdner)) { New-Item -ItemType Directory -Path $LogOrdner | Out-Null }
    # Log klein halten: ueber 1 MB nur die letzten 2000 Zeilen behalten
    if ((Test-Path $LogDatei) -and (Get-Item $LogDatei).Length -gt 1MB) {
        $rest = Get-Content $LogDatei -Tail 2000
        [IO.File]::WriteAllLines($LogDatei, $rest)
    }
    Add-Content -Path $LogDatei -Value ("{0}  {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $text) -Encoding UTF8
}

function Env-Wert($name, $standard) {
    $datei = Join-Path $Ordner '.env'
    if (-not (Test-Path $datei)) { return $standard }
    $zeile = Select-String -Path $datei -Pattern "^$name=(.*)$" | Select-Object -First 1
    if (-not $zeile) { return $standard }
    $wert = $zeile.Matches[0].Groups[1].Value.Trim().Trim('"').Trim("'")
    if ([string]::IsNullOrEmpty($wert)) { return $standard }
    return $wert
}

# Fuehrt docker mit Zeitlimit aus - ein haengendes Docker darf den Waechter
# nicht mit aufhaengen. Liefert die Ausgabe oder $null bei Fehler/Zeitablauf.
function Docker-Befehl([string]$argumente, [int]$sekunden = 30) {
    $start = New-Object Diagnostics.ProcessStartInfo 'docker', $argumente
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    try { $prozess = [Diagnostics.Process]::Start($start) } catch { return $null }
    $ausgabe = $prozess.StandardOutput.ReadToEndAsync()
    $null = $prozess.StandardError.ReadToEndAsync()
    if (-not $prozess.WaitForExit($sekunden * 1000)) {
        try { $prozess.Kill() } catch { }
        return $null
    }
    if ($prozess.ExitCode -ne 0) { return $null }
    return $ausgabe.Result.Trim()
}

function Erp-Antwortet {
    try {
        $antwort = Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 $HealthUrl
        return ($antwort.StatusCode -eq 200)
    } catch { return $false }
}

# Handy-Nachricht ueber dieselbe WEBHOOK_URL wie Sicherung und Nachtupdate
# (gemeinsam.sh). Ohne WEBHOOK_URL steht die Meldung nur im Log.
function Benachrichtigen([ValidateSet('dringend', 'leise')]$stufe, $titel, $text) {
    $url = Env-Wert 'WEBHOOK_URL' ''
    if (-not $url) { return }
    $kopf = @{}
    $token = Env-Wert 'WEBHOOK_TOKEN' ''
    if ($token) { $kopf['Authorization'] = "Bearer $token" }
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
        if ((Env-Wert 'WEBHOOK_FORMAT' 'ntfy') -eq 'json') {
            $inhalt = @{ text = "$titel`n$text" } | ConvertTo-Json -Compress
            Invoke-RestMethod -Method Post -Uri $url -Headers $kopf -ContentType 'application/json; charset=utf-8' `
                -Body ([Text.Encoding]::UTF8.GetBytes($inhalt)) -TimeoutSec 20 | Out-Null
        } else {
            # ntfy versteht Umlaute im Titel nur RFC-2047-kodiert
            $kopf['Title'] = '=?UTF-8?B?' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($titel)) + '?='
            if ($stufe -eq 'dringend') { $kopf['Priority'] = '5'; $kopf['Tags'] = 'rotating_light' }
            else { $kopf['Priority'] = '2'; $kopf['Tags'] = 'wrench' }
            Invoke-RestMethod -Method Post -Uri $url -Headers $kopf -ContentType 'text/plain; charset=utf-8' `
                -Body ([Text.Encoding]::UTF8.GetBytes($text)) -TimeoutSec 20 | Out-Null
        }
    } catch { Log "WARNUNG: Handy-Nachricht konnte nicht gesendet werden: $($_.Exception.Message)" }
}

# --- Pruefen --------------------------------------------------------------------
$adresse = Env-Wert 'APP_ADRESSE' '127.0.0.1'
if ($adresse -eq '0.0.0.0') { $adresse = '127.0.0.1' }
$HealthUrl = "http://$($adresse):$(Env-Wert 'APP_PORT' '8080')/actuator/health"
$Kunde = Env-Wert 'KUNDE_NAME' $env:COMPUTERNAME

if (Erp-Antwortet) { exit 0 }

# Kurze Aussetzer (z. B. Neustart der App) nicht gleich als Absturz werten
for ($i = 0; $i -lt 3; $i++) {
    Start-Sleep -Seconds 30
    if (Erp-Antwortet) { exit 0 }
}

if ($null -ne (Docker-Befehl 'info --format {{.ServerVersion}}' 60)) {
    $zustand = Docker-Befehl 'inspect --format "{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}" erp-app'
    if ($zustand -ne 'running healthy') {
        # Startet gerade, Nachtupdate laeuft oder bewusst gestoppt: Das regeln
        # Docker ("restart: always") und der Updater selbst.
        Log "ERP nicht erreichbar, Container erp-app: '$zustand' - kein Eingriff noetig."
        exit 0
    }
    $grund = 'Der ERP-Container laeuft gesund, ist von Windows aus aber nicht erreichbar (Port-Weiterleitung von Docker Desktop haengt).'
} else {
    if (-not (Test-Path $DockerExe)) { Log "Docker Desktop nicht gefunden ($DockerExe) - nichts zu tun."; exit 0 }
    $grund = 'Docker antwortet nicht (Docker Desktop abgestuerzt, beendet oder haengt).'
}

# --- Neustart (hoechstens alle 30 Minuten) ---------------------------------------
if ((Test-Path $NeustartMerker) -and ((Get-Date) - (Get-Item $NeustartMerker).LastWriteTime).TotalMinutes -lt 30) {
    Log "ERP weiter nicht erreichbar, aber Docker Desktop wurde vor weniger als 30 Minuten schon neu gestartet - warte. Grund: $grund"
    exit 0
}
if (-not (Test-Path $LogOrdner)) { New-Item -ItemType Directory -Path $LogOrdner | Out-Null }
Set-Content -Path $NeustartMerker -Value (Get-Date -Format 'o')

Log "Starte Docker Desktop neu. Grund: $grund"
if ($null -eq (Docker-Befehl 'desktop restart' 240)) {
    Log "'docker desktop restart' hat nicht geklappt - beende Docker Desktop und starte es neu."
    Get-Process -Name 'Docker Desktop', 'com.docker.backend' -ErrorAction SilentlyContinue |
        Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 15
    Start-Process $DockerExe
}

$bis = (Get-Date).AddMinutes(8)
while ((Get-Date) -lt $bis) {
    if (Erp-Antwortet) {
        Log 'ERP antwortet wieder.'
        Benachrichtigen leise "ERP war kurz nicht erreichbar - $Kunde" `
            "$grund Der Docker-Waechter hat Docker Desktop neu gestartet, das ERP laeuft wieder. Nichts zu tun."
        exit 0
    }
    Start-Sleep -Seconds 10
}
Log 'ERP antwortet auch nach dem Neustart nicht. Bitte von Hand nachsehen: docker compose ps'
Benachrichtigen dringend "ERP nicht erreichbar - $Kunde" `
    "$grund Ein automatischer Neustart von Docker Desktop hat nicht geholfen. Bitte am PC nachsehen (docker compose ps, Log: logs\docker-waechter.log)."
exit 1
