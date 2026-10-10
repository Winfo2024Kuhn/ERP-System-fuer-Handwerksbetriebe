@echo off
REM ERP Handwerk einrichten - per Doppelklick starten
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0einrichten-windows.ps1" %*
pause
