# Claude Code in der Cloud einrichten

Ziel: Eine Cloud-Sitzung auf claude.ai/code verhält sich wie die lokale
Windows-Umgebung – graphify samt Hook-Guard, Doc-Read- und Design-Skill-Hooks,
Java 23, die MCP-Server aus `.mcp.json` und die Frontend-Abhängigkeiten.

## Was automatisch passiert (im Repo)

- `scripts/claude-cloud/setup.sh --session` läuft als SessionStart-Hook
  (`.claude/settings.json`). Es verlinkt graphify ins Projekt (`.graphify-venv`,
  `./graphify`), setzt `JAVA_HOME` auf JDK 23 und holt fehlende Teile nach.
  Lokal (ohne `CLAUDE_CODE_REMOTE=true`) beendet es sich sofort.
- Alle Hooks nutzen `${CLAUDE_PROJECT_DIR}` statt fester Windows-Pfade und
  laufen dadurch in jedem Checkout – lokal wie in der Cloud.
- **npm**: `registry.npmmirror.com` ist in der Cloud gesperrt. Die Lockfiles
  zeigen deshalb nur noch auf `registry.npmjs.org`, und die `.npmrc` in beiden
  Frontends legt diese Registry fest. Für ältere Branches, die noch
  npmmirror-Einträge haben, installiert das Skript mit
  `npm ci --replace-registry-host=registry.npmmirror.com` – ohne die Lockfile
  anzufassen.
- **Playwright**: Die Playwright-Downloadserver sind gesperrt, und
  `@playwright/test` verlangt eine neuere Chromium-Revision als vorinstalliert.
  Das Skript verlinkt den vorhandenen Chromium unter den erwarteten Pfaden in
  `/opt/pw-browsers` – es wird nichts heruntergeladen. Die Tests laufen damit
  gegen eine ältere Chromium-Version als lokal; bei auffälligen Unterschieden
  in E2E-Tests lokal gegenprüfen.

Ohne die Schritte unten funktioniert das auch, der erste Sitzungsstart dauert
dann aber länger und `magic`/`shadcn` bleiben ohne Netz.

## Einmalig in der Cloud-Umgebung einstellen

claude.ai/code → Umgebungsmenü in der Titelleiste der Sitzung → **Bearbeiten**:

1. **Setup script**: den kompletten Inhalt von `scripts/claude-cloud/setup.sh`
   einfügen. Installiert PowerShell, graphify, JDK 23, lädt die MCP-Server
   vor und wird danach als Snapshot gecacht (neue Sitzungen starten schnell).
2. **Network access**: `Custom` wählen, Haken bei *Also include default list of
   common package managers* setzen und diese Domains eintragen:
   ```
   21st.dev
   *.21st.dev
   ui.shadcn.com
   ```
3. **Environment variables**:
   ```
   TWENTYFIRST_API_KEY=<dein 21st.dev-Key>
   ```
   Werte hier sind für alle sichtbar, die die Umgebung nutzen – die Umgebung
   also nicht teilen.

## Was nicht aus dem Repo kommt

- **GitHub**: Der `github`-Eintrag in `.mcp.json` braucht Docker + Token und
  ist nur für lokal gedacht. In der Cloud stellt Claude Code GitHub selbst
  bereit. Kann Claude keine PRs anlegen, GitHub unter
  https://claude.ai/connect-github neu verbinden und prüfen, ob die Claude
  GitHub-App auf dem Repo installiert ist
  (https://github.com/apps/claude/installations/select_target).
- **Connectoren** wie Gmail, Google Drive, Notion oder Canva werden in den
  claude.ai-Einstellungen verbunden und laufen über Anthropic – dafür ist
  nichts im Repo und keine Netzwerkfreigabe nötig.
- Plugins aus `enabledPlugins` installiert die Cloud nicht; Skills unter
  `.claude/skills/` sind dagegen verfügbar.

Protokoll der Hintergrund-Installation: `/tmp/claude-cloud-setup.log`.
