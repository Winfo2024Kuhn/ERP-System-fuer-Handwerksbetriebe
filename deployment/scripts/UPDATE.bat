@echo off
REM ============================================================================
REM Quick Update Script - Startet das PowerShell Update-Script
REM ============================================================================

echo.
echo ========================================
echo   SOFTWARE UPDATE
echo ========================================
echo.

cd /d "%~dp0"

powershell.exe -ExecutionPolicy Bypass -File ".\update-production.ps1"

pause
