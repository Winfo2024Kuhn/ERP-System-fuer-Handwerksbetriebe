@echo off
REM ====================================================================
REM OpenFile Launcher - Einfaches Installations-Skript (Batch)
REM ====================================================================
REM Dieses Skript ruft das PowerShell-Installations-Skript auf.
REM Vorteil: Kann per Doppelklick ausgefuehrt werden!
REM
REM WICHTIG: Rechtsklick -> "Als Administrator ausfuehren"
REM ====================================================================

echo.
echo =====================================================================
echo  OpenFile Launcher - Installation
echo =====================================================================
echo.
echo HINWEIS: Falls eine Sicherheitsabfrage erscheint, druecken Sie "Ja".
echo.

REM Pruefen, ob launcher.ps1 vorhanden ist
if not exist "%~dp0launcher.ps1" (
    echo FEHLER: launcher.ps1 nicht gefunden in diesem Verzeichnis!
    echo Erwarteter Pfad: %~dp0launcher.ps1
    echo.
    pause
    exit /b 1
)

REM PowerShell-Installations-Skript ausfuehren
echo Starte Installation...
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-openfile-launcher.ps1"

REM Exitcode pruefen
if %ERRORLEVEL% EQU 0 (
    echo.
    echo =====================================================================
    echo  Installation abgeschlossen!
    echo =====================================================================
    echo.
) else (
    echo.
    echo =====================================================================
    echo  Installation fehlgeschlagen! (Fehlercode: %ERRORLEVEL%)
    echo =====================================================================
    echo.
)

pause
