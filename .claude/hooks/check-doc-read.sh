#!/usr/bin/env bash
# Bash-Fassung von check-doc-read.ps1 (macOS/Linux ohne pwsh), gleiche Logik:
# PreToolUse-Hook für Edit|Write|MultiEdit. Blockt mit Exit 2, solange das
# passende Doc (bzw. der Design-Skill) in dieser Session nicht gelesen wurde.
# Die Flags setzen mark-doc-read.sh und mark-design-skill-used.sh.

if ! command -v jq >/dev/null 2>&1; then
    echo "DOC-READ-GUARD inaktiv: jq fehlt (macOS: brew install jq)." >&2
    exit 1
fi

payload="$(cat)"
sessionId="$(printf '%s' "$payload" | jq -r '.session_id // empty' 2>/dev/null)" || exit 0
filePath="$(printf '%s' "$payload" | jq -r '.tool_input.file_path // empty' 2>/dev/null)" || exit 0
if [ -z "$sessionId" ] || [ -z "$filePath" ]; then exit 0; fi

normalized="${filePath//\\//}"
# Wie [System.IO.Path]::GetTempPath() unter Unix: $TMPDIR, sonst /tmp.
tmpDir="${TMPDIR:-/tmp}"
flagDir="${tmpDir%/}/claude-doc-flags"

# PowerShell -match ist case-insensitive, deshalb hier ebenso.
shopt -s nocasematch

# Backend-Pfade: alle .java-Dateien im src/main oder src/test
reJava='\.java$'
if [[ $normalized =~ $reJava ]]; then
    if [ ! -e "$flagDir/$sessionId-backend.flag" ]; then
        cat >&2 <<'EOF'
DOC-READ-GUARD: Bevor du eine .java-Datei editierst/schreibst, lies bitte zuerst mit dem Read-Tool:
  docs/agent instructions/docs/BACKEND_ARCH.md (im Projektroot)
Diese Doc enthaelt verbindliche Architektur-, JPA-, Flyway- und SQL-Regeln (Schichtentrennung, Constructor Injection, Named-Params, Migration-Versionierung). Erst nach dem Read darf editiert werden. Wiederhole danach den Edit/Write-Aufruf.
EOF
        exit 2
    fi
fi

# Frontend-Pfade: react-pc-frontend oder react-zeiterfassung, Endung tsx/ts/jsx/js
reFrontendDir='/(react-pc-frontend|react-zeiterfassung)/'
reFrontendExt='\.(tsx|ts|jsx|js)$'
if [[ $normalized =~ $reFrontendDir ]] && [[ $normalized =~ $reFrontendExt ]]; then
    if [ ! -e "$flagDir/$sessionId-frontend.flag" ]; then
        cat >&2 <<'EOF'
DOC-READ-GUARD: Bevor du eine Frontend-Datei editierst/schreibst, lies bitte zuerst mit dem Read-Tool:
  docs/agent instructions/docs/FRONTEND_UI.md (im Projektroot)
Diese Doc enthaelt verbindliche UI-Regeln (rose-/slate-Farbschema, Pflicht-Komponenten Select/DatePicker/DocumentPreviewModal, Page-Header-Pattern, Handwerker-Wording). Erst nach dem Read darf editiert werden. Wiederhole danach den Edit/Write-Aufruf.
EOF
        exit 2
    fi

    # Zusaetzlich: Design-Skill muss in dieser Session aufgerufen worden sein
    if [ ! -e "$flagDir/$sessionId-design.flag" ]; then
        cat >&2 <<'EOF'
DESIGN-SKILL-GUARD: Frontend-Aenderungen erfordern zusaetzlich einen der Design-Skills. Rufe VORHER per Skill-Tool auf:
  handwerkerprogramm-design      (PFLICHT-STANDARD: unser eigenes Design-System - Farben, Typografie, Icons, Wording, UI-Kits, echte Screenshots)
  ui-ux-pro-max:ui-ux-pro-max    (generisch: Styles, Farben, Typografie, UX-Regeln - nur wenn handwerkerprogramm-design keine Antwort liefert)
  frontend-design:frontend-design (visuelle Ausrichtung neuer UI)
  ui-ux-pro-max:design-system    (Design-Tokens, Komponenten-Specs)
Nutze fuer Komponenten ausserdem die MCP-Server 'shadcn' (fertige Bausteine) und 'magic' (21st.dev, animierte Layouts), statt Komponenten von Hand nachzubauen - Farben danach immer auf unser Schema (rose/slate) umstellen.
Erst nach dem Skill-Aufruf darf editiert werden. Wiederhole danach den Edit/Write-Aufruf.
EOF
        exit 2
    fi
fi

# Test-Dateien: zwingt TESTING_SECURITY.md (greift bei *Test.java oder *.test.tsx/*.spec.tsx)
reTestJava='(Test|Tests)\.java$'
reTestJs='\.(test|spec)\.(tsx|ts|jsx|js)$'
if [[ $normalized =~ $reTestJava ]] || [[ $normalized =~ $reTestJs ]]; then
    if [ ! -e "$flagDir/$sessionId-testing.flag" ]; then
        cat >&2 <<'EOF'
DOC-READ-GUARD: Bevor du eine Test-Datei editierst/schreibst, lies bitte zuerst mit dem Read-Tool:
  docs/agent instructions/docs/TESTING_SECURITY.md (im Projektroot)
Diese Doc enthaelt verbindliche Test- und Security-Regeln (DSGVO-Dummy-Daten, JUnit/Vitest-Patterns). Erst nach dem Read darf editiert werden. Wiederhole danach den Edit/Write-Aufruf.
EOF
        exit 2
    fi
fi

exit 0
