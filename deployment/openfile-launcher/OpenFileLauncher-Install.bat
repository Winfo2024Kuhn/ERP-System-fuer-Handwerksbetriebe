@echo off
REM ============================================================================
REM OpenFile Launcher - Einfaches Installations-Script
REM ============================================================================
REM Dieses Script installiert den OpenFile Launcher, der es ermoeglicht,
REM CAD-Zeichnungen und Excel-Dateien direkt aus der Web-Anwendung zu oeffnen.
REM
REM WICHTIG: Dieses Script muss als Administrator ausgefuehrt werden!
REM ============================================================================

echo.
echo ========================================================
echo   OpenFile Launcher - Installation
echo ========================================================
echo.
echo Dieses Programm installiert den OpenFile Launcher auf
echo Ihrem Computer, damit Sie CAD-Zeichnungen (.sza, .tcd)
echo und Excel-Dateien direkt aus der Web-Anwendung oeffnen
echo koennen.
echo.
echo WICHTIG: Administrator-Rechte erforderlich!
echo.
pause

REM Pruefe Admin-Rechte
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo.
    echo [!] Dieses Script benoetigt Administrator-Rechte!
    echo.
    echo Bitte:
    echo   1. Rechtsklick auf diese Datei
    echo   2. "Als Administrator ausfuehren" waehlen
    echo.
    pause
    exit /b 1
)

REM Hole Pfad des Scripts
set "SCRIPT_DIR=%~dp0"
set "PS_SCRIPT=%SCRIPT_DIR%OpenFileLauncher-Setup.ps1"

REM Pruefe ob PowerShell-Script existiert
if not exist "%PS_SCRIPT%" (
    echo.
    echo [FEHLER] Setup-Script nicht gefunden: %PS_SCRIPT%
    echo.
    echo Bitte laden Sie das vollstaendige Setup-Paket herunter.
    echo.
    pause
    exit /b 1
)

echo.
echo [*] Starte Installation...
echo.

REM Fuehre PowerShell-Script aus
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS_SCRIPT%"

if %errorLevel% equ 0 (
    echo.
    echo ========================================================
    echo   Installation erfolgreich!
    echo ========================================================
    echo.
    echo Sie koennen jetzt CAD-Dateien direkt aus der
    echo Web-Anwendung oeffnen.
    echo.
) else (
    echo.
    echo [FEHLER] Installation fehlgeschlagen (Code: %errorLevel%)
    echo.
    echo Bitte kontaktieren Sie den Administrator.
    echo.
)

pause
