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
$startScript = "C:\Kalkulationsprogramm\scripts\start-kalkulationsprogramm.ps1"

if (-not (Test-Path $startScript)) {
    Write-Host "[FEHLER] Ziel-Skript nicht gefunden: $startScript" -ForegroundColor Red
    Write-Host "Bitte zuerst UPDATE.bat ausfuehren, damit die Skripte nach C:\Kalkulationsprogramm kopiert werden." -ForegroundColor Yellow
    pause
    exit 1
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
