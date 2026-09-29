#!/usr/bin/env bash
# Richtet eine Claude-Code-Cloud-Sitzung so ein, dass sie sich wie die lokale
# Windows-Umgebung verhält: graphify (inkl. Hook-Guard), PowerShell für die
# Doc-Read-/Design-Skill-Hooks, JDK 23 für das Backend, vorgewärmte MCP-Server
# (shadcn, magic, playwright) und installierte Frontend-/Maven-Abhängigkeiten.
#
# Zwei Einsatzarten, gleiche Datei:
#   1. Setup-Skript der Cloud-Umgebung (claude.ai/code → Umgebung → Bearbeiten
#      → "Setup script"): den kompletten Inhalt dieser Datei dort einfügen.
#      Läuft vor dem Start von Claude Code, das Ergebnis wird als Snapshot
#      gecacht. Braucht keine Dateien aus dem Repo.
#   2. SessionStart-Hook (.claude/settings.json) mit --session: verlinkt die
#      installierten Tools ins Projekt, setzt JAVA_HOME und holt fehlende
#      Teile nach, falls das Setup-Skript nicht eingerichtet ist.
#
# Lokal (CLAUDE_CODE_REMOTE != true) tut das Skript nichts.

set -uo pipefail

[ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || exit 0

MODE="${1:-provision}"
JDK_VERSION="23.0.2"
# Von https://download.oracle.com/java/23/archive/jdk-23.0.2_linux-x64_bin.tar.gz.sha256
JDK_SHA256="12d7553d06b5cacf88b26cad4a8ba83cabe79646f1defb1b7fd029f3356d0922"
JDK_DIR="/opt/jdk-23"
# Fest gepinnt: das Binary läuft als Hook bei jedem Tool-Aufruf.
GRAPHIFY_SPEC="graphifyy[sql]==0.9.71"
GRAPHIFY_VENV="/opt/graphify-venv"
LOG="/tmp/claude-cloud-setup.log"

log() { echo "[claude-cloud-setup] $*" >&2; }

find_repo() {
    if [ -n "${CLAUDE_PROJECT_DIR:-}" ] && [ -f "$CLAUDE_PROJECT_DIR/pom.xml" ]; then
        echo "$CLAUDE_PROJECT_DIR"
        return
    fi
    local top
    top="$(git rev-parse --show-toplevel 2>/dev/null)"
    if [ -n "$top" ] && [ -f "$top/pom.xml" ]; then
        echo "$top"
        return
    fi
    local pom
    for pom in /home/user/*/pom.xml; do
        [ -f "$pom" ] && [ -d "$(dirname "$pom")/react-pc-frontend" ] && { dirname "$pom"; return; }
    done
}

install_pwsh() {
    command -v pwsh >/dev/null 2>&1 && return 0
    log "Installiere PowerShell (für die .ps1-Hooks) ..."
    local deb version_id
    version_id="$(. /etc/os-release 2>/dev/null && echo "${VERSION_ID:-}")"
    [ -n "$version_id" ] || { log "Ubuntu-Version unbekannt – PowerShell übersprungen"; return 1; }
    deb="$(mktemp --suffix=.deb)" || return 1
    curl -fsSL -o "$deb" "https://packages.microsoft.com/config/ubuntu/${version_id}/packages-microsoft-prod.deb" \
        && dpkg -i "$deb" >/dev/null \
        && apt-get update -qq >/dev/null 2>&1 \
        && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq powershell >/dev/null 2>&1
    rm -f "$deb"
    command -v pwsh >/dev/null 2>&1 || { log "PowerShell-Installation fehlgeschlagen"; return 1; }
}

install_graphify() {
    [ -x "$GRAPHIFY_VENV/bin/graphify" ] || {
        log "Installiere graphify ..."
        if command -v uv >/dev/null 2>&1; then
            uv venv -q "$GRAPHIFY_VENV" && VIRTUAL_ENV="$GRAPHIFY_VENV" uv pip install -q "$GRAPHIFY_SPEC"
        else
            python3 -m venv "$GRAPHIFY_VENV" && "$GRAPHIFY_VENV/bin/pip" install -q "$GRAPHIFY_SPEC"
        fi
    }
    [ -x "$GRAPHIFY_VENV/bin/graphify" ] || { log "graphify-Installation fehlgeschlagen"; return 1; }
    # Die Hooks in .claude/settings.json rufen den Windows-Pfad
    # .graphify-venv/Scripts/graphify.exe auf. Unter Linux zeigt dieser Pfad
    # auf das echte Linux-Binary, damit dieselbe Konfiguration überall läuft.
    mkdir -p "$GRAPHIFY_VENV/Scripts"
    ln -sfn ../bin/graphify "$GRAPHIFY_VENV/Scripts/graphify.exe"
}

install_jdk() {
    [ -x "$JDK_DIR/bin/javac" ] && return 0
    log "Installiere JDK $JDK_VERSION (pom.xml verlangt Java 23) ..."
    local tmp
    tmp="$(mktemp -d)" || return 1
    if curl -fsSL -o "$tmp/jdk.tar.gz" "https://download.oracle.com/java/23/archive/jdk-${JDK_VERSION}_linux-x64_bin.tar.gz" \
        && echo "$JDK_SHA256  $tmp/jdk.tar.gz" | sha256sum -c --status \
        && tar -xzf "$tmp/jdk.tar.gz" -C "$tmp"; then
        rm -rf "$JDK_DIR"
        mv "$tmp"/jdk-"$JDK_VERSION" "$JDK_DIR"
    else
        log "JDK-Download oder Prüfsumme fehlgeschlagen"
    fi
    rm -rf "$tmp"
    [ -x "$JDK_DIR/bin/javac" ] || { log "JDK-Installation fehlgeschlagen"; return 1; }
}

# Lädt die MCP-Server aus .mcp.json einmal in den npx-Cache. Ohne das läuft
# der erste Download beim Sitzungsstart in das 30-s-Verbindungs-Timeout.
warm_mcp_servers() {
    local pkg
    for pkg in shadcn@latest @21st-dev/magic@latest @playwright/mcp@latest; do
        timeout 120 npx -y "$pkg" --version </dev/null >/dev/null 2>&1 || true
    done
}

# Die Cloud-Umgebung bringt einen Chromium mit (PLAYWRIGHT_BROWSERS_PATH),
# die Playwright-CDNs sind aber gesperrt. Neuere @playwright/test-Versionen
# verlangen eine höhere Revision und ein anderes Verzeichnislayout
# (chrome-linux64/chrome, chrome-headless-shell-linux64/chrome-headless-shell).
# Der vorhandene Browser wird deshalb unter den erwarteten Pfaden verlinkt.
link_playwright_browsers() {
    local app base="${PLAYWRIGHT_BROWSERS_PATH:-/opt/pw-browsers}"
    app="$(cd "$1" && pwd)" || return 0
    local browsers_json="$app/node_modules/playwright-core/browsers.json"
    [ -f "$browsers_json" ] || return 0

    local src_full src_shell
    src_full="$(ls -d "$base"/chromium-[0-9]*/chrome-linux 2>/dev/null | sort -V | head -n1)"
    src_shell="$(ls -d "$base"/chromium_headless_shell-[0-9]*/chrome-linux 2>/dev/null | sort -V | head -n1)"
    [ -n "$src_full" ] || return 0

    local rev_full rev_shell
    local find_rev='(f, n) => (require(f).browsers.find(b => b.name === n) || {}).revision || ""'
    rev_full="$(node -p "($find_rev)(process.argv[1], 'chromium')" "$browsers_json" 2>/dev/null)"
    rev_shell="$(node -p "($find_rev)(process.argv[1], 'chromium-headless-shell')" "$browsers_json" 2>/dev/null)"

    local dst
    if [ -n "$rev_full" ] && [ ! -e "$base/chromium-$rev_full/INSTALLATION_COMPLETE" ]; then
        dst="$base/chromium-$rev_full"
        mkdir -p "$dst"
        ln -sfn "$src_full" "$dst/chrome-linux64"
        ln -sfn "$src_full" "$dst/chrome-linux"
        touch "$dst/INSTALLATION_COMPLETE" "$dst/DEPENDENCIES_VALIDATED"
        log "Playwright: chromium-$rev_full -> $src_full"
    fi
    if [ -n "$rev_shell" ] && [ -n "$src_shell" ] && [ ! -e "$base/chromium_headless_shell-$rev_shell/INSTALLATION_COMPLETE" ]; then
        dst="$base/chromium_headless_shell-$rev_shell"
        rm -rf "$dst"
        mkdir -p "$dst"
        cp -as "$src_shell" "$dst/chrome-headless-shell-linux64"
        ln -sfn headless_shell "$dst/chrome-headless-shell-linux64/chrome-headless-shell"
        ln -sfn "$src_shell" "$dst/chrome-linux"
        touch "$dst/INSTALLATION_COMPLETE" "$dst/DEPENDENCIES_VALIDATED"
        log "Playwright: chromium_headless_shell-$rev_shell -> $src_shell"
    fi
}

# Einige Einträge in den package-lock.json verweisen auf registry.npmmirror.com,
# das die Cloud-Umgebung sperrt. --replace-registry-host lädt diese Pakete
# stattdessen von registry.npmjs.org (gleiche Tarballs, die Integrity-Hashes
# aus der Lockfile werden weiter geprüft). Die Lockfile bleibt unangetastet.
npm_ci_via_npmjs() {
    (cd "$1" && timeout 600 npm ci --no-audit --no-fund --loglevel=error \
        --registry=https://registry.npmjs.org/ \
        --replace-registry-host=registry.npmmirror.com)
}

install_frontend_deps() {
    local repo="$1" app
    for app in react-pc-frontend react-zeiterfassung; do
        [ -f "$repo/$app/package-lock.json" ] || continue
        # npm schreibt diese Datei erst am Ende einer erfolgreichen
        # Installation – ein abgebrochener Lauf wird so nachgeholt.
        if [ ! -f "$repo/$app/node_modules/.package-lock.json" ]; then
            log "npm ci in $app ..."
            npm_ci_via_npmjs "$repo/$app" || log "npm ci in $app fehlgeschlagen"
        fi
        link_playwright_browsers "$repo/$app"
    done
}

# $2 = Zeitlimit in Sekunden (0 = ohne Limit). Im Setup-Skript begrenzt,
# damit die Umgebung unter ~5 Minuten bleibt und gecacht wird.
prefetch_maven() {
    local repo="$1" limit="${2:-0}"
    [ -x "$JDK_DIR/bin/java" ] || return 0
    log "Lade Maven-Abhängigkeiten ..."
    (cd "$repo" && JAVA_HOME="$JDK_DIR" timeout "$limit" ./mvnw -q -B dependency:go-offline) \
        || log "Maven-Vorabdownload unvollständig – wird beim ersten Build nachgeholt"
}

link_into_project() {
    local repo="$1"
    # .graphify-venv und ./graphify sind gitignored – lokal legt man sie von
    # Hand an, in der Cloud zeigen sie auf die vorinstallierte Version.
    if [ ! -e "$repo/.graphify-venv" ]; then
        ln -sfn "$GRAPHIFY_VENV" "$repo/.graphify-venv"
    fi
    if [ ! -e "$repo/graphify" ]; then
        printf '%s\n' '#!/usr/bin/env bash' \
            'exec "$(dirname "$0")/.graphify-venv/bin/graphify" "$@"' > "$repo/graphify"
        chmod +x "$repo/graphify"
    fi
    # Wird vor jedem Bash-Aufruf gesourct – die Prüfung greift also auch,
    # wenn das JDK erst im Hintergrund fertig wird.
    local java_env="[ -x \"$JDK_DIR/bin/java\" ] && export JAVA_HOME=\"$JDK_DIR\" PATH=\"$JDK_DIR/bin:\$PATH\""
    if [ -n "${CLAUDE_ENV_FILE:-}" ] && ! grep -qxF "$java_env" "$CLAUDE_ENV_FILE" 2>/dev/null; then
        echo "$java_env" >> "$CLAUDE_ENV_FILE"
    fi
}

provision() {
    install_pwsh &
    install_graphify &
    install_jdk &
    warm_mcp_servers &
    wait
    local repo
    repo="$(find_repo)"
    if [ -n "$repo" ]; then
        install_frontend_deps "$repo" &
        prefetch_maven "$repo" 150 &
        wait
    fi
    log "Fertig."
}

session() {
    local repo
    repo="$(find_repo)"
    [ -n "$repo" ] || exit 0
    # Schnelle Teile sofort, damit die Hooks ab dem ersten Tool-Aufruf greifen.
    install_pwsh
    install_graphify
    link_into_project "$repo"
    # Langsame Teile im Hintergrund – nur nötig, wenn das Setup-Skript der
    # Umgebung fehlt oder der Cache abgelaufen ist.
    (
        install_jdk
        install_frontend_deps "$repo"
        prefetch_maven "$repo"
    ) >>"$LOG" 2>&1 </dev/null &
    disown
}

case "$MODE" in
    --session) session ;;
    *) provision ;;
esac
exit 0
