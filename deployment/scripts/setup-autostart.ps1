# Einmalig als Administrator ausfuehren!
# Richtet den Autostart-Task "Kalkulationsprogramm - Auto Start" ein.
# Verwendet denselben Task-Namen wie install-scheduled-tasks.ps1 um Konflikte/Doppelstarts zu vermeiden.

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "[FEHLER] Dieses Skript muss als Administrator ausgefuehrt werden." -ForegroundColor Red
    Write-Host "Rechtsklick auf SETUP-AUTOSTART.bat -> 'Als Administrator ausfuehren'" -ForegroundColor Yellow
    pause
    exit 1
}

$taskName   = "Kalkulationsprogramm - Auto Start"
$scriptDir  = "C:\Kalkulationsprogramm\scripts"
$startScript = Join-Path $scriptDir "start-kalkulationsprogramm.ps1"

# Start-Skript aus diesem Ordner nach C:\Kalkulationsprogramm\scripts kopieren
# (UPDATE.bat kopiert nur die JAR, keine Skripte). Die Aufgabe startet von dort,
# damit sie nicht vom Repository-Ordner abhaengt.
$startScriptSource = Join-Path $PSScriptRoot "start-kalkulationsprogramm.ps1"
if (-not (Test-Path $startScriptSource)) {
    Write-Host "[FEHLER] start-kalkulationsprogramm.ps1 nicht gefunden neben diesem Skript: $PSScriptRoot" -ForegroundColor Red
    pause
    exit 1
}
if (-not (Test-Path $scriptDir)) {
    New-Item -ItemType Directory -Path $scriptDir -Force | Out-Null
}
# Laeuft dieses Skript schon aus C:\Kalkulationsprogramm\scripts, liegt die Datei bereits dort
if ((Resolve-Path $startScriptSource).Path -ne (Join-Path (Resolve-Path $scriptDir).Path "start-kalkulationsprogramm.ps1")) {
    Copy-Item -Path $startScriptSource -Destination $startScript -Force -ErrorAction Stop
    Write-Host "  OK Start-Skript kopiert nach $startScript" -ForegroundColor Green
}

# Alten Task mit gleichem Namen entfernen (idempotent)
$existingTask = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if ($existingTask) {
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
    Write-Host "  INFO Alter Task '$taskName' entfernt" -ForegroundColor Gray
}

$action = New-ScheduledTaskAction `
    -Execute "powershell.exe" `
    -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$startScript`"" `
    -WorkingDirectory "C:\Kalkulationsprogramm"

$trigger = New-ScheduledTaskTrigger -AtStartup
$trigger.Delay = "PT2M"  # 2 Minuten warten bis Netzwerk + Datenbank oben sind

$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 30) `
    -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 5)

$principal = New-ScheduledTaskPrincipal `
    -UserId "SYSTEM" `
    -LogonType ServiceAccount `
    -RunLevel Highest

Register-ScheduledTask `
    -TaskName $taskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Principal $principal `
    -Description "Startet den Kalkulationsprogramm-Server automatisch beim System-Start (2 Min. Verzoegerung)" `
    -Force | Out-Null

Write-Host "[OK] Autostart-Task '$taskName' wurde eingerichtet." -ForegroundColor Green
Write-Host "     Zeitplan: Bei System-Start, 2 Minuten Verzoegerung" -ForegroundColor Gray
Write-Host "     Der Server startet ab sofort automatisch nach jedem Reboot." -ForegroundColor Green
pause
