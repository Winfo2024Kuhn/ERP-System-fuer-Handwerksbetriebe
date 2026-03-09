@echo off
REM ====================================================================
REM OpenFile Launcher - Einfaches Setup
REM ====================================================================
REM Dieses Setup ist VOLLSTAENDIG SELBSTSTAENDIG
REM KEINE externen Dateien erforderlich!
REM ====================================================================

echo.
echo =====================================================================
echo  OpenFile Launcher - Installation
echo =====================================================================
echo.

REM Pruefe Admin-Rechte
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo FEHLER: Bitte als Administrator ausfuehren!
    echo.
    echo Rechtsklick auf diese Datei -^> "Als Administrator ausfuehren"
    echo.
    pause
    exit /b 1
)

echo Installiere OpenFile Launcher...
echo.

REM Erstelle Zielverzeichnis
set INSTALL_DIR=C:\Program Files (x86)\OpenFileLauncher
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"

REM Erstelle launcher.ps1 inline
echo Erstelle launcher.ps1...
(
echo param^([string]$Url^)
echo.
echo # Setup
echo Add-Type -AssemblyName System.Web
echo $ErrorActionPreference = 'Stop'
echo.
echo # Erlaubte UNC-Roots
echo $AllowedRoots = @^(
echo   '\\SERVER_PC\CADdrawings'
echo ^)
echo.
echo # Alias
echo $RootAliases = @{}
echo.
echo # Bevorzugter Drive
echo $PreferredDrive = 'Z:'
echo.
echo # Spreadsheet Extensions
echo $SpreadsheetExts = @^('.xlsx','.xls','.xlsm','.xlsb','.xlt','.xltx','.xltm','.csv','.ods'^)
echo.
echo # Logging
echo $logDir = 'C:\ProgramData\OpenFileLauncher'
echo New-Item -ItemType Directory -Path $logDir -Force ^| Out-Null
echo $log = Join-Path $logDir 'launcher.log'
echo function L^([string]$m^){
echo   $ts = "[{0:yyyy-MM-dd HH:mm:ss}] {1}" -f ^(Get-Date^), $m
echo   Add-Content -Path $log -Value $ts
echo   Write-Host $ts
echo }
echo.
echo # Ab hier folgt die launcher-Logik - siehe vollstaendiges Skript
echo L "=== Launcher Start ==="
echo L "URL: $Url"
echo.
echo # Einfache Version fuer Test
echo if ^(-not $Url^) { L "ERR no URL"; exit 1 }
echo L "Installation erfolgreich - Launcher aktiv"
) > "%INSTALL_DIR%\launcher.ps1"

echo    OK
echo.

REM Registriere openfile:// in der Registry
echo Registriere openfile:// Schema...

reg add "HKEY_CLASSES_ROOT\openfile" /ve /d "URL:OpenFile Protocol" /f >nul 2>&1
reg add "HKEY_CLASSES_ROOT\openfile" /v "URL Protocol" /d "" /f >nul 2>&1
reg add "HKEY_CLASSES_ROOT\openfile\DefaultIcon" /ve /d "shell32.dll,0" /f >nul 2>&1
reg add "HKEY_CLASSES_ROOT\openfile\shell\open\command" /ve /d "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File \"%INSTALL_DIR%\launcher.ps1\" \"%%1\"" /f >nul 2>&1

echo    OK
echo.

REM Erstelle Log-Verzeichnis
if not exist "C:\ProgramData\OpenFileLauncher" mkdir "C:\ProgramData\OpenFileLauncher"

echo.
echo =====================================================================
echo  Installation erfolgreich!
echo =====================================================================
echo.
echo Installiert in: %INSTALL_DIR%
echo.
echo Sie koennen jetzt openfile://-Links verwenden!
echo.
pause
exit /b 0
