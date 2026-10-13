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
#      keine Verbindung zustande kommt (Port-Weiterleitung haengt).
# Er greift NICHT ein, wenn
#   - das ERP antwortet, auch mit einer Fehlerseite (dann geht die
#     Weiterleitung, das Problem liegt in der App),
#   - das ERP gerade startet, ein Nachtupdate laeuft oder es gestoppt wurde,
#   - der PC oder Docker Desktop erst vor weniger als 10 Minuten gestartet ist
#     (Docker Desktop braucht nach dem Anmelden ein paar Minuten).
# Hoechstens ein Neustart pro 30 Minuten; nach 2 erfolglosen Neustarts nur
# noch alle 4 Stunden und nur EINE dringende Handy-Nachricht.
#
# Aufgabe einrichten (macht Einrichten.cmd mit, kann gefahrlos wiederholt werden):
#   powershell -ExecutionPolicy Bypass -File docker-waechter.ps1 -Einrichten
# Abschalten: Aufgabenplanung > "ERP Handwerk Docker-Waechter" > Deaktivieren.
# Log: logs\docker-waechter.log
#
# Hinweis: Diese Datei bewusst nur mit ASCII-Zeichen (Windows PowerShell 5.1
# liest Dateien ohne BOM sonst mit falscher Kodierung). Umlaute in den
# Handy-Nachrichten daher ueber [char]-Codes.
# =============================================================================

param([switch]$Einrichten)

$ErrorActionPreference = 'Stop'
$Ordner = Split-Path -Parent $MyInvocation.MyCommand.Path
$AufgabenName = 'ERP Handwerk Docker-Waechter'
$StandardDockerExe = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
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

# Unerwartete Fehler sollen im Log landen, nicht still verschwinden. Der
# Exitcode 1 sagt auch einrichten-windows.ps1, dass etwas schiefging.
trap { Log "FEHLER: $_"; exit 1 }

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
        -StartWhenAvailable -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 30)
    try {
        Register-ScheduledTask -TaskName $AufgabenName -Action $aktion -Trigger $ausloeser -Principal $konto `
            -Settings $einstellungen -Description 'Startet Docker Desktop neu, wenn das ERP haengt (siehe docker-waechter.ps1).' `
            -Force | Out-Null
    } catch {
        Write-Host "    [!] Aufgabe nicht eingerichtet: $($_.Exception.Message)" -ForegroundColor Yellow
        exit 1
    }
    Write-Host "    [OK] Aufgabe `"$AufgabenName`" fuer $benutzer eingerichtet (alle 5 Minuten)" -ForegroundColor Green
    # Per "Als Administrator ausfuehren" mit einem anderen Konto eingerichtet,
    # liefe die Aufgabe nie - der Benutzer am Bildschirm ist ein anderer.
    try {
        $amBildschirm = (Get-CimInstance Win32_ComputerSystem).UserName
        if ($amBildschirm -and $amBildschirm -ne $benutzer) {
            Write-Host "    [!] Am Bildschirm ist $amBildschirm angemeldet. Der Waechter laeuft nur, wenn $benutzer angemeldet ist." -ForegroundColor Yellow
            Write-Host "        Einrichtung unter dem Benutzer wiederholen, unter dem Docker Desktop laeuft." -ForegroundColor Yellow
        }
    } catch { }
    exit 0
}

# --- Hilfsfunktionen ----------------------------------------------------------
$ae = [char]0x00E4; $ue = [char]0x00FC

# Wie env_wert in gemeinsam.sh und wie Docker Compose: Gross-/Kleinschreibung
# zaehlt, bei doppelten Schluesseln gilt der letzte, " # Kommentar" am Ende
# eines Werts ohne Anfuehrungszeichen gehoert nicht dazu.
function Env-Wert($name, $standard) {
    $datei = Join-Path $Ordner '.env'
    if (-not (Test-Path $datei)) { return $standard }
    $zeile = Select-String -Path $datei -Pattern "^$name=(.*)$" -CaseSensitive | Select-Object -Last 1
    if (-not $zeile) { return $standard }
    $wert = $zeile.Matches[0].Groups[1].Value.Trim()
    if ($wert -match '^"(.*)"$' -or $wert -match "^'(.*)'$") { $wert = $Matches[1] }
    else { $wert = ($wert -replace '\s+#.*$', '').Trim() }
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

# 'ok'      = ERP antwortet gesund
# 'antwort' = ERP antwortet, aber mit Fehler (z. B. 503) - Weiterleitung geht
# 'weg'     = keine Verbindung oder Zeitueberschreitung
function Erp-Zustand {
    try {
        $antwort = Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 $HealthUrl
        if ($antwort.StatusCode -eq 200) { return 'ok' }
        return 'antwort'
    } catch [Net.WebException] {
        if ($null -ne $_.Exception.Response) { return 'antwort' }
        return 'weg'
    } catch { return 'weg' }
}

# Handy-Nachricht ueber dieselbe WEBHOOK_URL wie Sicherung und Nachtupdate
# (gemeinsam.sh). Ohne WEBHOOK_URL steht die Meldung nur im Log.
function Benachrichtigen([ValidateSet('dringend', 'leise')]$stufe, $titel, $text) {
    $url = Env-Wert 'WEBHOOK_URL' ''
    if (-not $url) { return }
    if ($url -notmatch '^https://' -and $url -notmatch '^http://(127\.0\.0\.1|localhost)[:/]') {
        Log 'WARNUNG: WEBHOOK_URL ist nicht https - Nachrichten gehen unverschluesselt raus.'
    }
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

# Merker: Zeitpunkt des letzten Neustarts und Zahl der erfolglosen Neustarts
# seitdem, als "<zeit>|<fehlversuche>"
function Lies-Merker {
    if (-not (Test-Path $NeustartMerker)) { return $null }
    $teile = ([IO.File]::ReadAllText($NeustartMerker)).Trim() -split '\|'
    $zeit = [datetime]::MinValue
    if (-not [datetime]::TryParse($teile[0], [ref]$zeit)) { return $null }
    $fehlversuche = 0
    if ($teile.Count -gt 1) { [void][int]::TryParse($teile[1], [ref]$fehlversuche) }
    return @{ Zeit = $zeit; Fehlversuche = $fehlversuche }
}

function Schreibe-Merker([datetime]$zeit, [int]$fehlversuche) {
    if (-not (Test-Path $LogOrdner)) { New-Item -ItemType Directory -Path $LogOrdner | Out-Null }
    [IO.File]::WriteAllText($NeustartMerker, ('{0}|{1}' -f $zeit.ToString('o'), $fehlversuche))
}

# --- Einstellungen pruefen --------------------------------------------------------
# Im Zweifel nicht eingreifen: Mit einer kaputten Adresse galte das ERP nie als
# erreichbar, und der Waechter wuerde grundlos immer wieder neu starten.
$port = Env-Wert 'APP_PORT' '8080'
$adresse = Env-Wert 'APP_ADRESSE' '127.0.0.1'
$ip = $null
# TryParse nimmt auch Kurzformen wie "192.168.1" an - IPv4 daher vollstaendig verlangen
if ($port -notmatch '^\d{1,5}$' -or -not [Net.IPAddress]::TryParse($adresse, [ref]$ip) -or
    ($adresse -notmatch ':' -and $adresse -notmatch '^\d{1,3}(\.\d{1,3}){3}$')) {
    Log "APP_PORT ('$port') oder APP_ADRESSE ('$adresse') in der .env ist ungueltig - Waechter pausiert."
    exit 0
}
if ($ip.Equals([Net.IPAddress]::Any) -or $ip.Equals([Net.IPAddress]::IPv6Any)) { $adresse = '127.0.0.1' }
elseif ($ip.AddressFamily -eq [Net.Sockets.AddressFamily]::InterNetworkV6) { $adresse = "[$adresse]" }
$HealthUrl = "http://$($adresse):$port/actuator/health"
$Kunde = Env-Wert 'KUNDE_NAME' $env:COMPUTERNAME

# --- Pruefen --------------------------------------------------------------------
$merker = Lies-Merker
$zustand = Erp-Zustand
# Kurze Aussetzer (z. B. Neustart der App) nicht gleich als Absturz werten
for ($i = 0; $i -lt 3 -and $zustand -eq 'weg'; $i++) {
    Start-Sleep -Seconds 30
    $zustand = Erp-Zustand
}

if ($zustand -ne 'weg') {
    if ($merker -and $merker.Fehlversuche -gt 0) {
        # Jemand hat es von Hand repariert: Sperre fuer den naechsten Ausfall aufheben
        Log 'ERP antwortet wieder.'
        Schreibe-Merker $merker.Zeit 0
    }
    exit 0
}

$dockerProzess = Get-Process -Name 'Docker Desktop' -ErrorAction SilentlyContinue | Select-Object -First 1
$DockerExe = if ($dockerProzess -and $dockerProzess.Path) { $dockerProzess.Path } else { $StandardDockerExe }

# Nach dem Anmelden braucht Docker Desktop ein paar Minuten - nicht beim
# Hochfahren dazwischenfunken (auch nicht, wenn es sich gerade aktualisiert).
$frisch = (Get-Date).AddMinutes(-10)
$hochgefahren = (Get-CimInstance Win32_OperatingSystem).LastBootUpTime
# Nur der Hintergrunddienst zaehlt: Fenster von Docker Desktop (Electron)
# starten beim Oeffnen neue Prozesse mit demselben Namen.
$dienstStart = Get-Process -Name 'com.docker.backend' -ErrorAction SilentlyContinue |
    ForEach-Object { try { $_.StartTime } catch { } } | Sort-Object | Select-Object -First 1
if ($hochgefahren -gt $frisch -or ($dienstStart -and $dienstStart -gt $frisch)) {
    Log 'ERP nicht erreichbar, aber PC oder Docker Desktop startet gerade erst - kein Eingriff.'
    exit 0
}

if ($null -ne (Docker-Befehl 'info --format {{.ServerVersion}}' 60)) {
    $container = Docker-Befehl 'inspect --format "{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}" erp-app'
    if ($container -ne 'running healthy') {
        # Startet gerade, Nachtupdate laeuft oder bewusst gestoppt: Das regeln
        # Docker ("restart: always") und der Updater selbst.
        Log "ERP nicht erreichbar, Container erp-app: '$container' - kein Eingriff noetig."
        exit 0
    }
    $grund = 'Der ERP-Container laeuft gesund, ist von Windows aus aber nicht erreichbar (Port-Weiterleitung von Docker Desktop haengt).'
    $grundHandy = "Docker Desktop hing, das ERP war im Browser nicht erreichbar."
} else {
    $grund = 'Docker antwortet nicht (Docker Desktop abgestuerzt, beendet oder haengt).'
    $grundHandy = "Docker Desktop war abgest${ue}rzt oder hing, das ERP war nicht erreichbar."
}

if (-not (Test-Path $DockerExe)) {
    # Ohne Programmdatei koennten wir Docker Desktop zwar beenden, aber nicht
    # wieder starten - dann lieber gar nichts anfassen.
    Log "Docker Desktop nicht gefunden ($DockerExe) - kein Eingriff. Grund: $grund"
    if (-not $merker -or $merker.Fehlversuche -eq 0) {
        Schreibe-Merker (Get-Date) 1
        Benachrichtigen dringend "ERP nicht erreichbar - $Kunde" `
            "$grundHandy Docker Desktop wurde nicht gefunden und konnte nicht neu gestartet werden. Bitte am PC nachsehen."
    }
    exit 1
}

# --- Neustart (hoechstens alle 30 Minuten, nach 2 Fehlschlaegen alle 4 Stunden) ---
$sperreMinuten = if ($merker -and $merker.Fehlversuche -ge 2) { 240 } else { 30 }
if ($merker -and ((Get-Date) - $merker.Zeit).TotalMinutes -lt $sperreMinuten) {
    Log "ERP weiter nicht erreichbar, letzter Neustart vor weniger als $sperreMinuten Minuten - warte. Grund: $grund"
    exit 0
}
$fehlversuche = if ($merker) { $merker.Fehlversuche } else { 0 }
Schreibe-Merker (Get-Date) $fehlversuche

Log "Starte Docker Desktop neu. Grund: $grund"
if ($null -eq (Docker-Befehl 'desktop restart --timeout 240' 300)) {
    Log "'docker desktop restart' hat nicht geklappt - beende Docker Desktop und starte es neu."
    Get-Process -Name 'Docker Desktop', 'com.docker.backend' -ErrorAction SilentlyContinue |
        Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 15
    Start-Process $DockerExe
}

$bis = (Get-Date).AddMinutes(8)
while ((Get-Date) -lt $bis) {
    if ((Erp-Zustand) -ne 'weg') {
        Log 'ERP antwortet wieder.'
        Schreibe-Merker (Get-Date) 0
        Benachrichtigen leise "ERP war kurz nicht erreichbar - $Kunde" `
            "$grundHandy Der Docker-W${ae}chter hat Docker Desktop neu gestartet, das ERP l${ae}uft wieder. Nichts zu tun."
        exit 0
    }
    Start-Sleep -Seconds 10
}

$fehlversuche++
Schreibe-Merker (Get-Date) $fehlversuche
Log "ERP antwortet auch nach dem Neustart nicht ($fehlversuche. Fehlversuch). Bitte von Hand nachsehen: docker compose ps"
if ($fehlversuche -eq 1) {
    Benachrichtigen dringend "ERP nicht erreichbar - $Kunde" `
        "$grundHandy Ein automatischer Neustart von Docker Desktop hat nicht geholfen. Bitte am PC nachsehen (docker compose ps, Log: logs\docker-waechter.log). Weitere Versuche laufen, nach dem zweiten nur noch alle 4 Stunden."
}
exit 1
