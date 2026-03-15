@echo off
chcp 65001 >nul
echo.
echo   ============================================
echo     ERP-Handwerk - Lokale Einrichtung
echo   ============================================
echo.
echo   Dieses Script richtet ERP-Handwerk auf Ihrem
echo   Computer ein (Desktop-Symbol + Autostart).
echo.
echo   Bitte bestaetigen Sie eventuelle
echo   Sicherheitsabfragen mit "Ja".
echo.
pause

cd /d "%~dp0"
PowerShell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-local.ps1"

if errorlevel 1 (
    echo.
    echo   Die Einrichtung ist fehlgeschlagen.
    echo   Bitte pruefen Sie die Fehlermeldung oben.
    echo.
    pause
)
