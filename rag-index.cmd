@echo off
REM ============================================================================
REM RAG-Datei der KI-Hilfe lokal erzeugen bzw. aktualisieren (Windows)
REM ============================================================================
REM Dieselbe Logik wie beim Image-Bau in GitHub und im Server (RAG_INDEXING.md).
REM Fuer die Kundenserver nicht noetig - deren RAG-Datei kommt mit dem Image.
REM
REM   set GEMINI_API_KEY=...
REM   rag-index.cmd                     -^> .rag-cache.json
REM   rag-index.cmd pfad\datei.json
REM   rag-index.cmd --nur-zaehlen       (ohne Schluessel, ohne Kosten)
REM
REM Den Schluessel nur als Umgebungsvariable, nie als Argument.
REM ============================================================================
setlocal
cd /d "%~dp0"

echo Baue das ERP (ohne Tests) ...
call mvnw.cmd -q -DskipTests package
if errorlevel 1 exit /b 1

REM Nur der Spring-Boot-Jar, kein alter "...-jar-with-dependencies.jar"
set "JAR="
for /f "delims=" %%F in ('dir /b target\Kalkulationsprogramm-*.jar ^| findstr /v /c:"-sources.jar" /c:"-javadoc.jar" /c:"-jar-with-dependencies.jar"') do set "JAR=target\%%F"
if not defined JAR (
    echo JAR nicht gefunden - Build fehlgeschlagen?
    exit /b 1
)

java -cp "%JAR%" -Dloader.main=org.example.kalkulationsprogramm.cli.StandaloneRagIndexer org.springframework.boot.loader.launch.PropertiesLauncher . %*
exit /b %errorlevel%
