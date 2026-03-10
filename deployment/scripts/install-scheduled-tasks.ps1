# =========================================================
# Installation Script fuer Scheduled Tasks
# Kalkulationsprogramm
# =========================================================
# Erstellt alle notwendigen Windows Scheduled Tasks fuer:
# - Automatische Datenbank-Backups
# - Automatischen Server-Start beim Boot
# - Optionaler woechentlicher Server-Neustart
# =========================================================
# WICHTIG: Dieses Script muss als Administrator ausgefuehrt werden!
# =========================================================

param(
    [switch]$EnableWeeklyRestart = $false,
    [string]$DeploymentPath = "C:\Kalkulationsprogramm"
)

# Pruefe Administrator-Rechte
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "FEHLER: Dieses Script muss als Administrator ausgefuehrt werden!" -ForegroundColor Red
    Write-Host "Bitte starten Sie PowerShell als Administrator und fuehren Sie das Script erneut aus." -ForegroundColor Yellow
    exit 1
}

Write-Host "========================================"  -ForegroundColor Cyan
Write-Host "Kalkulationsprogramm - Task Installation" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Skript-Pfade (im Repository - wir sind bereits in deployment/scripts/)
$backupScriptSource = Join-Path $PSScriptRoot "backup-database.ps1"
$startScriptSource = Join-Path $PSScriptRoot "start-kalkulationsprogramm.ps1"
$restartScriptSource = Join-Path $PSScriptRoot "restart-kalkulationsprogramm.ps1"

# Deployment-Pfade
$deploymentScriptDir = Join-Path $DeploymentPath "scripts"

# =========================================================
# Funktionen
# =========================================================

function Copy-ScriptsToDeployment {
    Write-Host "[1/6] Kopiere Scripts zum Deployment-Verzeichnis..." -ForegroundColor Yellow
    
    # Erstelle Scripts-Verzeichnis
    if (-not (Test-Path $deploymentScriptDir)) {
        New-Item -ItemType Directory -Path $deploymentScriptDir -Force | Out-Null
        Write-Host "  OK Verzeichnis erstellt: $deploymentScriptDir" -ForegroundColor Green
    }
    
    # Kopiere Scripts
    $scripts = @(
        @{Source = $backupScriptSource; Name = "backup-database.ps1"},
        @{Source = $startScriptSource; Name = "start-kalkulationsprogramm.ps1"},
        @{Source = $restartScriptSource; Name = "restart-kalkulationsprogramm.ps1"}
    )
    
    foreach ($script in $scripts) {
        $destination = Join-Path $deploymentScriptDir $script.Name
        if (Test-Path $script.Source) {
            Copy-Item -Path $script.Source -Destination $destination -Force
            Write-Host "  OK Kopiert: $($script.Name)" -ForegroundColor Green
        }
        else {
            Write-Host "  X WARNUNG: Script nicht gefunden: $($script.Source)" -ForegroundColor Red
        }
    }
    
    Write-Host ""
}

function Remove-ExistingTask {
    param([string]$TaskName)
    
    $task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    if ($task) {
        Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
        Write-Host "  INFO Alter Task '$TaskName' entfernt" -ForegroundColor Gray
    }
}

function Create-BackupTask {
    Write-Host "[2/6] Erstelle Backup-Task..." -ForegroundColor Yellow
    
    $taskName = "Kalkulationsprogramm - Database Backup"
    Remove-ExistingTask -TaskName $taskName
    
    # Task-Action
    $backupScript = Join-Path $deploymentScriptDir "backup-database.ps1"
    $action = New-ScheduledTaskAction -Execute "PowerShell.exe" `
        -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$backupScript`""
    
    # Task-Trigger: Taeglich um 02:00 Uhr
    $trigger = New-ScheduledTaskTrigger -Daily -At "02:00"
    
    # Task-Settings
    $settings = New-ScheduledTaskSettingsSet `
        -AllowStartIfOnBatteries `
        -DontStopIfGoingOnBatteries `
        -StartWhenAvailable `
        -RunOnlyIfNetworkAvailable `
        -ExecutionTimeLimit (New-TimeSpan -Hours 2)
    
    # Task-Principal (als SYSTEM ausfuehren)
    $principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
    
    # Task registrieren
    Register-ScheduledTask -TaskName $taskName `
        -Action $action `
        -Trigger $trigger `
        -Settings $settings `
        -Principal $principal `
        -Description "Erstellt taeglich um 02:00 Uhr ein Backup der Kalkulationsprogramm-Datenbank" | Out-Null
    
    Write-Host "  OK Task erstellt: $taskName" -ForegroundColor Green
    Write-Host "    Zeitplan: Taeglich um 02:00 Uhr" -ForegroundColor Gray
    Write-Host ""
}

function Create-AutoStartTask {
    Write-Host "[3/6] Erstelle Autostart-Task..." -ForegroundColor Yellow
    
    $taskName = "Kalkulationsprogramm - Auto Start"
    Remove-ExistingTask -TaskName $taskName
    
    # Task-Action
    $startScript = Join-Path $deploymentScriptDir "start-kalkulationsprogramm.ps1"
    $action = New-ScheduledTaskAction -Execute "PowerShell.exe" `
        -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$startScript`""
    
    # Task-Trigger: Bei System-Start, mit 2 Minuten Verzoegerung
    $trigger = New-ScheduledTaskTrigger -AtStartup
    $trigger.Delay = "PT2M"  # 2 Minuten Verzoegerung
    
    # Task-Settings
    $settings = New-ScheduledTaskSettingsSet `
        -AllowStartIfOnBatteries `
        -DontStopIfGoingOnBatteries `
        -StartWhenAvailable `
        -ExecutionTimeLimit (New-TimeSpan -Minutes 30)
    
    # Task-Principal
    $principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
    
    # Task registrieren
    Register-ScheduledTask -TaskName $taskName `
        -Action $action `
        -Trigger $trigger `
        -Settings $settings `
        -Principal $principal `
        -Description "Startet den Kalkulationsprogramm-Server automatisch beim System-Start" | Out-Null
    
    Write-Host "  OK Task erstellt: $taskName" -ForegroundColor Green
    Write-Host "    Zeitplan: Bei System-Start (2 Min. Verzoegerung)" -ForegroundColor Gray
    Write-Host ""
}

function Create-WeeklyRestartTask {
    Write-Host "[4/6] Erstelle woechentlichen Neustart-Task..." -ForegroundColor Yellow
    
    $taskName = "Kalkulationsprogramm - Weekly Restart"
    
    if (-not $EnableWeeklyRestart) {
        # Entferne Task falls vorhanden
        $task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
        if ($task) {
            Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
            Write-Host "  INFO Woechentlicher Neustart deaktiviert (Task entfernt)" -ForegroundColor Gray
        }
        else {
            Write-Host "  INFO Woechentlicher Neustart nicht aktiviert" -ForegroundColor Gray
        }
        Write-Host ""
        return
    }
    
    Remove-ExistingTask -TaskName $taskName
    
    # Task-Action
    $restartScript = Join-Path $deploymentScriptDir "restart-kalkulationsprogramm.ps1"
    $action = New-ScheduledTaskAction -Execute "PowerShell.exe" `
        -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$restartScript`""
    
    # Task-Trigger: Woechentlich Sonntags um 03:00 Uhr
    $trigger = New-ScheduledTaskTrigger -Weekly -DaysOfWeek Sunday -At "03:00"
    
    # Task-Settings
    $settings = New-ScheduledTaskSettingsSet `
        -AllowStartIfOnBatteries `
        -DontStopIfGoingOnBatteries `
        -StartWhenAvailable `
        -ExecutionTimeLimit (New-TimeSpan -Hours 1)
    
    # Task-Principal
    $principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
    
    # Task registrieren
    Register-ScheduledTask -TaskName $taskName `
        -Action $action `
        -Trigger $trigger `
        -Settings $settings `
        -Principal $principal `
        -Description "Startet den Kalkulationsprogramm-Server woechentlich neu (Wartung)" | Out-Null
    
    Write-Host "  OK Task erstellt: $taskName" -ForegroundColor Green
    Write-Host "    Zeitplan: Woechentlich Sonntags um 03:00 Uhr" -ForegroundColor Gray
    Write-Host ""
}

function Verify-Tasks {
    Write-Host "[5/6] Ueberpruefe erstellte Tasks..." -ForegroundColor Yellow
    
    $expectedTasks = @(
        "Kalkulationsprogramm - Database Backup",
        "Kalkulationsprogramm - Auto Start"
    )
    
    if ($EnableWeeklyRestart) {
        $expectedTasks += "Kalkulationsprogramm - Weekly Restart"
    }
    
    $allOk = $true
    foreach ($taskName in $expectedTasks) {
        $task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
        if ($task) {
            Write-Host "  OK $taskName" -ForegroundColor Green
        }
        else {
            Write-Host "  X $taskName - FEHLT!" -ForegroundColor Red
            $allOk = $false
        }
    }
    
    Write-Host ""
    return $allOk
}

function Show-Summary {
    Write-Host "[6/6] Installation abgeschlossen!" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "========================================"  -ForegroundColor Cyan
    Write-Host "Zusammenfassung" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    
    Write-Host "Installierte Tasks:" -ForegroundColor White
    Write-Host "  1. Database Backup   - Taeglich um 02:00 Uhr" -ForegroundColor Gray
    Write-Host "  2. Auto Start        - Bei System-Start" -ForegroundColor Gray
    if ($EnableWeeklyRestart) {
        Write-Host "  3. Weekly Restart    - Sonntags um 03:00 Uhr" -ForegroundColor Gray
    }
    Write-Host ""
    
    Write-Host "Script-Speicherort:" -ForegroundColor White
    Write-Host "  $deploymentScriptDir" -ForegroundColor Gray
    Write-Host ""
    
    Write-Host "Backup-Speicherort:" -ForegroundColor White
    Write-Host "  E:\Kalkulationsprogramm\Backups\" -ForegroundColor Gray
    Write-Host ""
    
    Write-Host "Naechste Schritte:" -ForegroundColor White
    Write-Host "  1. Kopieren Sie die JAR-Datei nach: $DeploymentPath\" -ForegroundColor Gray
    Write-Host "  2. Benennen Sie sie um zu: Kalkulationsprogramm.jar" -ForegroundColor Gray
    Write-Host "  3. Stellen Sie sicher, dass die externe Festplatte E:\ verfuegbar ist" -ForegroundColor Gray
    Write-Host "  4. Installieren Sie MySQL Client (fuer mysqldump)" -ForegroundColor Gray
    Write-Host ""
    
    Write-Host "Tasks manuell verwalten:" -ForegroundColor White
    Write-Host "  - Task Scheduler oeffnen: taskschd.msc" -ForegroundColor Gray
    Write-Host "  - Tasks anzeigen: Get-ScheduledTask | Where-Object {`$_.TaskName -like '*Kalkulationsprogramm*'}" -ForegroundColor Gray
    Write-Host ""
    
    Write-Host "Backup manuell testen:" -ForegroundColor White
    Write-Host "  Rufen Sie auf: $deploymentScriptDir\backup-database.ps1" -ForegroundColor Gray
    Write-Host ""
    
    Write-Host "========================================" -ForegroundColor Cyan
}

# =========================================================
# Hauptprogramm
# =========================================================

try {
    Copy-ScriptsToDeployment
    Create-BackupTask
    Create-AutoStartTask
    Create-WeeklyRestartTask
    
    $verified = Verify-Tasks
    
    Show-Summary
    
    if ($verified) {
        Write-Host "OK Alle Tasks erfolgreich installiert!" -ForegroundColor Green
        exit 0
    }
    else {
        Write-Host "WARNUNG Einige Tasks konnten nicht installiert werden!" -ForegroundColor Yellow
        exit 1
    }
}
catch {
    Write-Host ""
    Write-Host "FEHLER bei der Installation: $_" -ForegroundColor Red
    Write-Host ""
    exit 1
}
