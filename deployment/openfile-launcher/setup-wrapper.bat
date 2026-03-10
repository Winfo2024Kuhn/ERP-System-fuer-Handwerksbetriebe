@echo off
REM Wrapper fuer IExpress Setup - wechsle ins Skript-Verzeichnis
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Start-Process powershell.exe -ArgumentList '-NoProfile -ExecutionPolicy Bypass -File \"%~dp0install-openfile-launcher.ps1\"' -Verb RunAs -Wait"
