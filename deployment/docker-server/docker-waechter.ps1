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
# Ausserdem haelt er die Sicherungsziele erreichbar (siehe "Sicherungsziele"
# unten): Sieht Windows die externe Festplatte, der Updater aber nicht mehr,
# startet er Docker Desktop kurz vor der Sicherung neu (15 bis 60 Minuten vorher).
#
# Aufgabe einrichten (macht Einrichten.cmd mit, kann gefahrlos wiederholt werden):
#   powershell -ExecutionPolicy Bypass -File docker-waechter.ps1 -Einrichten
# Nur pruefen, nichts neu starten, keine Handy-Nachricht (zum Ausprobieren):
#   powershell -ExecutionPolicy Bypass -File docker-waechter.ps1 -Probelauf
# Abschalten: Aufgabenplanung > "ERP Handwerk Docker-Waechter" > Deaktivieren.
# Log: logs\docker-waechter.log
#
# Hinweis: Diese Datei bewusst nur mit ASCII-Zeichen (Windows PowerShell 5.1
# liest Dateien ohne BOM sonst mit falscher Kodierung). Umlaute in den
# Handy-Nachrichten daher ueber [char]-Codes.
# =============================================================================

# -UpdaterContainer nur zum Testen (anderer Container als erp-updater)
param([switch]$Einrichten, [switch]$Probelauf, [string]$UpdaterContainer = 'erp-updater')

$ErrorActionPreference = 'Stop'
$Ordner = Split-Path -Parent $MyInvocation.MyCommand.Path
$AufgabenName = 'ERP Handwerk Docker-Waechter'
$StandardDockerExe = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
$LogOrdner = Join-Path $Ordner 'logs'
$LogDatei = Join-Path $LogOrdner 'docker-waechter.log'
$NeustartMerker = Join-Path $LogOrdner 'docker-waechter-letzter-neustart.txt'
# Lebenszeichen fuer sicherung.sh (sieht den Ordner als /erp/logs): Laeuft der
# Waechter, verspricht die Meldung "wird von selbst wieder eingebunden"
$Lebenszeichen = Join-Path $LogOrdner 'docker-waechter-lebt.txt'
$ZielMerker = Join-Path $LogOrdner 'docker-waechter-sicherungsziel.txt'
$ZielNeustartMerker = Join-Path $LogOrdner 'docker-waechter-sicherungsziel-neustart.txt'
$Markierung = 'ERP-SICHERUNGSZIEL.txt'

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
    if ($Probelauf) { Log "PROBELAUF: Handy-Nachricht ($stufe) '$titel' wuerde gesendet."; return }
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

# Zeitpunkt aus einer Merker-Datei (erste Zeile) oder $null
function Lies-Zeit($datei) {
    if (-not (Test-Path $datei)) { return $null }
    $zeit = [datetime]::MinValue
    if ([datetime]::TryParse(([IO.File]::ReadAllText($datei)).Trim(), [ref]$zeit)) { return $zeit }
    return $null
}

function Schreibe-Zeit($datei) {
    if (-not (Test-Path $LogOrdner)) { New-Item -ItemType Directory -Path $LogOrdner | Out-Null }
    [IO.File]::WriteAllText($datei, (Get-Date).ToString('o'))
}

# Docker Desktop neu starten; klappt das nicht, hart beenden und neu starten
function Docker-Neustarten {
    if ($Probelauf) { Log 'PROBELAUF: Docker Desktop wuerde jetzt neu gestartet.'; return }
    if ($null -eq (Docker-Befehl 'desktop restart --timeout 240' 300)) {
        Log "'docker desktop restart' hat nicht geklappt - beende Docker Desktop und starte es neu."
        Get-Process -Name 'Docker Desktop', 'com.docker.backend' -ErrorAction SilentlyContinue |
            Stop-Process -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 15
        Start-Process $DockerExe
    }
}

# --- Sicherungsziele -------------------------------------------------------------
# Verschwindet ein Sicherungsziel kurz (Windows schaltet die USB-Platte beim
# Energiesparen ab, sie wird ab- und wieder angesteckt ...), ist es unter
# Windows danach wieder da. Docker Desktop haelt aber an der alten, toten
# Verbindung fest, bis es selbst neu startet: Die Sicherung meldet jede Nacht
# "nur teilweise" (so geschehen am 18. und 19.10.2026). Ein Neustart nur des
# Updaters hilft nicht - er scheitert und laesst den Updater gestoppt zurueck.
# Gleiches gilt fuer eine Platte, die beim Start von Docker noch fehlte.
#
# Darum: Sieht Windows die Markierung eines Ziels, der Updater aber nicht,
# startet der Waechter Docker Desktop neu. Nur 15 bis 60 Minuten vor der Sicherung
# (SICHERUNG_ZEITPLAN, nachts - das ERP ist dabei 1-2 Minuten weg), nicht
# waehrend eines Nachtupdates und hoechstens einmal in 12 Stunden.

# /run/desktop/mnt/host/f/Ordner -> F:\Ordner; $null fuer andere Pfade
function Windows-Pfad($dockerPfad) {
    if ($dockerPfad -notmatch '^/run/desktop/mnt/host/([a-zA-Z])(/.*)?$') { return $null }
    $rest = if ($Matches[2]) { $Matches[2] -replace '/', '\' } else { '\' }
    return $Matches[1].ToUpper() + ':' + $rest
}

# "<Status>|<Fehler>" des Updaters; $null, wenn Docker oder der Updater nicht
# antwortet (dann ist unbekannt, ob die Ziele erreichbar sind)
function Updater-Zustand {
    return Docker-Befehl "inspect --format ""{{.State.Status}}|{{.State.Error}}"" $UpdaterContainer"
}

# Namen der Ziele, die Windows sieht, der Updater aber nicht
# Abgerissene-Ziele <Ergebnis von Updater-Zustand>
function Abgerissene-Ziele([string]$zustand) {
    $status, $fehler = $zustand -split '\|', 2
    $umgebung = Docker-Befehl "inspect --format ""{{range .Config.Env}}{{println .}}{{end}}"" $UpdaterContainer"
    $umgebung = if ($umgebung) { @($umgebung -split "`r?`n") } else { @() }
    $abgerissen = @()
    $ziele = @(
        @{ Name = 'Externe Festplatte'; Env = 'SICHERUNG_PLATTE'; Innen = '/sicherungsziele/platte' },
        @{ Name = 'Cloud-Ordner'; Env = 'SICHERUNG_CLOUD'; Innen = '/sicherungsziele/cloud' })
    foreach ($ziel in $ziele) {
        $eingetragen = Env-Wert $ziel.Env ''
        $windows = Windows-Pfad $eingetragen
        if (-not $windows) { continue }
        # Auch unter Windows weg (Platte nicht angesteckt): Das meldet die
        # Sicherung, ein Neustart braechte nichts
        # Nicht Join-Path: Das scheitert, wenn es das Laufwerk gerade nicht gibt
        if (-not (Test-Path -LiteralPath ($windows.TrimEnd('\') + '\' + $Markierung))) { continue }
        # Neu eingetragen, aber noch nicht eingebunden: hilft nur
        # "docker compose up -d updater", das meldet die Sicherung
        if ($umgebung -cnotcontains "$($ziel.Env)_EINGEBUNDEN=$eingetragen") { continue }
        if ($status -eq 'running') {
            if ($null -eq (Docker-Befehl "exec $UpdaterContainer test -f $($ziel.Innen)/$Markierung")) {
                $abgerissen += $ziel.Name
            }
        } elseif ($fehler -and $fehler.Contains($eingetragen)) {
            # Updater startet nicht mehr: "error while creating mount source path ..."
            $abgerissen += $ziel.Name
        }
    }
    return $abgerissen
}

# Minuten bis zur naechsten Sicherung laut SICHERUNG_ZEITPLAN ("M H * * *");
# $null, wenn der Zeitplan anders aussieht (dann lieber nicht eingreifen)
function Minuten-Bis-Sicherung {
    $plan = Env-Wert 'SICHERUNG_ZEITPLAN' '0 2 * * *'
    if ($plan -notmatch '^(\d{1,2}) (\d{1,2}) \* \* \*$') { return $null }
    $minute = [int]$Matches[1]; $stunde = [int]$Matches[2]
    if ($minute -gt 59 -or $stunde -gt 23) { return $null }
    $jetzt = Get-Date
    $naechste = $jetzt.Date.AddHours($stunde).AddMinutes($minute)
    if ($naechste -le $jetzt) { $naechste = $naechste.AddDays(1) }
    return ($naechste - $jetzt).TotalMinutes
}

function Sicherungsziele-Pruefen {
    $zustand = Updater-Zustand
    if ($null -eq $zustand) { return }
    $abgerissen = @(Abgerissene-Ziele $zustand)
    if ($abgerissen.Count -eq 0) {
        if (Test-Path $ZielMerker) {
            Log 'Sicherungsziele sind wieder erreichbar.'
            Remove-Item $ZielMerker -Force
        }
        return
    }
    $namen = $abgerissen -join ' und '
    $minuten = Minuten-Bis-Sicherung
    if (-not (Test-Path $ZielMerker)) {
        if (-not $Probelauf) { Schreibe-Zeit $ZielMerker }
        if ($null -eq $minuten) {
            Log "Docker kommt nicht mehr an: $namen (unter Windows vorhanden). SICHERUNG_ZEITPLAN ist kein einfaches 'Minute Stunde * * *' - kein automatischer Neustart, bitte Docker Desktop von Hand neu starten."
        } else {
            Log "Docker kommt nicht mehr an: $namen (unter Windows vorhanden). Docker Desktop wird 15 bis 60 Minuten vor der naechsten Sicherung neu gestartet."
        }
    }
    # Spaetestens 15 Minuten vorher: Neustart von Docker Desktop und Updater
    # dauern ein paar Minuten, sonst fiele die Sicherung ganz aus
    if ($null -eq $minuten -or $minuten -lt 15 -or $minuten -gt 60) { return }
    $letzter = Lies-Zeit $ZielNeustartMerker
    if ($letzter -and ((Get-Date) - $letzter).TotalHours -lt 12) { return }
    if ($null -ne (Docker-Befehl "exec $UpdaterContainer pgrep -f nachtupdate.sh")) {
        Log 'Sicherungsziel abgerissen, aber gerade laeuft ein Nachtupdate - warte.'
        return
    }
    if (-not (Test-Path $DockerExe)) {
        Log "Sicherungsziel abgerissen, Docker Desktop aber nicht gefunden ($DockerExe) - kein Eingriff."
        return
    }

    Log "Starte Docker Desktop neu, damit die Sicherung wieder an $namen kommt."
    # Ein Probelauf darf den echten Neustart dieser Nacht nicht sperren
    if ($Probelauf) { Docker-Neustarten; return }
    Schreibe-Zeit $ZielNeustartMerker
    Docker-Neustarten
    $bis = (Get-Date).AddMinutes(8)
    while ((Get-Date) -lt $bis) {
        Start-Sleep -Seconds 15
        $zustand = Updater-Zustand
        if ((Erp-Zustand) -eq 'ok' -and $null -ne $zustand -and @(Abgerissene-Ziele $zustand).Count -eq 0) {
            Log "$namen wieder eingebunden."
            Remove-Item $ZielMerker -Force -ErrorAction SilentlyContinue
            Benachrichtigen leise "ERP-Sicherung: $namen wieder verbunden - $Kunde" `
                "Die Verbindung von Docker zu: $namen war abgerissen (z. B. nach dem Energiesparen). Der Docker-W${ae}chter hat Docker Desktop neu gestartet, die Sicherung heute Nacht l${ae}uft normal. Nichts zu tun."
            return
        }
    }
    Log "$namen auch nach dem Neustart von Docker Desktop nicht erreichbar. Bitte den PC neu starten."
    Benachrichtigen dringend "ERP-Sicherung: $namen nicht erreichbar - $Kunde" `
        "Unter Windows ist $namen da, Docker kommt aber nicht heran. Auch ein Neustart von Docker Desktop hat nicht geholfen. Bitte den PC neu starten, sonst sichert die Nacht nur teilweise."
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
if (-not $Probelauf) {
    if (-not (Test-Path $LogOrdner)) { New-Item -ItemType Directory -Path $LogOrdner | Out-Null }
    [IO.File]::WriteAllText($Lebenszeichen, (Get-Date).ToString('o'))
}
$dockerProzess = Get-Process -Name 'Docker Desktop' -ErrorAction SilentlyContinue | Select-Object -First 1
$DockerExe = if ($dockerProzess -and $dockerProzess.Path) { $dockerProzess.Path } else { $StandardDockerExe }

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
    # Nur bei gesundem ERP: Antwortet es mit Fehler, startet es vielleicht
    # gerade neu (Nachtupdate) - dann nicht dazwischenfunken
    if ($zustand -eq 'ok') { Sicherungsziele-Pruefen }
    exit 0
}

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
Log "Starte Docker Desktop neu. Grund: $grund"
# Ein Probelauf darf den echten Waechter nicht sperren
if ($Probelauf) { Docker-Neustarten; exit 0 }
$fehlversuche = if ($merker) { $merker.Fehlversuche } else { 0 }
Schreibe-Merker (Get-Date) $fehlversuche
Docker-Neustarten

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
