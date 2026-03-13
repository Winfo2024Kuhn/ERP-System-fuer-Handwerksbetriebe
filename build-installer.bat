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
copy target\Kalkulationsprogramm-1.0.0.jar target\jpackage-input\ >nul
echo       Staging fertig.

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
echo ============================================
echo   Installer erstellt in:
echo   target\installer\ERP-Handwerk-1.0.0.exe
echo.
echo   Der Installer enthaelt:
echo   - Eigene Java-Laufzeitumgebung (JRE)
echo   - Eingebettete H2-Datenbank
echo   - Startmenue-Eintrag + Desktop-Shortcut
echo.
echo   Der Benutzer braucht NICHTS vorinstalliert!
echo ============================================
echo.
pause
