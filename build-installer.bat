@echo off
chcp 65001 >nul
echo ============================================
echo   ERP-Handwerk - Installer bauen
echo ============================================
echo.

cd /d "%~dp0"

:: Prüfe ob JDK vorhanden (jpackage benötigt JDK, nicht nur JRE)
where jpackage >nul 2>&1
if errorlevel 1 (
    echo [FEHLER] jpackage nicht gefunden!
    echo Bitte JDK 23+ installieren (https://adoptium.net)
    echo.
    pause
    exit /b 1
)

:: Prüfe ob WiX Toolset installiert ist (für .exe Installer)
where candle >nul 2>&1
if errorlevel 1 (
    echo [WARNUNG] WiX Toolset nicht gefunden.
    echo Fuer .exe/.msi Installer wird WiX 3.x benoetigt:
    echo   https://github.com/wixtoolset/wix3/releases
    echo.
    echo Versuche trotzdem zu bauen...
    echo.
)

echo [1/4] Baue JAR...
call mvnw.cmd clean package -DskipTests -q
if errorlevel 1 (
    echo [FEHLER] Maven Build fehlgeschlagen!
    pause
    exit /b 1
)
echo       JAR erstellt.

echo.
echo [2/4] Erstelle Staging-Verzeichnis...
if exist target\jpackage-input rmdir /s /q target\jpackage-input
mkdir target\jpackage-input

set "APP_JAR="
for %%f in (target\Kalkulationsprogramm-*.jar) do (
    set "APP_JAR=%%~nxf"
)

if not defined APP_JAR (
    echo [FEHLER] Keine Spring-Boot-JAR in target\ gefunden!
    pause
    exit /b 1
)

copy "target\%APP_JAR%" target\jpackage-input\ >nul
if errorlevel 1 (
    echo [FEHLER] Konnte target\%APP_JAR% nicht in target\jpackage-input\ kopieren!
    pause
    exit /b 1
)

echo       Staging fertig: %APP_JAR%

echo.
echo [3/4] Erstelle Windows-Installer mit jpackage...
call mvnw.cmd jpackage:jpackage -q
if errorlevel 1 (
    echo [FEHLER] jpackage fehlgeschlagen!
    echo Ist WiX Toolset installiert?
    pause
    exit /b 1
)

echo.
echo [4/4] Fertig!
echo.
set "INSTALLER_EXE="
for %%f in (target\installer\ERP-Handwerk-*.exe) do (
    set "INSTALLER_EXE=%%~nxf"
)

echo ============================================
echo   Installer erstellt in:
if defined INSTALLER_EXE (
echo   target\installer\%INSTALLER_EXE%
) else (
echo   target\installer\ERP-Handwerk-<Version>.exe
)
echo.
echo   ALTERNATIV: Lokale Installation (empfohlen)
echo   ============================================
echo   Falls der jpackage-Installer Probleme macht
echo   (z.B. "Failed to launch JVM"), verwenden Sie
echo   stattdessen die lokale Einrichtung:
echo.
echo     deployment\local-install\Einrichtung-ERP-Handwerk.bat
echo.
echo   Voraussetzung: Java 23+ installiert
echo   Erstellt Desktop-Shortcut + Autostart
echo ============================================
echo.
pause
