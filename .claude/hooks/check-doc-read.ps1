$ErrorActionPreference = 'SilentlyContinue'
try {
    $payload = [Console]::In.ReadToEnd() | ConvertFrom-Json
} catch {
    exit 0
}

$sessionId = $payload.session_id
$filePath = $payload.tool_input.file_path
if (-not $sessionId -or -not $filePath) { exit 0 }

$normalized = $filePath -replace '\\', '/'
$flagDir = Join-Path $env:TEMP 'claude-doc-flags'

# Backend-Pfade: alle .java-Dateien im src/main oder src/test
if ($normalized -match '\.java$') {
    $flag = Join-Path $flagDir "$sessionId-backend.flag"
    if (-not (Test-Path $flag)) {
        [Console]::Error.WriteLine(
            "DOC-READ-GUARD: Bevor du eine .java-Datei editierst/schreibst, lies bitte zuerst mit dem Read-Tool:`n" +
            "  c:\dev\ERP-System-fuer-Handwerksbetriebe\docs\agent instructions\docs\BACKEND_ARCH.md`n" +
            "Diese Doc enthaelt verbindliche Architektur-, JPA-, Flyway- und SQL-Regeln (Schichtentrennung, Constructor Injection, Named-Params, Migration-Versionierung). Erst nach dem Read darf editiert werden. Wiederhole danach den Edit/Write-Aufruf."
        )
        exit 2
    }
}

# Frontend-Pfade: react-pc-frontend oder react-zeiterfassung, Endung tsx/ts/jsx/js
if ($normalized -match '/(react-pc-frontend|react-zeiterfassung)/' -and $normalized -match '\.(tsx|ts|jsx|js)$') {
    $flag = Join-Path $flagDir "$sessionId-frontend.flag"
    if (-not (Test-Path $flag)) {
        [Console]::Error.WriteLine(
            "DOC-READ-GUARD: Bevor du eine Frontend-Datei editierst/schreibst, lies bitte zuerst mit dem Read-Tool:`n" +
            "  c:\dev\ERP-System-fuer-Handwerksbetriebe\docs\agent instructions\docs\FRONTEND_UI.md`n" +
            "Diese Doc enthaelt verbindliche UI-Regeln (rose-/slate-Farbschema, Pflicht-Komponenten Select/DatePicker/DocumentPreviewModal, Page-Header-Pattern, Handwerker-Wording). Erst nach dem Read darf editiert werden. Wiederhole danach den Edit/Write-Aufruf."
        )
        exit 2
    }

    # Zusaetzlich: Design-Skill muss in dieser Session aufgerufen worden sein
    $designFlag = Join-Path $flagDir "$sessionId-design.flag"
    if (-not (Test-Path $designFlag)) {
        [Console]::Error.WriteLine(
            "DESIGN-SKILL-GUARD: Frontend-Aenderungen erfordern zusaetzlich einen der Design-Skills. Rufe VORHER per Skill-Tool auf:`n" +
            "  ui-ux-pro-max:ui-ux-pro-max   (Standard: Styles, Farben, Typografie, UX-Regeln)`n" +
            "  frontend-design:frontend-design (visuelle Ausrichtung neuer UI)`n" +
            "  ui-ux-pro-max:design-system    (Design-Tokens, Komponenten-Specs)`n" +
            "Nutze fuer Komponenten ausserdem die MCP-Server 'shadcn' (fertige Bausteine) und 'magic' (21st.dev, animierte Layouts), statt Komponenten von Hand nachzubauen.`n" +
            "Erst nach dem Skill-Aufruf darf editiert werden. Wiederhole danach den Edit/Write-Aufruf."
        )
        exit 2
    }
}

# Test-Dateien: zwingt TESTING_SECURITY.md (greift bei *Test.java oder *.test.tsx/*.spec.tsx)
if ($normalized -match '(Test|Tests)\.java$' -or $normalized -match '\.(test|spec)\.(tsx|ts|jsx|js)$') {
    $flag = Join-Path $flagDir "$sessionId-testing.flag"
    if (-not (Test-Path $flag)) {
        [Console]::Error.WriteLine(
            "DOC-READ-GUARD: Bevor du eine Test-Datei editierst/schreibst, lies bitte zuerst mit dem Read-Tool:`n" +
            "  c:\dev\ERP-System-fuer-Handwerksbetriebe\docs\agent instructions\docs\TESTING_SECURITY.md`n" +
            "Diese Doc enthaelt verbindliche Test- und Security-Regeln (DSGVO-Dummy-Daten, JUnit/Vitest-Patterns). Erst nach dem Read darf editiert werden. Wiederhole danach den Edit/Write-Aufruf."
        )
        exit 2
    }
}

exit 0
