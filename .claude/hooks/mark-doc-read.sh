#!/usr/bin/env bash
# Bash-Fassung von mark-doc-read.ps1 (macOS/Linux ohne pwsh), gleiche Logik:
# PostToolUse-Hook für Read. Setzt das Session-Flag, sobald BACKEND_ARCH.md,
# FRONTEND_UI.md oder TESTING_SECURITY.md gelesen wurde.

if ! command -v jq >/dev/null 2>&1; then
    echo "DOC-READ-GUARD inaktiv: jq fehlt (macOS: brew install jq)." >&2
    exit 1
fi

payload="$(cat)"
sessionId="$(printf '%s' "$payload" | jq -r '.session_id // empty' 2>/dev/null)" || exit 0
filePath="$(printf '%s' "$payload" | jq -r '.tool_input.file_path // empty' 2>/dev/null)" || exit 0
if [ -z "$sessionId" ] || [ -z "$filePath" ]; then exit 0; fi

# Wie [System.IO.Path]::GetTempPath() unter Unix: $TMPDIR, sonst /tmp.
tmpDir="${TMPDIR:-/tmp}"
flagDir="${tmpDir%/}/claude-doc-flags"
mkdir -p "$flagDir" 2>/dev/null

normalized="${filePath//\\//}"

# PowerShell -match ist case-insensitive, deshalb hier ebenso.
shopt -s nocasematch

reBackend='/BACKEND_ARCH\.md$'
reFrontend='/FRONTEND_UI\.md$'
reTesting='/TESTING_SECURITY\.md$'
if [[ $normalized =~ $reBackend ]]; then
    printf 'read\n' > "$flagDir/$sessionId-backend.flag" 2>/dev/null
fi
if [[ $normalized =~ $reFrontend ]]; then
    printf 'read\n' > "$flagDir/$sessionId-frontend.flag" 2>/dev/null
fi
if [[ $normalized =~ $reTesting ]]; then
    printf 'read\n' > "$flagDir/$sessionId-testing.flag" 2>/dev/null
fi

exit 0
