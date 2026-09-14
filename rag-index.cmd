@echo off
REM RAG Indexierung starten (Windows)

if "%1"=="" (
    echo.
    echo [91m❌ Gemini API Key erforderlich[0m
    echo.
    echo Verwendung: rag-index.cmd ^<gemini-api-key^> [project-root]
    echo.
    echo Beispiel:
    echo   rag-index.cmd sk-1234567890 .
    echo.
    echo Oder mit Env-Variable:
    echo   set GEMINI_API_KEY=sk-1234567890
    echo   rag-index.cmd
    echo.
    exit /b 1
)

setlocal enabledelayedexpansion

if "%GEMINI_API_KEY%"=="" (
    set "API_KEY=%1"
) else (
    set "API_KEY=!GEMINI_API_KEY!"
)

if "%2"=="" (
    set "PROJECT_ROOT=."
) else (
    set "PROJECT_ROOT=%2"
)

if "!API_KEY!"=="" (
    echo.
    echo [91m❌ Gemini API Key nicht gesetzt[0m
    echo.
    exit /b 1
)

echo.
echo [96m📦 Kompiliere Projekt...[0m
call mvn clean compile assembly:single -DskipTests -q
if errorlevel 1 (
    echo [91m❌ Kompilierung fehlgeschlagen[0m
    exit /b 1
)

setlocal enabledelayedexpansion
for /f "delims=" %%F in ('dir /b target\*-jar-with-dependencies.jar 2^>nul') do (
    set "JAR=target\%%F"
    goto found_jar
)

:found_jar
if "!JAR!"=="" (
    echo [91m❌ JAR nicht gefunden nach Kompilierung[0m
    exit /b 1
)

echo [96m🚀 Starte RAG-Indexierung...[0m
echo    JAR: !JAR!
echo    Root: !PROJECT_ROOT!
echo.

java -cp "!JAR!" org.example.kalkulationsprogramm.cli.StandaloneRagIndexer "!API_KEY!" "!PROJECT_ROOT!"

if errorlevel 1 (
    echo [91m❌ Indexierung fehlgeschlagen[0m
    exit /b 1
)

echo.
echo [92m✅ Fertig! Cache gespeichert in: !PROJECT_ROOT!\.rag-cache.json[0m
echo.
