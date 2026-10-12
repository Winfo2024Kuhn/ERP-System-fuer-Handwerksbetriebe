#!/usr/bin/env bash
# Startet einen Guard-Hook plattformgerecht (Aufruf aus .claude/settings.json):
#   bash "${CLAUDE_PROJECT_DIR}/.claude/hooks/run-hook.sh" check-doc-read
#
# Mit PowerShell (Windows, Cloud-Sitzung) läuft <name>.ps1, sonst (macOS/Linux
# ohne pwsh) die gleichwertige Bash-Fassung <name>.sh. stdin (Hook-JSON),
# stderr und Exit-Code werden unverändert durchgereicht.

NAME="${1:-}"
case "$NAME" in
    ''|*[!abcdefghijklmnopqrstuvwxyz-]*)
        echo "run-hook.sh: ungueltiger Hook-Name '$NAME'" >&2
        exit 1
        ;;
esac

# Bewusst ohne cd/pwd: unter Git Bash bleibt so ein Windows-Pfad aus
# CLAUDE_PROJECT_DIR erhalten, den pwsh direkt versteht.
HIER="$(dirname "${BASH_SOURCE[0]}")"

if command -v pwsh >/dev/null 2>&1 && [ -f "$HIER/$NAME.ps1" ]; then
    exec pwsh -NoProfile -ExecutionPolicy Bypass -File "$HIER/$NAME.ps1"
fi
exec bash "$HIER/$NAME.sh"
