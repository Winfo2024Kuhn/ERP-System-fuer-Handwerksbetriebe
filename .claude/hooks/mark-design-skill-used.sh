#!/usr/bin/env bash
# Bash-Fassung von mark-design-skill-used.ps1 (macOS/Linux ohne pwsh), gleiche Logik:
# Setzt ein Session-Flag, sobald einer der Pflicht-Design-Skills aufgerufen wurde.
# Gegenstueck: check-doc-read.sh blockt Frontend-Edits ohne dieses Flag.

if ! command -v jq >/dev/null 2>&1; then
    echo "DESIGN-SKILL-GUARD inaktiv: jq fehlt (macOS: brew install jq)." >&2
    exit 1
fi

payload="$(cat)"
sessionId="$(printf '%s' "$payload" | jq -r '.session_id // empty' 2>/dev/null)" || exit 0
if [ -z "$sessionId" ]; then exit 0; fi

# Skill-Name steht je nach Tool-Variante in .skill oder .name
skillName="$(printf '%s' "$payload" | jq -r '.tool_input.skill // .tool_input.name // empty' 2>/dev/null)" || exit 0
if [ -z "$skillName" ]; then exit 0; fi

# Der Frontend-Skill, der als Design-Pflichtlektuere zaehlt
# (PowerShell -contains vergleicht case-insensitive, deshalb hier ebenso.)
shopt -s nocasematch
case "$skillName" in
    handwerkerprogramm-design|\
    handwerkerprogramm-design:handwerkerprogramm-design)
        # Wie [System.IO.Path]::GetTempPath() unter Unix: $TMPDIR, sonst /tmp.
        tmpDir="${TMPDIR:-/tmp}"
        flagDir="${tmpDir%/}/claude-doc-flags"
        mkdir -p "$flagDir" 2>/dev/null
        printf '%s\n' "$skillName" > "$flagDir/$sessionId-design.flag" 2>/dev/null
        ;;
esac

exit 0
