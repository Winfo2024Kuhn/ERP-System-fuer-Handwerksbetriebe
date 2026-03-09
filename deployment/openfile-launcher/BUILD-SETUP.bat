@echo off
REM ====================================================================
REM OpenFile Launcher - Setup-Generator
REM ====================================================================
REM Erstellt eine Setup.exe aus den Installations-Dateien
REM ====================================================================

echo.
echo =====================================================================
echo  OpenFile Launcher - Setup-Generator
echo =====================================================================
echo.

REM Pruefen, ob alle benoetigten Dateien vorhanden sind
if not exist "launcher.ps1" (
    echo FEHLER: launcher.ps1 nicht gefunden!
    pause
    exit /b 1
)

if not exist "install-openfile-launcher.ps1" (
    echo FEHLER: install-openfile-launcher.ps1 nicht gefunden!
    pause
    exit /b 1
)

echo Erstelle Setup.exe mit IExpress...
echo.

REM IExpress ausfuehren
iexpress /N setup-config.sed

if exist "OpenFileLauncher-Setup.exe" (
    echo.
    echo =====================================================================
    echo  Setup erfolgreich erstellt!
    echo =====================================================================
    echo.
    echo Datei: OpenFileLauncher-Setup.exe
    echo.
    echo Sie koennen diese .exe-Datei jetzt an andere PCs verteilen.
    echo Doppelklick auf die .exe fuehrt die Installation durch.
    echo.
) else (
    echo.
    echo FEHLER: Setup konnte nicht erstellt werden!
    echo.
)

pause
