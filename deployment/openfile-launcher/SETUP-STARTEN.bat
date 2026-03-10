@echo off
REM ====================================================================
REM OpenFile Launcher - Setup Starter
REM ====================================================================
REM Fuehrt das PowerShell-Setup als Administrator aus
REM ====================================================================

echo.
echo =====================================================================
echo  OpenFile Launcher - Installation wird gestartet...
echo =====================================================================
echo.

REM Starte PowerShell-Setup als Administrator
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Start-Process powershell.exe -ArgumentList '-NoProfile -ExecutionPolicy Bypass -File \"%~dp0OpenFileLauncher-Setup-FINAL.ps1\"' -Verb RunAs -Wait"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Installation abgeschlossen!
    echo.
) else (
    echo.
    echo Fehler bei der Installation!
    echo.
)

pause
