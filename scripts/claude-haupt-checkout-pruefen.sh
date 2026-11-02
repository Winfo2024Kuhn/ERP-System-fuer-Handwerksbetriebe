#!/usr/bin/env bash
# SessionStart-Hook: hält den Haupt-Checkout auf dem Stand von origin/main.
#
# Sitzungen der Desktop-App laufen in Worktrees unter
# <Haupt-Checkout>/.claude/worktrees/<name>/, laden Skills und Agenten
# (.claude/skills, .claude/agents, .claude/commands) aber aus dem
# Haupt-Checkout. Liegt der zurück, arbeitet die Sitzung mit alten Skills und
# ohne neue Agent-Typen, ohne dass es auffällt.
#
# Ablauf:
#   1. git fetch origin (mit Zeitlimit; offline still weiter)
#   2. Haupt-Checkout auf main, ohne lokale Änderungen an versionierten
#      Dateien, nur hinterher  ->  Fast-Forward (wie git pull --ff-only)
#   3. Weichen Skills/Agenten danach noch von origin/main ab (lokale
#      Änderungen, anderer Branch, eigene Commits, Pull gescheitert), wird
#      nichts angefasst, sondern ein Hinweis für den Sitzungskontext
#      ausgegeben. Der Haupt-Checkout wird von parallelen Sitzungen geteilt:
#      nie zurücksetzen, stashen oder Dateien überschreiben.
#
# Ist alles aktuell, gibt es keine Ausgabe. Exit-Code immer 0 (die Sitzung
# wird nie blockiert). Aufruf aus .claude/settings.json:
#   bash "${CLAUDE_PROJECT_DIR}/scripts/claude-haupt-checkout-pruefen.sh"
#
# Umgebungsvariablen (vor allem für den Test):
#   CLAUDE_PROJECT_DIR                 Startordner (sonst aktueller Ordner)
#   HAUPT_CHECKOUT_FETCH_SEKUNDEN      Zeitlimit für git fetch (Standard 4)
#
# Braucht Git 2.31+ (--path-format); mit älterem Git endet es still.

set -uo pipefail

# Würden git -C übersteuern und ein fremdes Repo zum Haupt-Checkout machen.
unset GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE GIT_COMMON_DIR

[ "${CLAUDE_CODE_REMOTE:-}" = "true" ] && exit 0

FETCH_SEKUNDEN="${HAUPT_CHECKOUT_FETCH_SEKUNDEN:-4}"
HAUPT_BRANCH="main"
ZIEL="origin/$HAUPT_BRANCH"
# Ordner, die Claude Code aus dem Haupt-Checkout lädt.
GELADENE_ORDNER=(.claude/skills .claude/agents .claude/commands)
MAX_ZEILEN=15

START="${CLAUDE_PROJECT_DIR:-$PWD}"

# Führt einen Befehl aus und bricht ihn nach $1 Sekunden ab. Eigene Fassung,
# weil `timeout` auf älterem macOS fehlt. Nachfahren (ssh, git-remote-https)
# werden mit beendet, soweit pgrep vorhanden ist. Git for Windows hat kein
# pgrep: dort laufen sie bis zu ihrem eigenen Netz-Timeout weiter, die Sitzung
# wartet aber nicht darauf (Ausgaben gehen nach /dev/null).
prozessbaum_beenden() {
    local kind
    if command -v pgrep >/dev/null 2>&1; then
        for kind in $(pgrep -P "$1"); do
            prozessbaum_beenden "$kind"
        done
    fi
    kill "$1"
}

mit_zeitlimit() {
    local sekunden="$1"; shift
    "$@" </dev/null >/dev/null 2>&1 &
    local pid=$!
    ( sleep "$sekunden"; prozessbaum_beenden "$pid" ) </dev/null >/dev/null 2>&1 &
    local waechter=$!
    wait "$pid" 2>/dev/null
    local rc=$?
    kill "$waechter" 2>/dev/null
    wait "$waechter" 2>/dev/null
    return "$rc"
}

# Gibt höchstens MAX_ZEILEN Zeilen aus, eingerückt, mit Hinweis auf den Rest.
liste_kuerzen() {
    awk -v max="$MAX_ZEILEN" '
        NF { n++; if (n <= max) print "    " $0 }
        END { if (n > max) print "    … und " (n - max) " weitere" }'
}

common_dir="$(git -C "$START" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)" || exit 0
[ -n "$common_dir" ] && [ "$(basename "$common_dir")" = ".git" ] || exit 0
HAUPT="$(dirname "$common_dir")"
[ -d "$HAUPT/.git" ] || exit 0

g() { git -C "$HAUPT" "$@"; }

sitzung_im_haupt_checkout=false
eigener_top="$(git -C "$START" rev-parse --show-toplevel 2>/dev/null)"
[ "$eigener_top" = "$HAUPT" ] && sitzung_im_haupt_checkout=true

g remote get-url origin >/dev/null 2>&1 || exit 0

# Offline oder Server langsam: mit dem bekannten Stand von origin/main weiter.
# Ein eigenes core.sshCommand (z. B. anderer Schlüssel) bleibt unangetastet.
if [ -z "${GIT_SSH_COMMAND:-}" ] && [ -z "$(g config core.sshCommand 2>/dev/null)" ]; then
    export GIT_SSH_COMMAND="ssh -o BatchMode=yes -o ConnectTimeout=3"
fi
GIT_TERMINAL_PROMPT=0 \
    mit_zeitlimit "$FETCH_SEKUNDEN" \
    git -C "$HAUPT" -c http.lowSpeedLimit=1000 -c http.lowSpeedTime=3 \
        fetch --quiet --no-tags origin "$HAUPT_BRANCH"

g rev-parse --verify --quiet "$ZIEL^{commit}" >/dev/null || exit 0

branch="$(g symbolic-ref --quiet --short HEAD 2>/dev/null || echo "")"

# Eine Sitzung direkt im Haupt-Checkout auf einem Arbeits-Branch ändert
# Skills womöglich gerade selbst: dann nichts melden.
if $sitzung_im_haupt_checkout && [ "$branch" != "$HAUPT_BRANCH" ]; then
    exit 0
fi

geaenderte_dateien() {
    g status --porcelain --untracked-files=no
}

# Meldet ein Vorspulen nur, wenn dieser Lauf selbst vorgespult hat. Starten
# mehrere Sitzungen gleichzeitig, sehen die späteren "Already up to date".
vorgespult() {
    if ! printf '%s' "$ausgabe" | grep -qE "Already up.to.date"; then
        echo "Haupt-Checkout $HAUPT: main war $hinter Commit(s) hinter origin/main und ist jetzt aktualisiert. Skills und Agenten, die diese Sitzung schon beim Start geladen hat, können noch die alte Fassung sein – im Zweifel die Datei im Worktree per Read lesen."
    fi
    hinter=0
}

hinter=0
voraus=0
grund=""
if [ "$branch" = "$HAUPT_BRANCH" ]; then
    read -r voraus hinter < <(g rev-list --left-right --count "HEAD...$ZIEL" 2>/dev/null || echo "0 0")
    if [ "$voraus" -gt 0 ]; then
        grund="main hat $voraus eigene Commit(s), die nicht auf origin/main liegen"
    elif [ "$hinter" -gt 0 ]; then
        if [ -n "$(geaenderte_dateien)" ]; then
            grund="lokale Änderungen im Haupt-Checkout"
        # origin/main ist frisch geholt: ein Fast-Forward-Merge reicht (wie
        # git pull --ff-only, aber ohne zweiten Netzzugriff). Autostash ist
        # ausdrücklich aus: Ändert eine parallele Sitzung genau jetzt eine
        # Datei, bricht der Merge ab, statt ihre Änderung zu stashen.
        # LC_ALL=C, damit vorgespult() die Meldung von git sicher erkennt.
        elif ausgabe="$(LC_ALL=C g -c merge.autoStash=false merge --ff-only --no-autostash --no-stat "$ZIEL" </dev/null 2>&1)"; then
            vorgespult
        elif [ "$(g rev-parse HEAD)" = "$(g rev-parse "$ZIEL")" ]; then
            # Eine parallel startende Sitzung war schneller (z. B. index.lock).
            # Läuft deren Merge noch, landet dieser Lauf im else-Zweig und
            # warnt einmal unnötig – harmlos, angefasst wird nichts.
            hinter=0
        else
            grund="das Vorspulen auf origin/main ist gescheitert: $(printf '%s' "$ausgabe" | head -n 3 | tr '\n' ' ')"
        fi
    fi
elif [ -n "$branch" ]; then
    grund="im Haupt-Checkout ist Branch '$branch' ausgecheckt statt main"
else
    grund="im Haupt-Checkout ist kein Branch ausgecheckt (detached HEAD)"
fi

# Maßgeblich ist, was Claude Code tatsächlich lädt: weichen Skills, Agenten
# oder Commands im Haupt-Checkout (inkl. lokaler Änderungen) von origin/main ab?
abweichend="$(
    {
        g diff --name-only "$ZIEL" -- "${GELADENE_ORDNER[@]}"
        g ls-files --others --exclude-standard -- "${GELADENE_ORDNER[@]}"
    } 2>/dev/null | grep -v '\.DS_Store$' | sort -u
)"

[ -z "$abweichend" ] && exit 0

# Weicht nur etwas ab, weil der Pull nicht ging oder lokale Änderungen da
# sind, steht der Grund oben; sonst (main aktuell, aber Skills lokal geändert):
[ -n "$grund" ] || grund="lokale Änderungen an Skills/Agenten im Haupt-Checkout"

if [ "$branch" = "$HAUPT_BRANCH" ]; then
    stand="main ist $hinter Commit(s) hinter origin/main"
else
    stand="'${branch:-detached HEAD}' ausgecheckt, $(g rev-list --count "HEAD..$ZIEL" 2>/dev/null || echo "?") Commit(s) von origin/main fehlen"
fi

cat <<EOF
⚠️ VERALTETE SKILLS/AGENTEN: Der Haupt-Checkout $HAUPT ist nicht auf dem Stand von origin/main. Claude Code lädt Skills und Agenten von dort – die Fassungen in dieser Sitzung können veraltet sein, neue Agent-Typen fehlen womöglich.
  Stand: $stand.
  Nicht automatisch aktualisiert, weil: $grund.
  Abweichende Skills/Agenten/Commands:
$(printf '%s\n' "$abweichend" | liste_kuerzen)
EOF

lokal="$(geaenderte_dateien)"
if [ -n "$lokal" ]; then
    echo "  Lokal geänderte Dateien im Haupt-Checkout (gehören evtl. anderen Sitzungen):"
    printf '%s\n' "$lokal" | liste_kuerzen
fi

cat <<'EOF'
  So vorgehen:
  - Vor einem Skill oder Agenten die aktuelle Fassung aus diesem Worktree per Read lesen (.claude/skills/<name>/SKILL.md, .claude/agents/<name>.md, .claude/commands/<name>.md). Bei Widerspruch gilt die Worktree-Datei.
  - Fehlt ein Agent-Typ, seine .md aus dem Worktree lesen und die Anweisungen einem general-purpose-Agenten mitgeben.
  - Den Nutzer zu Beginn kurz darauf hinweisen. Den Haupt-Checkout NICHT zurücksetzen, stashen oder Dateien dort überschreiben – er wird von parallelen Sitzungen geteilt.
EOF

exit 0
