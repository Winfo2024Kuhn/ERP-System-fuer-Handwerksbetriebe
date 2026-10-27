@echo off
title ERP - Dateien direkt oeffnen einrichten
rem Richtet das Hilfsprogramm fuer openfile://-Links aus dem ERP fuer diesen Windows-Benutzer ein.
rem Keine Administrator-Rechte noetig. Der PowerShell-Teil steht unten in dieser Datei.
set "ERP_EINRICHTUNG=%~f0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$t=[IO.File]::ReadAllText($env:ERP_EINRICHTUNG); $m='#'+'#POWERSHELL#'+'#'; $i=$t.LastIndexOf($m); if ($i -lt 0) { exit 9 }; Invoke-Expression $t.Substring($i + $m.Length)"
echo.
echo Diese Datei loescht sich jetzt selbst (sie kann das Passwort des Datei-Ordners enthalten).
echo Fuer einen weiteren PC bitte im ERP neu herunterladen.
pause
rem Selbst loeschen: (goto) beendet die Stapeldatei, del laeuft danach noch
(goto) 2>nul & del "%~f0"
exit /b

##POWERSHELL##
$ErrorActionPreference = 'Stop'
$launcherB64 = '@@LAUNCHER_BASE64@@'
$configB64 = '@@CONFIG_BASE64@@'
try {
  $ziel = Join-Path $env:LOCALAPPDATA 'OpenFileLauncher'
  New-Item -ItemType Directory -Path $ziel -Force | Out-Null
  $launcher = Join-Path $ziel 'launcher.ps1'
  [IO.File]::WriteAllBytes($launcher, [Convert]::FromBase64String($launcherB64))
  [IO.File]::WriteAllBytes((Join-Path $ziel 'config.json'), [Convert]::FromBase64String($configB64))

  # openfile:// fuer diesen Benutzer anmelden (hat Vorrang vor einer alten Installation fuer alle)
  $schluessel = 'HKCU:\Software\Classes\openfile'
  New-Item -Path "$schluessel\shell\open\command" -Force | Out-Null
  New-Item -Path "$schluessel\DefaultIcon" -Force | Out-Null
  Set-ItemProperty -Path $schluessel -Name '(default)' -Value 'URL:OpenFile Protocol'
  Set-ItemProperty -Path $schluessel -Name 'URL Protocol' -Value ''
  Set-ItemProperty -Path "$schluessel\DefaultIcon" -Name '(default)' -Value 'shell32.dll,0'
  $befehl = 'powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "' + $launcher + '" "%1"'
  Set-ItemProperty -Path "$schluessel\shell\open\command" -Name '(default)' -Value $befehl
@@ORDNER_ZUGANG@@
  Write-Host ''
  Write-Host 'Fertig! Excel-, HiCAD- und Tenado-Dateien aus dem ERP oeffnen sich jetzt direkt.' -ForegroundColor Green
  Write-Host 'Fragt der Browser beim ersten Klick, ob er den Link oeffnen darf: "Immer erlauben" anhaken.'
  Write-Host 'Das Hilfsprogramm haelt sich selbst aktuell. Dieses Fenster kann geschlossen werden.'
} catch {
  Write-Host ''
  Write-Host ('Einrichtung fehlgeschlagen: ' + $_.Exception.Message) -ForegroundColor Red
  exit 1
}
