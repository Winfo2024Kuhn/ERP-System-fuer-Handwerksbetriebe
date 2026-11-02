#!/usr/bin/env bash
# Test für scripts/claude-haupt-checkout-pruefen.sh mit Wegwerf-Repos:
#   bash scripts/claude-haupt-checkout-pruefen.test.sh
#   TEST_BASH=/bin/bash bash …   # Skript mit macOS-Bash 3.2 prüfen
#
# Baut je Fall ein Remote (bare), einen Haupt-Checkout mit Worktree unter
# .claude/worktrees/ und einen zweiten Klon, der neue Commits pusht. Läuft
# ohne Netz und ohne die globale Git-Konfiguration des Rechners.

set -uo pipefail

SKRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/claude-haupt-checkout-pruefen.sh"
TMP_BASIS="$(mktemp -d)"
trap 'rm -rf "$TMP_BASIS"' EXIT

export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1
export GIT_AUTHOR_NAME="Max Mustermann" GIT_AUTHOR_EMAIL="max@example.com"
export GIT_COMMITTER_NAME="Max Mustermann" GIT_COMMITTER_EMAIL="max@example.com"
unset CLAUDE_CODE_REMOTE GIT_DIR GIT_WORK_TREE

fehler=0
ok()     { echo "OK   $1"; }
falsch() { echo "FAIL $1"; fehler=1; }

# Legt $FALL/remote.git, $FALL/haupt (mit Worktree $FALL/haupt/.claude/worktrees/wt)
# und $FALL/anderer an. Alle drei stehen danach auf demselben main.
aufbauen() {
    FALL="$TMP_BASIS/$1"
    mkdir -p "$FALL"
    git init --quiet --bare --initial-branch=main "$FALL/remote.git"
    git clone --quiet "$FALL/remote.git" "$FALL/haupt" 2>/dev/null
    H="$FALL/haupt"
    git -C "$H" symbolic-ref HEAD refs/heads/main
    mkdir -p "$H/.claude/skills/beispiel" "$H/.claude/agents"
    echo "Fassung 1" > "$H/.claude/skills/beispiel/SKILL.md"
    echo "Agent 1" > "$H/.claude/agents/erp-backend.md"
    echo "Code 1" > "$H/README.md"
    git -C "$H" add -A
    git -C "$H" commit --quiet -m "Start"
    git -C "$H" push --quiet -u origin main 2>/dev/null
    echo ".claude/worktrees/" >> "$H/.git/info/exclude"
    git -C "$H" worktree add --quiet -b sitzung "$H/.claude/worktrees/wt" origin/main 2>/dev/null
    WT="$H/.claude/worktrees/wt"
    git clone --quiet "$FALL/remote.git" "$FALL/anderer" 2>/dev/null
    A="$FALL/anderer"
}

# Neuer Commit auf origin/main, der einen Skill ändert.
remote_vorruecken() {
    echo "Fassung 2" > "$A/.claude/skills/beispiel/SKILL.md"
    git -C "$A" commit --quiet -am "Neue Skill-Fassung"
    git -C "$A" push --quiet origin main 2>/dev/null
}

pruefen() {
    AUSGABE="$(CLAUDE_PROJECT_DIR="$1" HAUPT_CHECKOUT_FETCH_SEKUNDEN=3 "${TEST_BASH:-bash}" "$SKRIPT" </dev/null)"
    RC=$?
}

kopf()   { git -C "$1" rev-parse HEAD; }
enthaelt() { case "$AUSGABE" in *"$1"*) return 0;; *) return 1;; esac; }

# 1. Alles aktuell: keine Ausgabe.
aufbauen aktuell
pruefen "$WT"
[ "$RC" -eq 0 ] && [ -z "$AUSGABE" ] && ok "aktuell: still" || falsch "aktuell: Ausgabe '$AUSGABE' (rc $RC)"

# 2. Hinterher und sauber: Fast-Forward, kurze Meldung.
aufbauen vorspulen
remote_vorruecken
echo "egal" > "$H/.DS_Store"   # unversionierte Dateien stören nicht
pruefen "$WT"
[ "$(kopf "$H")" = "$(kopf "$A")" ] && ok "vorspulen: Haupt-Checkout auf origin/main" || falsch "vorspulen: HEAD nicht vorgerückt"
[ "$(cat "$H/.claude/skills/beispiel/SKILL.md")" = "Fassung 2" ] && ok "vorspulen: neue Skill-Fassung da" || falsch "vorspulen: Skill alt"
enthaelt "aktualisiert" && ! enthaelt "VERALTETE" && ok "vorspulen: nur Erfolgsmeldung" || falsch "vorspulen: Ausgabe '$AUSGABE'"

# 3. Hinterher mit lokaler Änderung: nichts anfassen, deutlich warnen.
aufbauen schmutzig
remote_vorruecken
echo "lokal geändert" > "$H/README.md"
vorher="$(kopf "$H")"
pruefen "$WT"
[ "$(kopf "$H")" = "$vorher" ] && ok "schmutzig: HEAD unverändert" || falsch "schmutzig: HEAD verändert"
[ "$(cat "$H/README.md")" = "lokal geändert" ] && ok "schmutzig: lokale Änderung erhalten" || falsch "schmutzig: lokale Änderung weg"
enthaelt "VERALTETE" && enthaelt "1 Commit(s) hinter" && enthaelt "README.md" && enthaelt ".claude/skills/beispiel/SKILL.md" \
    && ok "schmutzig: Warnung mit Rückstand, Dateien und Skill" || falsch "schmutzig: Ausgabe '$AUSGABE'"
[ "$RC" -eq 0 ] && ok "schmutzig: Exit 0" || falsch "schmutzig: Exit $RC"

# 4. Anderer Branch im Haupt-Checkout: warnen, Branch bleibt.
aufbauen branch
remote_vorruecken
git -C "$H" checkout --quiet -b experiment
pruefen "$WT"
[ "$(git -C "$H" symbolic-ref --short HEAD)" = "experiment" ] && ok "branch: Branch bleibt" || falsch "branch: Branch gewechselt"
enthaelt "VERALTETE" && enthaelt "experiment" && ok "branch: Warnung nennt Branch" || falsch "branch: Ausgabe '$AUSGABE'"

# 5. Eigene Commits auf main: nicht vorspulen, warnen.
aufbauen eigene
remote_vorruecken
echo "eigener Stand" > "$H/README.md"
git -C "$H" commit --quiet -am "Lokaler Commit"
vorher="$(kopf "$H")"
pruefen "$WT"
[ "$(kopf "$H")" = "$vorher" ] && enthaelt "1 eigene Commit(s)" && ok "eigene: unverändert, Warnung" || falsch "eigene: Ausgabe '$AUSGABE'"

# 6. Aktuell, aber Skill lokal geändert: Warnung, weil die Sitzung ihn so lädt.
aufbauen skill-lokal
echo "Bastelstand" > "$H/.claude/skills/beispiel/SKILL.md"
pruefen "$WT"
enthaelt "VERALTETE" && enthaelt "lokale Änderungen an Skills" && ok "skill-lokal: Warnung" || falsch "skill-lokal: Ausgabe '$AUSGABE'"

# 6b. Unversionierte Datei steht dem Vorspulen im Weg: git bricht ab, Datei bleibt.
aufbauen im-weg
mkdir -p "$A/.claude/skills/neu"
echo "vom Server" > "$A/.claude/skills/neu/SKILL.md"
git -C "$A" add -A && git -C "$A" commit --quiet -m "Neuer Skill" && git -C "$A" push --quiet origin main 2>/dev/null
mkdir -p "$H/.claude/skills/neu"
echo "lokal angelegt" > "$H/.claude/skills/neu/SKILL.md"
vorher="$(kopf "$H")"
pruefen "$WT"
[ "$(kopf "$H")" = "$vorher" ] && [ "$(cat "$H/.claude/skills/neu/SKILL.md")" = "lokal angelegt" ] \
    && ok "im-weg: nichts überschrieben" || falsch "im-weg: Datei oder HEAD verändert"
enthaelt "Vorspulen auf origin/main ist gescheitert" && ok "im-weg: Warnung" || falsch "im-weg: Ausgabe '$AUSGABE'"

# 6c. Nur gestagte Änderung: zählt als lokale Änderung.
aufbauen gestagt
remote_vorruecken
echo "gestagt" > "$H/README.md"
git -C "$H" add README.md
vorher="$(kopf "$H")"
pruefen "$WT"
[ "$(kopf "$H")" = "$vorher" ] && enthaelt "lokale Änderungen im Haupt-Checkout" \
    && [ -n "$(git -C "$H" diff --cached --name-only)" ] && ok "gestagt: unverändert, Warnung" || falsch "gestagt: Ausgabe '$AUSGABE'"

# 6d. Detached HEAD: warnen, nichts anfassen.
aufbauen detached
remote_vorruecken
git -C "$H" checkout --quiet --detach
pruefen "$WT"
! git -C "$H" symbolic-ref --quiet HEAD >/dev/null && enthaelt "detached HEAD" && ok "detached: Warnung" || falsch "detached: Ausgabe '$AUSGABE'"

# 6e. Mehrere Sitzungen starten gleichzeitig: einmal vorgespult, genau eine Meldung.
aufbauen parallel
remote_vorruecken
for i in 1 2 3 4; do
    CLAUDE_PROJECT_DIR="$WT" HAUPT_CHECKOUT_FETCH_SEKUNDEN=3 "${TEST_BASH:-bash}" "$SKRIPT" </dev/null > "$FALL/lauf$i.txt" &
done
wait
meldungen="$(cat "$FALL"/lauf*.txt | grep -c "ist jetzt aktualisiert")"
[ "$(kopf "$H")" = "$(kopf "$A")" ] && [ -z "$(git -C "$H" status --porcelain --untracked-files=no)" ] && [ "$meldungen" -eq 1 ] \
    && ok "parallel: vorgespult, $meldungen Meldung" || falsch "parallel: $meldungen Meldungen, Status '$(git -C "$H" status --porcelain)'"

# 6f. Cloud-Sitzung: still.
aufbauen cloud
remote_vorruecken
vorher="$(kopf "$H")"
AUSGABE="$(CLAUDE_CODE_REMOTE=true CLAUDE_PROJECT_DIR="$WT" "${TEST_BASH:-bash}" "$SKRIPT" </dev/null)"
[ -z "$AUSGABE" ] && [ "$(kopf "$H")" = "$vorher" ] && ok "cloud: still, nichts angefasst" || falsch "cloud: Ausgabe '$AUSGABE'"

# 7. Offline (Remote weg): kein Hängen, mit bekanntem Stand weiter.
aufbauen offline
git -C "$H" remote set-url origin "$FALL/gibt-es-nicht.git"
start=$SECONDS
pruefen "$WT"
[ $((SECONDS - start)) -le 5 ] && [ "$RC" -eq 0 ] && [ -z "$AUSGABE" ] && ok "offline: still und schnell" || falsch "offline: rc $RC, Ausgabe '$AUSGABE'"

# 7b. Server hängt: das Zeitlimit bricht den Fetch ab.
aufbauen haengt
git -C "$H" remote set-url origin "ssh://server.invalid/repo.git"
start=$SECONDS
AUSGABE="$(CLAUDE_PROJECT_DIR="$WT" HAUPT_CHECKOUT_FETCH_SEKUNDEN=2 GIT_SSH_COMMAND="sh -c 'sleep 30'" "${TEST_BASH:-bash}" "$SKRIPT" </dev/null)"
dauer=$((SECONDS - start))
[ "$dauer" -le 4 ] && [ -z "$AUSGABE" ] && ok "haengt: nach ${dauer}s abgebrochen" || falsch "haengt: ${dauer}s, Ausgabe '$AUSGABE'"

# 8. Sitzung direkt im Haupt-Checkout auf einem Arbeits-Branch: still.
aufbauen haupt-sitzung
remote_vorruecken
git -C "$H" checkout --quiet -b arbeit
echo "in Arbeit" > "$H/.claude/skills/beispiel/SKILL.md"
pruefen "$H"
[ -z "$AUSGABE" ] && [ "$(cat "$H/.claude/skills/beispiel/SKILL.md")" = "in Arbeit" ] && ok "haupt-sitzung: still, nichts angefasst" || falsch "haupt-sitzung: Ausgabe '$AUSGABE'"

# 9. Kein Git-Repo: still.
mkdir -p "$TMP_BASIS/kein-repo"
pruefen "$TMP_BASIS/kein-repo"
[ "$RC" -eq 0 ] && [ -z "$AUSGABE" ] && ok "kein-repo: still" || falsch "kein-repo: rc $RC, Ausgabe '$AUSGABE'"

exit "$fehler"
