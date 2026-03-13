@echo off
chcp 65001 >nul
echo ============================================
echo   Kalkulationsprogramm - Docker Starter
echo ============================================
echo.

:: Prüfe ob Docker läuft
docker info >nul 2>&1
if errorlevel 1 (
    echo [FEHLER] Docker ist nicht gestartet!
    echo Bitte Docker Desktop starten und erneut versuchen.
    echo.
    pause
    exit /b 1
)

echo [1/3] Baue und starte alle Container...
echo       (Erster Start kann einige Minuten dauern)
echo.

docker compose up -d --build

if errorlevel 1 (
    echo.
    echo [FEHLER] Container konnten nicht gestartet werden.
    echo Logs anzeigen mit: docker compose logs
    pause
    exit /b 1
)

echo.
echo [2/3] Warte auf Datenbank-Initialisierung...
timeout /t 10 /nobreak >nul

echo.
echo [3/3] Prüfe Status...
docker compose ps

echo.
echo ============================================
echo   Anwendung gestartet!
echo.
echo   Frontend:  http://localhost:8080
echo   MySQL:     localhost:3307
echo   Qdrant:    http://localhost:6333
echo.
echo   Anmelden mit:
echo     Benutzer: Marvin
echo     Passwort: 123456
echo.
echo   Stoppen mit: stop-docker.bat
echo   Logs:        docker compose logs -f app
echo ============================================
echo.

:: Browser öffnen
timeout /t 5 /nobreak >nul
start "" "http://localhost:8080"

pause
