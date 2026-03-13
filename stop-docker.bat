@echo off
chcp 65001 >nul
echo Stoppe Kalkulationsprogramm Docker Container...
echo.

docker compose down

echo.
echo Alle Container wurden gestoppt.
echo.
echo Hinweis: Daten bleiben in Docker Volumes erhalten.
echo          Zum kompletten Löschen: docker compose down -v
echo.
pause
