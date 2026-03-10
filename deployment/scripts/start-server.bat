@echo off
cd /d "%~dp0"
echo Starte Kalkulationsprogramm...
rem → neues Konsolenfenster, minimiert, bleibt offen
start "Kalkulationsprogramm Server" /min cmd /k "java -jar target\Kalkulationsprogramm-1.0.0.jar"
rem Browser nach kurzer Wartezeit öffnen
timeout /t 3 >nul
start "" "http://localhost:8080"
