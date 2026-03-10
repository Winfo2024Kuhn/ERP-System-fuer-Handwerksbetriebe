@echo off
REM ====================================================================
REM OpenFileLauncher - Einfaches Installations-Setup Generator
REM ====================================================================
echo.
echo =====================================================================
echo  OpenFile Launcher - Setup-Generator (Einfachversion)
echo =====================================================================
echo.

if not exist "launcher.ps1" (
    echo FEHLER: launcher.ps1 nicht gefunden!
    pause
    exit /b 1
)

echo [1/2] Erstelle Installer mit eingebettetem launcher.ps1...

REM Lese launcher.ps1 und konvertiere zu Base64
powershell.exe -NoProfile -Command "$content = Get-Content 'launcher.ps1' -Raw; $bytes = [System.Text.Encoding]::UTF8.GetBytes($content); $base64 = [Convert]::ToBase64String($bytes); $template = Get-Content 'OpenFileLauncher-Installer.ps1' -Raw; $final = $template -replace 'INSERT_LAUNCHER_BASE64_HERE', $base64; Set-Content 'OpenFileLauncher-Setup.ps1' -Value $final -Encoding UTF8"

if not exist "OpenFileLauncher-Setup.ps1" (
    echo FEHLER: Setup-Skript konnte nicht erstellt werden!
    pause
    exit /b 1
)

echo    OK - OpenFileLauncher-Setup.ps1 erstellt
echo.
echo [2/2] Fertig!
echo.
echo =====================================================================
echo  Setup erfolgreich erstellt!
echo =====================================================================
echo.
echo Datei: OpenFileLauncher-Setup.ps1
echo.
echo VERTEILUNG AN CLIENTS:
echo ---------------------
echo 1. Kopieren Sie "OpenFileLauncher-Setup.ps1" auf den Client
echo 2. Client fuehrt aus: Rechtsklick -^> "Mit PowerShell ausfuehren"
echo    ODER: Rechtsklick -^> "Als Administrator ausfuehren"
echo.
echo HINWEIS: Dies ist eine .ps1-Datei, keine .exe
echo Fuer eine echte .exe benoetigen Sie Inno Setup oder ps2exe
echo.
pause
