#!/usr/bin/env bash
# Bash-Fassung von mark-graphify-used.ps1 (macOS/Linux ohne pwsh), gleiche Logik:
# PostToolUse-Hook für Bash: setzt das Session-Flag, sobald ein graphify-Befehl
# gelaufen ist.
#
# Erwartet auf stdin den Hook-Input von Claude Code (JSON mit session_id
# und tool_input.command).

if ! command -v jq >/dev/null 2>&1; then
    echo "mark-graphify-used: jq fehlt (macOS: brew install jq)." >&2
    exit 1
fi

payload="$(cat)"
sessionId="$(printf '%s' "$payload" | jq -r '.session_id // empty' 2>/dev/null)" || exit 0
if [ -z "$sessionId" ]; then exit 0; fi

command="$(printf '%s' "$payload" | jq -r '.tool_input.command // empty' 2>/dev/null)" || exit 0
if [ -z "$command" ]; then exit 0; fi

# Nur echte graphify-Aufrufe markieren — keine zufälligen Substrings.
# (PowerShell -match ist case-insensitive, deshalb hier ebenso.)
shopt -s nocasematch
reGraphify='(^|[[:space:];&|])graphify([[:space:]]|$)'
if ! [[ $command =~ $reGraphify ]]; then exit 0; fi

# Gegenstück zu $env:TEMP unter Windows.
tmpDir="${TMPDIR:-/tmp}"
flagDir="${tmpDir%/}/claude-erp-graphify-flags"
mkdir -p "$flagDir" 2>/dev/null
date '+%Y-%m-%dT%H:%M:%S%z' > "$flagDir/session-$sessionId.flag" 2>/dev/null

exit 0
